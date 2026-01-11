#!/bin/bash
set -e

AWS_REGION="${AWS_REGION:-eu-west-3}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="${SCRIPT_DIR}/../.."

cd "${PROJECT_ROOT}/infrastructure/terraform"

ECR_REPO=$(terraform output -raw ecr_repository_url)
CLUSTER_NAME=$(terraform output -raw ecs_cluster_name)
SERVICE_NAME=$(terraform output -raw ecs_service_name)

cd "${PROJECT_ROOT}"

echo "Building Docker image..."
docker build -t raibu-backend .

echo "Logging in to ECR..."
aws ecr get-login-password --region "${AWS_REGION}" | docker login --username AWS --password-stdin "${ECR_REPO}"

echo "Pushing image to ECR..."
docker tag raibu-backend:latest "${ECR_REPO}:latest"
docker push "${ECR_REPO}:latest"

echo "Forcing new ECS deployment..."
aws ecs update-service \
  --cluster "${CLUSTER_NAME}" \
  --service "${SERVICE_NAME}" \
  --force-new-deployment \
  --region "${AWS_REGION}"

echo "Deployment completed."
