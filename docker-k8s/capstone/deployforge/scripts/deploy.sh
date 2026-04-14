#!/usr/bin/env bash
# DeployForge — Deployment Helper
# Introduced in Module 04/06 (Kustomize + Helm deployments)
#
# Deploys DeployForge to the active Kubernetes cluster using either
# Kustomize overlays or the Helm chart.
#
# Usage:
#   ./scripts/deploy.sh dev               # Kustomize dev overlay
#   ./scripts/deploy.sh staging            # Kustomize staging overlay
#   ./scripts/deploy.sh prod               # Kustomize prod overlay
#   ./scripts/deploy.sh dev --helm         # Helm with dev values
#   ./scripts/deploy.sh prod --helm        # Helm with prod values

set -euo pipefail

ENVIRONMENT="${1:-dev}"
USE_HELM=false

if [[ "${2:-}" == "--helm" ]]; then
  USE_HELM=true
fi

VALID_ENVS=("dev" "staging" "prod")
if [[ ! " ${VALID_ENVS[*]} " =~ " ${ENVIRONMENT} " ]]; then
  echo "❌ Invalid environment: ${ENVIRONMENT}"
  echo "   Valid options: ${VALID_ENVS[*]}"
  exit 1
fi

echo "🚀 Deploying DeployForge to '${ENVIRONMENT}'..."
echo ""

if [[ "${USE_HELM}" == true ]]; then
  # ── Helm Deployment ─────────────────────────────────────────
  echo "📦 Strategy: Helm"

  VALUES_FILE="helm/deployforge/values-${ENVIRONMENT}.yaml"
  HELM_ARGS=(
    upgrade --install deployforge
    ./helm/deployforge
    --namespace deployforge
    --create-namespace
  )

  if [[ -f "${VALUES_FILE}" ]]; then
    echo "   Using values: ${VALUES_FILE}"
    HELM_ARGS+=(-f "${VALUES_FILE}")
  else
    echo "   ⚠️  No ${VALUES_FILE} found — using defaults"
  fi

  helm "${HELM_ARGS[@]}"

else
  # ── Kustomize Deployment ───────────────────────────────────
  echo "📦 Strategy: Kustomize"

  OVERLAY_DIR="k8s/overlays/${ENVIRONMENT}"
  if [[ ! -d "${OVERLAY_DIR}" ]]; then
    echo "❌ Overlay directory not found: ${OVERLAY_DIR}"
    exit 1
  fi

  echo "   Overlay: ${OVERLAY_DIR}"
  kubectl apply -k "${OVERLAY_DIR}"
fi

echo ""
echo "⏳ Waiting for rollout..."
kubectl -n deployforge rollout status deployment/api --timeout=120s || true

echo ""
echo "✅ Deployment complete!"
kubectl -n deployforge get pods
