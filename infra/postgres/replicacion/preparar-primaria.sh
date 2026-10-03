#!/bin/sh
# Prepara la base primaria para que una réplica pueda conectarse. Se ejecuta DENTRO del
# contenedor de la primaria, como usuario postgres, en cada despliegue (lo invoca desplegar.sh):
#     docker compose exec -T -u postgres postgres sh /replicacion/preparar-primaria.sh
#
# Es idempotente y funciona sobre una base que ya tiene datos. No se hace con un script de
# /docker-entrypoint-initdb.d porque esos solo corren al inicializar un volumen vacío, y la
# primaria de producción ya existía cuando se agregó la réplica.
#
# Qué deja configurado:
#   - el rol `replicador`, solo con permiso de replicación, con la contraseña de REPLICA_PASSWORD.
#     La regla por defecto de la imagen (`host all all all`) le permitiría conectarse a la base de
#     la aplicación y ver los nombres de sus tablas, aunque no leer los datos; por eso se retira
#     CONNECT a PUBLIC. La replicación no lo necesita (usa el protocolo de replicación, que no
#     abre una base de datos) y la aplicación entra como propietario, que no depende de PUBLIC;
#   - la ranura física `replica_slot`: la primaria conserva el WAL que la réplica aún no leyó
#     (el tope lo pone max_slot_wal_keep_size en el compose, para que una réplica caída no
#     llene el disco);
#   - la regla de pg_hba que permite conexiones de replicación solo desde la misma subred de
#     Docker; la base nunca se publica fuera de esa red.
set -eu

: "${POSTGRES_USER:?}" "${POSTGRES_DB:?}" "${REPLICA_PASSWORD:?Falta REPLICA_PASSWORD}"

psql -v ON_ERROR_STOP=1 -q -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v clave="$REPLICA_PASSWORD" -v basedatos="$POSTGRES_DB" > /dev/null <<'SQL'
SELECT 'CREATE ROLE replicador WITH REPLICATION LOGIN'
WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'replicador') \gexec

-- Se reasigna siempre, así una rotación de REPLICA_PASSWORD se aplica en el siguiente despliegue.
ALTER ROLE replicador WITH REPLICATION LOGIN PASSWORD :'clave';

-- El rol replicador no debe poder entrar a la base de la aplicación (ver la cabecera).
REVOKE CONNECT ON DATABASE :"basedatos" FROM PUBLIC;

-- Sin \gexec: la función devuelve una fila, no texto SQL que ejecutar.
SELECT pg_create_physical_replication_slot('replica_slot')
WHERE NOT EXISTS (SELECT FROM pg_replication_slots WHERE slot_name = 'replica_slot');
SQL

ARCHIVO_HBA=$(psql -tA -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "SHOW hba_file")
REGLA='host replication replicador samenet scram-sha-256'
if ! grep -qxF "$REGLA" "$ARCHIVO_HBA"; then
    echo "$REGLA" >> "$ARCHIVO_HBA"
    echo "primaria: regla de replicación agregada a pg_hba.conf"
fi
psql -tA -q -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "SELECT pg_reload_conf()" > /dev/null

echo "primaria: rol replicador, ranura replica_slot y regla de pg_hba listos"
