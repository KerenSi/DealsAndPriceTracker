#!/usr/bin/env bash
# Runs on the EC2 host (sent by .github/workflows/deploy.yml through SSM Run Command).
# Usage: remote-deploy.sh <registry> <image-tag>
set -euo pipefail

REGISTRY="$1"
IMAGE_TAG="$2"
APP_DIR=/opt/dealstracker
PARAM_PATH=/dealstracker/
REGION=$(TOKEN=$(curl -sS -X PUT http://169.254.169.254/latest/api/token -H "X-aws-ec2-metadata-token-ttl-seconds: 60") \
  && curl -sS -H "X-aws-ec2-metadata-token: $TOKEN" http://169.254.169.254/latest/meta-data/placement/region)

cd "$APP_DIR"

# Every parameter under /dealstracker/ becomes an env var, e.g. /dealstracker/ANTHROPIC_API_KEY.
# New secrets (VAPID keys for push, etc.) only need an `aws ssm put-parameter` and a redeploy.
{
  echo "REGISTRY=$REGISTRY"
  echo "IMAGE_TAG=$IMAGE_TAG"
  aws ssm get-parameters-by-path --region "$REGION" --path "$PARAM_PATH" --with-decryption \
    --query 'Parameters[].[Name,Value]' --output text \
    | awk -F'\t' -v p="$PARAM_PATH" '{ sub(p, "", $1); print $1 "=" $2 }'
} > .env.new
chmod 600 .env.new
mv .env.new .env

aws ecr get-login-password --region "$REGION" | docker login --username AWS --password-stdin "$REGISTRY"
docker compose -f compose.prod.yml pull --quiet
docker compose -f compose.prod.yml up -d --remove-orphans
docker image prune -af --filter "until=168h" >/dev/null

docker compose -f compose.prod.yml ps
