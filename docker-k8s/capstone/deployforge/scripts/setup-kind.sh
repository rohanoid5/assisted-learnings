#!/usr/bin/env bash
# DeployForge — Kind Cluster Setup
# Introduced in Module 04 (Local Kubernetes with Kind)
#
# Creates a multi-node Kind cluster with an Nginx Ingress controller
# and the deployforge namespace.
#
# Usage:
#   ./scripts/setup-kind.sh          # create cluster
#   ./scripts/setup-kind.sh teardown  # delete cluster

set -euo pipefail

CLUSTER_NAME="deployforge"
KIND_CONFIG=$(cat <<'EOF'
kind: Cluster
apiVersion: kind.x-k8s.io/v1alpha4
nodes:
  - role: control-plane
    kubeadmConfigPatches:
      - |
        kind: InitConfiguration
        nodeRegistration:
          kubeletExtraArgs:
            node-labels: "ingress-ready=true"
    extraPortMappings:
      - containerPort: 80
        hostPort: 80
        protocol: TCP
      - containerPort: 443
        hostPort: 443
        protocol: TCP
  - role: worker
  - role: worker
EOF
)

teardown() {
  echo "🗑  Deleting Kind cluster '${CLUSTER_NAME}'..."
  kind delete cluster --name "${CLUSTER_NAME}"
  echo "✅ Cluster deleted."
}

create_cluster() {
  if kind get clusters 2>/dev/null | grep -q "^${CLUSTER_NAME}$"; then
    echo "⚠️  Cluster '${CLUSTER_NAME}' already exists. Use '$0 teardown' to remove it."
    exit 0
  fi

  echo "🚀 Creating Kind cluster '${CLUSTER_NAME}' (1 control-plane + 2 workers)..."
  echo "${KIND_CONFIG}" | kind create cluster --name "${CLUSTER_NAME}" --config=-

  echo ""
  echo "📦 Installing Nginx Ingress Controller..."
  kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/main/deploy/static/provider/kind/deploy.yaml

  echo ""
  echo "⏳ Waiting for Ingress controller to be ready..."
  kubectl wait --namespace ingress-nginx \
    --for=condition=ready pod \
    --selector=app.kubernetes.io/component=controller \
    --timeout=120s

  echo ""
  echo "📁 Creating deployforge namespace..."
  kubectl apply -f k8s/base/namespace.yaml

  echo ""
  echo "✅ Kind cluster '${CLUSTER_NAME}' is ready!"
  echo "   Nodes:"
  kubectl get nodes
  echo ""
  echo "   Next steps:"
  echo "     ./scripts/deploy.sh dev    # deploy with Kustomize"
  echo "     kubectl -n deployforge get pods"
}

# ── Main ────────────────────────────────────────────────────────
case "${1:-create}" in
  teardown|delete|destroy)
    teardown
    ;;
  create|"")
    create_cluster
    ;;
  *)
    echo "Usage: $0 [create|teardown]"
    exit 1
    ;;
esac
