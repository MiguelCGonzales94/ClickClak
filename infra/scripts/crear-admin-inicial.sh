#!/bin/sh
# Crea el primer administrador (rol RRHH_ADMIN) de un despliegue nuevo. En producción no hay
# semilla de desarrollo, y el endpoint de usuarios exige estar autenticado como administrador:
# sin este paso nadie podría entrar la primera vez.
#
# La contraseña es aleatoria y se guarda SOLO en la VM, en ~/credenciales-admin-inicial.txt
# (permisos 600). No se imprime ni va al repositorio. Hay que cambiarla tras el primer acceso.
#
# Uso (en la VM, desde infra/):  sh scripts/crear-admin-inicial.sh [correo]
set -eu
cd "$(dirname "$0")/.."

CORREO="${1:-admin@clickclak.local}"
DESTINO="$HOME/credenciales-admin-inicial.txt"
COMPOSE="sudo docker compose -f docker-compose.prod.yml --env-file .env"
PSQL="$COMPOSE exec -T postgres psql -U clickclak -d clickclak -v ON_ERROR_STOP=1 -tA"

EXISTE=$($PSQL -v correo="$CORREO" -c "SELECT count(*) FROM usuario WHERE correo = :'correo';")
if [ "$EXISTE" != "0" ]; then
    echo "Ya existe un usuario con el correo $CORREO: no se modifica nada."
    exit 0
fi

command -v htpasswd > /dev/null || sudo apt-get install -y -qq apache2-utils > /dev/null

# Cumple PoliticaContrasenas (mínimo 8, letras y números): prefijo y sufijo fijos más 16 al azar.
CLAVE="Cc$(openssl rand -base64 24 | tr -dc 'A-Za-z0-9' | head -c 16)7"
# htpasswd emite $2y$; Spring Security acepta ese prefijo igual que $2a$.
HASH=$(htpasswd -bnBC 10 "" "$CLAVE" | tr -d ':\n')

$PSQL -v correo="$CORREO" -v hash="$HASH" > /dev/null <<'SQL'
INSERT INTO usuario (nombres, apellidos, tipo_documento, numero_documento, correo, password_hash, rol_id)
SELECT 'Administrador', 'Inicial', 'DNI', '00000000', :'correo', :'hash', rol.id
FROM rol WHERE rol.nombre = 'RRHH_ADMIN';
SQL

(
    umask 077
    printf 'correo: %s\ncontraseña: %s\n' "$CORREO" "$CLAVE" > "$DESTINO"
)
echo "Administrador creado: $CORREO"
echo "Credenciales guardadas en $DESTINO (solo legibles por este usuario)."
