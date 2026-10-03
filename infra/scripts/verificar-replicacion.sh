#!/bin/sh
# Verifica que la replicación de Postgres funciona de extremo a extremo. No es destructivo: crea
# una tabla temporal en la primaria, comprueba que aparece en la réplica y la borra al terminar.
# Se ejecuta EN la VM, desde infra/ (lo invoca también verificar-despliegue.sh):
#     sh scripts/verificar-replicacion.sh
# Para otro proyecto de Compose o sin sudo:  PROYECTO=cctest DOCKER=docker sh scripts/verificar-replicacion.sh
# Termina con código distinto de 0 si algo falla.
set -u
cd "$(dirname "$0")/.."

PROYECTO="${PROYECTO:-clickclak}"
DOCKER="${DOCKER:-sudo docker}"
PRIMARIA="${PROYECTO}-postgres-1"
REPLICA="${PROYECTO}-postgres-replica-1"
TABLA="verificacion_replicacion_$$"
MARCA="marca-$(date +%s)-$$"
MAXIMO_RETRASO_BYTES=1048576

FALLOS=0
resultado() { # $1 = descripción, $2 = esperado, $3 = obtenido
    if [ "$2" = "$3" ]; then
        printf 'OK     %-60s %s\n' "$1" "$3"
    else
        printf 'FALLA  %-60s esperado %s, obtenido %s\n' "$1" "$2" "$3"
        FALLOS=$((FALLOS + 1))
    fi
}
sql() { # $1 = contenedor, $2 = consulta
    $DOCKER exec "$1" psql -U clickclak -d clickclak -tAc "$2" 2>&1
}
limpiar() { sql "$PRIMARIA" "DROP TABLE IF EXISTS $TABLA" > /dev/null; }
trap limpiar EXIT

echo "== estado de la replicación (visto desde la primaria)"
resultado "la primaria no está en modo réplica" "f" "$(sql "$PRIMARIA" 'SELECT pg_is_in_recovery()')"
resultado "la réplica está conectada y en streaming" "streaming" "$(sql "$PRIMARIA" "SELECT state FROM pg_stat_replication LIMIT 1")"
resultado "se conecta con el rol replicador" "replicador" "$(sql "$PRIMARIA" "SELECT usename FROM pg_stat_replication LIMIT 1")"
resultado "la ranura replica_slot está activa" "t" "$(sql "$PRIMARIA" "SELECT active FROM pg_replication_slots WHERE slot_name = 'replica_slot'")"
RETRASO=$(sql "$PRIMARIA" "SELECT COALESCE(pg_wal_lsn_diff(sent_lsn, replay_lsn), 0)::bigint FROM pg_stat_replication LIMIT 1")
if [ "$RETRASO" -le "$MAXIMO_RETRASO_BYTES" ] 2> /dev/null; then
    resultado "retraso de la réplica (${RETRASO} bytes) dentro de 1 MiB" "correcto" "correcto"
else
    resultado "retraso de la réplica dentro de 1 MiB" "correcto" "${RETRASO:-sin dato}"
fi

echo "== la réplica (hot standby)"
resultado "la réplica está en modo recuperación" "t" "$(sql "$REPLICA" 'SELECT pg_is_in_recovery()')"
resultado "la réplica tiene PostGIS" "postgis" "$(sql "$REPLICA" "SELECT extname FROM pg_extension WHERE extname = 'postgis'")"
SALIDA=$(sql "$REPLICA" "CREATE TABLE ${TABLA}_rechazada (a int)")
case "$SALIDA" in
    *"read-only transaction"*) resultado "la réplica rechaza escrituras" "rechazada" "rechazada" ;;
    *)                         resultado "la réplica rechaza escrituras" "rechazada" "aceptada: $SALIDA" ;;
esac

echo "== el acceso de replicación está acotado"
# La clave del rol replicador sale de infra/.env, o de REPLICA_PASSWORD si se pasa por entorno.
CLAVE_REPLICA="${REPLICA_PASSWORD:-$(sed -n 's/^REPLICA_PASSWORD=//p' .env 2> /dev/null)}"
if [ -n "$CLAVE_REPLICA" ]; then
    SALIDA=$($DOCKER exec -e PGPASSWORD="$CLAVE_REPLICA" "$REPLICA" psql -h postgres -U replicador -d clickclak -tAc 'SELECT 1' 2>&1)
    case "$SALIDA" in
        *"permission denied for database"*) resultado "el rol replicador no puede entrar a la base de la aplicación" "denegado" "denegado" ;;
        *)                                  resultado "el rol replicador no puede entrar a la base de la aplicación" "denegado" "$SALIDA" ;;
    esac
else
    echo "OMITIDO  no se encontró REPLICA_PASSWORD para probar el acceso del rol replicador"
fi
SALIDA=$($DOCKER exec -e PGPASSWORD="clave-incorrecta" "$REPLICA" psql "host=postgres user=replicador dbname=postgres replication=true" -c 'IDENTIFY_SYSTEM' 2>&1)
case "$SALIDA" in
    *"password authentication failed"*) resultado "una contraseña incorrecta no puede replicar" "rechazada" "rechazada" ;;
    *)                                  resultado "una contraseña incorrecta no puede replicar" "rechazada" "$SALIDA" ;;
esac

echo "== los datos viajan de la primaria a la réplica"
sql "$PRIMARIA" "CREATE TABLE $TABLA (marca text); INSERT INTO $TABLA VALUES ('$MARCA')" > /dev/null
VISTO=""
for intento in 1 2 3 4 5 6 7 8 9 10; do
    VISTO=$(sql "$REPLICA" "SELECT marca FROM $TABLA" 2> /dev/null | head -1)
    [ "$VISTO" = "$MARCA" ] && break
    sleep 1
done
resultado "una escritura en la primaria aparece en la réplica" "$MARCA" "$VISTO"
# Solo se compara si la tabla existe y devuelve un número; si no, comparar dos mensajes de error
# idénticos daría un "OK" que no mide nada.
ROLES_PRIMARIA=$(sql "$PRIMARIA" 'SELECT count(*) FROM rol')
case "$ROLES_PRIMARIA" in
    [0-9]*) resultado "las dos bases tienen los mismos roles de la aplicación" "$ROLES_PRIMARIA" "$(sql "$REPLICA" 'SELECT count(*) FROM rol')" ;;
    *)      echo "OMITIDO  la tabla rol no existe todavía (el backend aún no migró la base)" ;;
esac

echo
if [ "$FALLOS" -eq 0 ]; then
    echo "Replicación verificada: todo en orden."
else
    echo "Replicación con $FALLOS fallo(s)."
    exit 1
fi
