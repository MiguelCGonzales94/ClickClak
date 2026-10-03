# Despliegue en Azure — VM, costos y operación

> Cap. VII 7.2 del informe (APF2). Describe la infraestructura creada el 2 de octubre de 2026 para el despliegue de la versión 1 (CLICKCLACK-61 y CLICKCLACK-8) y cómo operarla. Los scripts viven en `infra/scripts/`.

## 1. Infraestructura

| Recurso | Valor |
|---|---|
| Suscripción | Azure for Students (tenant de la UTP) |
| Grupo de recursos | `rg-clickclak` |
| Región | **Chile Central** (`chilecentral`) |
| VM | `vm-clickclak`, Ubuntu 24.04 LTS, `Standard_B2als_v2` (2 vCPU AMD, 4 GiB de RAM) |
| Disco | 64 GiB Standard SSD |
| IP pública | `57.156.70.235` (estática) |
| Nombre DNS | `clickclak-utp.chilecentral.cloudapp.azure.com` |
| Puertos abiertos | 22 (SSH), 80 (HTTP), 443 (HTTPS) |
| Software instalado | Docker 29.1.3, Docker Compose 2.40.3, `fail2ban`, 2 GiB de swap |

### Por qué Chile Central

Una política de la universidad restringe la suscripción a cinco regiones: `westus`, `chilecentral`, `northcentralus`, `canadacentral` y `mexicocentral`. Chile Central es la más cercana a Perú entre las que permite, lo que reduce la latencia de los técnicos en campo.

### Por qué ese tamaño

- **Memoria (supuesto a validar al desplegar):** el compose lleva dos Postgres (primario y réplica), el backend, Nginx, Prometheus y Grafana. Se estima en 2 a 2,5 GiB, por lo que 4 GiB alcanzan con el swap como colchón.
- **Costo:** la variante de 8 GiB (`B2as_v2`) cuesta el doble y agotaría el crédito antes del cierre del curso.

## 2. Costo

Precios públicos de Azure en USD para Chile Central. Disco e IP son estimaciones, no se pudieron consultar.

| Opción | VM por mes | Total mensual aprox. | Hasta el 13-dic (~72 días) |
|---|---|---|---|
| `B2als_v2` encendida 24/7 | 38,40 | ~47 | ~113 (supera los 100 USD del crédito) |
| **`B2als_v2` ~12 h al día (adoptada)** | ~19 | ~28 | ~67 |
| `B2as_v2` (8 GiB) 24/7 | 76,65 | ~85 | No alcanza |

**Decisión:** el equipo pidió no consumir los 100 USD del crédito. Se adopta la VM de 4 GiB encendida solo en horario de uso, apagada cada noche y con alerta de presupuesto.

**Alerta de presupuesto:** `presupuesto-clickclak`, 35 USD por mes, con aviso por correo al 80 % y al 100 % del gasto real y cuando el pronóstico supere el 100 %.

## 3. Seguridad de la VM

- **Acceso solo por llave SSH** (ed25519). El acceso por contraseña está desactivado y se verificó con `sshd -T`. La llave privada se guarda en el equipo de quien administra y nunca en el repositorio.
- **`fail2ban`** activo contra intentos masivos. El puerto 22 está abierto a cualquier IP porque el equipo se conecta desde lugares distintos; se compensa con la llave y `fail2ban`.
- **Sin credenciales en la VM ni en el repositorio:** el apagado nocturno usa la identidad administrada de la VM, con el rol *Virtual Machine Contributor* limitado a esa misma VM.
- **Pendiente:** TLS con certificado de Let's Encrypt sobre el nombre DNS, cabeceras de seguridad y rate limiting en Nginx (CLICKCLACK-57).

## 4. Apagado nocturno automático

La VM se desasigna cada día a las **23:00 hora de Lima** (04:00 UTC) para dejar de cobrar cómputo. Siguen cobrándose el disco y la IP estática.

