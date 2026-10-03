#!/bin/sh
# Prepara una VM Ubuntu 24.04 para ejecutar el stack de ClickClak con Docker Compose.
# Es idempotente: se puede volver a ejecutar sin efectos secundarios.
#
# - Docker y Compose v2 desde el repositorio oficial de Ubuntu (sin scripts descargados).
# - Archivo de intercambio de 2 GiB: la VM tiene 4 GiB de RAM y el stack (dos Postgres, el
#   backend, Nginx, Prometheus y Grafana) puede acercarse a ese límite en picos.
# - fail2ban: el puerto 22 está abierto a cualquier IP porque el equipo entra desde sitios
#   distintos; la autenticación es solo por llave y fail2ban frena los intentos masivos.
set -eu
export DEBIAN_FRONTEND=noninteractive

# Justo tras arrancar, el agente de Azure puede tener el bloqueo de apt unos segundos.
for intento in 1 2 3 4 5 6; do
    sudo apt-get update -qq && break
    sleep 10
done

sudo apt-get install -y -qq docker.io docker-compose-v2 fail2ban
sudo systemctl enable --now docker fail2ban
sudo usermod -aG docker "$USER"

if ! swapon --show --noheadings | grep -q '/swapfile'; then
    sudo fallocate -l 2G /swapfile
    sudo chmod 600 /swapfile
    sudo mkswap /swapfile > /dev/null
    sudo swapon /swapfile
    echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab > /dev/null
fi
echo 'vm.swappiness=10' | sudo tee /etc/sysctl.d/99-clickclak.conf > /dev/null
sudo sysctl -q --system

echo "--- verificación"
sudo docker --version
sudo docker compose version
swapon --show --noheadings
echo "fail2ban: $(systemctl is-active fail2ban)"
echo "ssh por contraseña: $(sudo sshd -T | awk '/^passwordauthentication/ {print $2}')"
