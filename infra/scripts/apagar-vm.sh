#!/bin/sh
# Desasigna esta VM de Azure para que deje de cobrar cómputo (conserva disco e IP estática).
#
# Por qué no `shutdown -h` dentro de la VM: apagar el sistema operativo deja la VM en estado
# "Detenida", que Azure sigue cobrando. Solo la desasignación detiene el cobro del cómputo.
# Por qué no el apagado automático de Azure: el servicio DevTestLab no existe en Chile Central.
#
# Usa la identidad administrada de la VM, cuyo único permiso es "Virtual Machine Contributor"
# sobre esta misma VM: no hay credenciales guardadas ni en este archivo ni en el repositorio.
# Se programa con cron a las 04:00 UTC (23:00 en Lima; Perú no tiene horario de verano).
#
# Uso:  apagar-vm.sh            desasigna la VM
#       apagar-vm.sh --probar   solo verifica identidad y permisos, sin apagar nada
set -eu

METADATOS="http://169.254.169.254/metadata"
dato() { curl -fsS -H "Metadata:true" "$METADATOS/instance/compute/$1?api-version=2021-02-01&format=text"; }

SUSCRIPCION=$(dato subscriptionId)
GRUPO=$(dato resourceGroupName)
VM=$(dato name)

TOKEN=$(curl -fsS -H "Metadata:true" \
    "$METADATOS/identity/oauth2/token?api-version=2018-02-01&resource=https%3A%2F%2Fmanagement.azure.com%2F" \
    | sed -n 's/.*"access_token":"\([^"]*\)".*/\1/p')
if [ -z "$TOKEN" ]; then
    echo "$(date -u +%FT%TZ) no se pudo obtener el token de la identidad administrada" >&2
    exit 1
fi

RECURSO="https://management.azure.com/subscriptions/$SUSCRIPCION/resourceGroups/$GRUPO/providers/Microsoft.Compute/virtualMachines/$VM"

if [ "${1:-}" = "--probar" ]; then
    CODIGO=$(curl -sS -o /dev/null -w '%{http_code}' -H "Authorization: Bearer $TOKEN" "$RECURSO?api-version=2024-03-01")
    echo "$(date -u +%FT%TZ) prueba de permisos sobre $VM: HTTP $CODIGO (200 = correcto)"
    [ "$CODIGO" = "200" ]
    exit
fi

CODIGO=$(curl -sS -o /dev/null -w '%{http_code}' -X POST \
    -H "Authorization: Bearer $TOKEN" -H "Content-Length: 0" \
    "$RECURSO/deallocate?api-version=2024-03-01")
echo "$(date -u +%FT%TZ) desasignación de $VM solicitada: HTTP $CODIGO (202 = aceptada)"
[ "$CODIGO" = "202" ] || [ "$CODIGO" = "200" ]
