#!/bin/bash
# First-boot setup for the app host (Amazon Linux 2023). The app itself arrives
# with the first GitHub Actions deploy.
set -euxo pipefail

dnf install -y docker
systemctl enable --now docker

COMPOSE_VERSION=v2.29.7
mkdir -p /usr/local/lib/docker/cli-plugins
curl -sSfL "https://github.com/docker/compose/releases/download/${COMPOSE_VERSION}/docker-compose-linux-x86_64" \
  -o /usr/local/lib/docker/cli-plugins/docker-compose
chmod +x /usr/local/lib/docker/cli-plugins/docker-compose

# 2 GiB swap as headroom for the JVM + Next.js on a 2 GiB instance.
if [ ! -f /swapfile ]; then
  dd if=/dev/zero of=/swapfile bs=1M count=2048
  chmod 600 /swapfile
  mkswap /swapfile
  echo '/swapfile none swap sw 0 0' >> /etc/fstab
fi
swapon -a

mkdir -p /opt/dealstracker
