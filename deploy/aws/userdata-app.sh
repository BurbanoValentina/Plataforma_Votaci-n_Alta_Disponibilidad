#!/bin/bash
set -euo pipefail
exec > >(tee /var/log/voting-bootstrap.log | logger -t voting-app-init -s 2>/dev/console) 2>&1

# Replace CORE_PRIVATE_IP before launching EC2 App Nodes
CORE_PRIVATE_IP="__CORE_PRIVATE_IP__"
NODE_ID="__NODE_ID__"

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

echo "=== [3/5] Detecting AWS Availability Zone ==="
TOKEN=$(curl -s -X PUT "http://169.254.169.254/latest/api/token" -H "X-aws-ec2-metadata-token-ttl-seconds: 21600")
AZ=$(curl -s -H "X-aws-ec2-metadata-token: $TOKEN" http://169.254.169.254/latest/meta-data/placement/availability-zone)
echo "NODE_ID=${NODE_ID} AZ=${AZ} CORE_PRIVATE_IP=${CORE_PRIVATE_IP}"

echo "=== [4/5] Cloning Voting Platform Repository ==="
REPO_URL="https://github.com/BurbanoValentina/Plataforma_Votaci-n_Alta_Disponibilidad.git"
rm -rf /opt/voting-platform
git clone --depth 1 -b main "$REPO_URL" /opt/voting-platform

echo "=== [5/5] Building and Starting AWS App Edge Node (1 Container) ==="
cd /opt/voting-platform/deploy/aws
cat > .env <<EOF
NODE_ID=${NODE_ID}
AVAILABILITY_ZONE=${AZ}
CORE_PRIVATE_IP=${CORE_PRIVATE_IP}
DB_NAME=voting_db
DB_USER=voting_user
DB_PASSWORD=0512971401
EOF

docker compose -f docker-compose.app.yml up -d --build
echo "=== APP NODE ${NODE_ID} READY ON PORT 80 ==="