Dos decisiones técnicas:

- **No sirve el apagado automático nativo de Azure:** usa el servicio DevTestLab, que no existe en Chile Central.
- **No sirve apagar el sistema operativo desde dentro (`shutdown`):** deja la VM «detenida» y Azure sigue cobrando el cómputo. Solo la **desasignación** lo detiene.

Mecanismo adoptado: `infra/scripts/apagar-vm.sh`, instalado en `/usr/local/bin` y lanzado por cron (`infra/scripts/clickclak-apagado.cron`). El script pide un token a la identidad administrada de la VM y llama a la API de Azure para desasignarla. Se probó de extremo a extremo: Azure aceptó la orden (HTTP 202), la VM pasó a `VM deallocated` y se volvió a encender sin problema.

Para verificar permisos sin apagar nada: `sudo /usr/local/bin/apagar-vm.sh --probar`.

## 5. Operación

**Encender antes de trabajar o de una evaluación** (la IP no cambia):

```powershell
az vm start -g rg-clickclak -n vm-clickclak
```

**Conectarse:**

```powershell
ssh -i $env:USERPROFILE\.ssh\clickclak_azure azureuser@clickclak-utp.chilecentral.cloudapp.azure.com
```

**Ver el estado:**

```powershell
az vm get-instance-view -g rg-clickclak -n vm-clickclak --query "instanceView.statuses[?starts_with(code,'PowerState')].displayStatus | [0]" -o tsv
```

**Apagar a mano** (sin esperar a las 23:00): desde la VM, `sudo /usr/local/bin/apagar-vm.sh`. Apagar solo el sistema operativo no ahorra costo.

> **Recordatorio:** hay que encender la VM antes de cada evaluación o sustentación. Un apagado a las 23:00 no avisa a nadie.

## 6. Cómo recrearla

Preparación de la suscripción (una sola vez): registrar los proveedores `Microsoft.Compute`, `Microsoft.Network`, `Microsoft.Storage` y `Microsoft.DevTestLab`.

```powershell
az group create -n rg-clickclak -l chilecentral --tags proyecto=clickclak curso=integrador2

az vm create -g rg-clickclak -n vm-clickclak -l chilecentral `
  --image Canonical:ubuntu-24_04-lts:server:latest --size Standard_B2als_v2 `
  --admin-username azureuser --authentication-type ssh --ssh-key-values "$env:USERPROFILE\.ssh\clickclak_azure.pub" `
  --os-disk-size-gb 64 --storage-sku StandardSSD_LRS `
  --public-ip-sku Standard --public-ip-address-allocation static --public-ip-address-dns-name clickclak-utp `
  --nsg-rule SSH

az vm open-port -g rg-clickclak -n vm-clickclak --port 80 --priority 1010
az vm open-port -g rg-clickclak -n vm-clickclak --port 443 --priority 1020

# Identidad administrada con permiso solo sobre esta VM (para el apagado nocturno)
az vm identity assign -g rg-clickclak -n vm-clickclak
az role assignment create --assignee-object-id <principalId> --assignee-principal-type ServicePrincipal `
  --role "Virtual Machine Contributor" --scope <id-de-la-VM>
```

Después, copiar `infra/scripts/` a la VM, ejecutar `sh preparar-vm.sh` (Docker, swap y `fail2ban`) e instalar `apagar-vm.sh` y el cron como se indica en la sección 4.

## 7. Límites conocidos

- **No es alta disponibilidad real.** La réplica de Postgres correrá en esta misma VM: demuestra el mecanismo de replicación (cap. 6.2), pero no protege ante la caída de la VM. Una alta disponibilidad real (cap. 13.2) pediría una segunda VM, que no cabe en el crédito de estudiante.
- **El apagado nocturno es una decisión de costo.** El sistema no está disponible entre las 23:00 y el momento en que alguien la enciende.
- **Cuotas:** 6 vCPU en la región y 10 en la familia de la VM; esta usa 2.
