#!/bin/sh
# Hook de despliegue para Certbot. Se instala en:
#   /etc/letsencrypt/renewal-hooks/deploy/recargar-clickclak-proxy
# Certbot lo ejecuta solo después de renovar correctamente un certificado.
set -eu

CONTENEDOR_PROXY="clickclak-proxy-1"

if ! sudo docker inspect -f '{{.State.Running}}' "$CONTENEDOR_PROXY" 2>/dev/null | grep -qx true; then
    echo "No se puede recargar Nginx: $CONTENEDOR_PROXY no está activo." >&2
    exit 1
fi

sudo docker exec "$CONTENEDOR_PROXY" nginx -s reload
echo "Nginx recargado después de renovar el certificado de ClickClak."
