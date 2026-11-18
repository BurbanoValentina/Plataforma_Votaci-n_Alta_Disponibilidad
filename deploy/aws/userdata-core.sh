#!/bin/bash
set -euo pipefail
exec > >(tee /var/log/voting-bootstrap.log | logger -t voting-core-init -s 2>/dev/console) 2>&1

echo "=== [1/5] Configuring 2GB Swap ==="
if [ ! -f /swapfile ]; then
    fallocate -l 2G /swapfile
    chmod 600 /swapfile
    mkswap /swapfile
    swapon /swapfile
    echo '/swapfile none swap sw 0 0' >> /etc/fstab
fi

echo "=== [2/5] Installing Docker, Compose Plugin & Git ==="
apt-get update -y
apt-get install -y ca-certificates curl git docker.io docker-compose-v2
systemctl enable --now docker
usermod -aG docker ubuntu || true

echo "=== [3/5] Detecting AWS Private IP via IMDSv2 ==="
TOKEN=$(curl -s -X PUT "http://169.254.169.254/latest/api/token" -H "X-aws-ec2-metadata-token-ttl-seconds: 21600")
CORE_PRIVATE_IP=$(curl -s -H "X-aws-ec2-metadata-token: $TOKEN" http://169.254.169.254/latest/meta-data/local-ipv4)
echo "CORE_PRIVATE_IP=$CORE_PRIVATE_IP"

echo "=== [4/5] Cloning Voting Platform Repository ==="
REPO_URL="https://github.com/BurbanoValentina/Plataforma_Votaci-n_Alta_Disponibilidad.git"
rm -rf /opt/voting-platform
git clone --depth 1 -b main "$REPO_URL" /opt/voting-platform

echo "=== [5/5] Building and Starting AWS Core Stack (4 Containers) ==="
cd /opt/voting-platform/deploy/aws
cat > .env <<EOF
CORE_PRIVATE_IP=${CORE_PRIVATE_IP}
DB_NAME=voting_db
DB_USER=voting_user
DB_PASSWORD=0512971401
EOF

docker compose -f docker-compose.core.yml up -d --build
echo "=== CORE NODE READY (Private IP: ${CORE_PRIVATE_IP}) ==="
