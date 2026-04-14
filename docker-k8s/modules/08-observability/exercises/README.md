# Module 08 — Exercises

Hands-on practice with Prometheus metrics, OpenTelemetry tracing, structured logging, and Grafana dashboards. These exercises build the complete observability stack for DeployForge — from metric scraping through end-to-end trace-to-log correlation and alerting.

> **⚠️ Prerequisite:** You need a running kind cluster with the `deployforge` namespace and workloads from Module 07. If you don't have one, run:
> ```bash
> cat <<EOF | kind create cluster --config=-
> kind: Cluster
> apiVersion: kind.x-k8s.io/v1alpha4
> name: deployforge
> nodes:
> - role: control-plane
>   kubeadmConfigPatches:
>   - |
>     kind: InitConfiguration
>     nodeRegistration:
>       kubeletExtraArgs:
>         node-labels: "ingress-ready=true"
>   extraPortMappings:
>   - containerPort: 80
>     hostPort: 80
>     protocol: TCP
>   - containerPort: 443
>     hostPort: 443
>     protocol: TCP
> - role: worker
> - role: worker
> EOF
> kubectl create namespace deployforge
> kubectl config set-context --current --namespace=deployforge
> ```
>
> You'll also need Helm installed:
> ```bash
> brew install helm
> ```

---

## Exercise 1: Deploy Prometheus and Scrape DeployForge Metrics

**Goal:** Deploy the kube-prometheus-stack to your kind cluster, create a ServiceMonitor, and verify that Prometheus is scraping metrics from a test application that simulates the DeployForge API Gateway.

### Steps

1. **Add the Helm repo and install kube-prometheus-stack:**

```bash
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo update

helm install prometheus prometheus-community/kube-prometheus-stack \
  --namespace monitoring \
  --create-namespace \
  --set prometheus.prometheusSpec.serviceMonitorSelectorNilUsesHelmValues=false \
  --set prometheus.prometheusSpec.podMonitorSelectorNilUsesHelmValues=false \
  --set grafana.adminPassword='deployforge-admin' \
  --wait --timeout=5m
```

2. **Deploy a test metrics server that simulates DeployForge API Gateway metrics:**

```yaml
# metrics-test-server.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: api-gateway-sim
  namespace: deployforge
spec:
  replicas: 2
  selector:
    matchLabels:
      app: api-gateway
  template:
    metadata:
      labels:
        app: api-gateway
      annotations:
        prometheus.io/scrape: "true"
        prometheus.io/port: "9090"
    spec:
      containers:
      - name: metrics-sim
        image: quay.io/brancz/prometheus-example-app:v0.5.0
        ports:
        - containerPort: 8080
          name: http
        - containerPort: 9090
          name: http-metrics
        resources:
          requests:
            cpu: 50m
            memory: 64Mi
          limits:
            cpu: 100m
            memory: 128Mi
---
apiVersion: v1
kind: Service
metadata:
  name: api-gateway
  namespace: deployforge
  labels:
    app: api-gateway
spec:
  selector:
    app: api-gateway
  ports:
  - name: http
    port: 8080
    targetPort: 8080
  - name: http-metrics
    port: 9090
    targetPort: 9090
```

3. **Create a ServiceMonitor to configure Prometheus scraping:**

```yaml
# api-gateway-servicemonitor.yaml
apiVersion: monitoring.coreos.com/v1
kind: ServiceMonitor
metadata:
  name: api-gateway
  namespace: deployforge
  labels:
    release: prometheus
spec:
  selector:
    matchLabels:
      app: api-gateway
  endpoints:
  - port: http-metrics
    interval: 15s
    path: /metrics
  namespaceSelector:
    matchNames:
    - deployforge
```

4. **Apply everything and wait for pods to be ready.**

5. **Port-forward to Prometheus and verify the target is being scraped:**

```bash
kubectl port-forward -n monitoring svc/prometheus-kube-prometheus-prometheus 9090:9090 &
```

Navigate to http://localhost:9090/targets and look for the `api-gateway` target.

6. **Write a PromQL query that shows the request rate.**

7. **Create a recording rule for the API Gateway request rate:**

```yaml
# recording-rules.yaml
apiVersion: monitoring.coreos.com/v1
kind: PrometheusRule
metadata:
  name: deployforge-recording-rules
  namespace: deployforge
  labels:
    release: prometheus
spec:
  groups:
  - name: deployforge.recording
    interval: 30s
    rules:
    - record: deployforge:api_gateway:request_rate:5m
      expr: sum(rate(http_requests_total{service="api-gateway"}[5m]))
```

### Verification

```bash
# All monitoring pods should be Running
kubectl get pods -n monitoring
# → prometheus-kube-prometheus-operator-xxx   1/1 Running
# → prometheus-prometheus-kube-prometheus-prometheus-0   2/2 Running
# → prometheus-grafana-xxx   3/3 Running
# → alertmanager-prometheus-kube-prometheus-alertmanager-0   2/2 Running

# API Gateway sim should be Running
kubectl get pods -n deployforge -l app=api-gateway
# → api-gateway-sim-xxx   1/1 Running   (×2)

# ServiceMonitor should exist
kubectl get servicemonitor -n deployforge
# → api-gateway

# Target should be UP in Prometheus
curl -s http://localhost:9090/api/v1/targets | \
  jq '.data.activeTargets[] | select(.labels.service=="api-gateway") | {instance, health}'
# → { "instance": "10.244.x.x:9090", "health": "up" }
```

<details>
<summary>Show solution</summary>

```bash
#!/bin/bash
set -euo pipefail

NS="deployforge"

echo "=== Step 1: Install kube-prometheus-stack ==="
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo update

helm install prometheus prometheus-community/kube-prometheus-stack \
  --namespace monitoring \
  --create-namespace \
  --set prometheus.prometheusSpec.serviceMonitorSelectorNilUsesHelmValues=false \
  --set prometheus.prometheusSpec.podMonitorSelectorNilUsesHelmValues=false \
  --set grafana.adminPassword='deployforge-admin' \
  --wait --timeout=5m

echo "=== Step 2: Deploy API Gateway simulator ==="
cat <<'EOF' | kubectl apply -f -
apiVersion: apps/v1
kind: Deployment
metadata:
  name: api-gateway-sim
  namespace: deployforge
spec:
  replicas: 2
  selector:
    matchLabels:
      app: api-gateway
  template:
    metadata:
      labels:
        app: api-gateway
    spec:
      containers:
      - name: metrics-sim
        image: quay.io/brancz/prometheus-example-app:v0.5.0
        ports:
        - containerPort: 8080
          name: http
        - containerPort: 9090
          name: http-metrics
        resources:
          requests:
            cpu: 50m
            memory: 64Mi
          limits:
            cpu: 100m
            memory: 128Mi
---
apiVersion: v1
kind: Service
metadata:
  name: api-gateway
  namespace: deployforge
  labels:
    app: api-gateway
spec:
  selector:
    app: api-gateway
  ports:
  - name: http
    port: 8080
    targetPort: 8080
  - name: http-metrics
    port: 9090
    targetPort: 9090
EOF

echo "=== Step 3: Create ServiceMonitor ==="
cat <<'EOF' | kubectl apply -f -
apiVersion: monitoring.coreos.com/v1
kind: ServiceMonitor
metadata:
  name: api-gateway
  namespace: deployforge
  labels:
    release: prometheus
spec:
  selector:
    matchLabels:
      app: api-gateway
  endpoints:
  - port: http-metrics
    interval: 15s
    path: /metrics
  namespaceSelector:
    matchNames:
    - deployforge
EOF

echo "=== Step 4: Wait for pods ==="
kubectl wait --for=condition=Ready pod -l app.kubernetes.io/name=prometheus -n monitoring --timeout=180s
kubectl wait --for=condition=Ready pod -l app=api-gateway -n $NS --timeout=120s

echo "=== Step 5: Create recording rules ==="
cat <<'EOF' | kubectl apply -f -
apiVersion: monitoring.coreos.com/v1
kind: PrometheusRule
metadata:
  name: deployforge-recording-rules
  namespace: deployforge
  labels:
    release: prometheus
spec:
  groups:
  - name: deployforge.recording
    interval: 30s
    rules:
    - record: deployforge:api_gateway:request_rate:5m
      expr: sum(rate(http_requests_total{service="api-gateway"}[5m]))
EOF

echo "=== Step 6: Verify ==="
kubectl port-forward -n monitoring svc/prometheus-kube-prometheus-prometheus 9090:9090 &
sleep 5

echo "Active targets:"
curl -s http://localhost:9090/api/v1/targets | \
  jq '.data.activeTargets[] | select(.discoveredLabels.__meta_kubernetes_namespace=="deployforge") | {instance: .labels.instance, health}'

echo ""
echo "Recording rules:"
curl -s http://localhost:9090/api/v1/rules | \
  jq '.data.groups[] | select(.name=="deployforge.recording") | .rules[].name'

echo ""
echo "Prometheus UI: http://localhost:9090"
echo "Grafana: kubectl port-forward -n monitoring svc/prometheus-grafana 3000:80"
echo "         http://localhost:3000 (admin / deployforge-admin)"
```

</details>

---

## Exercise 2: Instrument DeployForge API with OpenTelemetry

**Goal:** Deploy the OpenTelemetry Collector and Jaeger in the kind cluster. Create a test application that generates traces with parent-child spans. Verify traces appear in Jaeger with correct span hierarchy.

### Steps

1. **Deploy Jaeger (all-in-one mode for development):**

```yaml
# jaeger.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: jaeger
  namespace: deployforge
spec:
  replicas: 1
  selector:
    matchLabels:
      app: jaeger
  template:
    metadata:
      labels:
        app: jaeger
    spec:
      containers:
      - name: jaeger
        image: jaegertracing/all-in-one:1.53
        ports:
        - containerPort: 16686
          name: ui
        - containerPort: 4317
          name: otlp-grpc
        - containerPort: 4318
          name: otlp-http
        env:
        - name: COLLECTOR_OTLP_ENABLED
          value: "true"
        resources:
          requests:
            memory: 256Mi
            cpu: 100m
          limits:
            memory: 512Mi
            cpu: 500m
---
apiVersion: v1
kind: Service
metadata:
  name: jaeger-collector
  namespace: deployforge
spec:
  selector:
    app: jaeger
  ports:
  - name: otlp-grpc
    port: 4317
    targetPort: 4317
  - name: otlp-http
    port: 4318
    targetPort: 4318
---
apiVersion: v1
kind: Service
metadata:
  name: jaeger-query
  namespace: deployforge
spec:
  selector:
    app: jaeger
  ports:
  - name: ui
    port: 16686
    targetPort: 16686
```

2. **Deploy the OTel Collector with a ConfigMap:**

```yaml
# otel-collector.yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: otel-collector-config
  namespace: deployforge
data:
  config.yaml: |
    receivers:
      otlp:
        protocols:
          grpc:
            endpoint: 0.0.0.0:4317
          http:
            endpoint: 0.0.0.0:4318
    processors:
      batch:
        timeout: 5s
        send_batch_size: 1024
    exporters:
      otlp/jaeger:
        endpoint: jaeger-collector:4317
        tls:
          insecure: true
    service:
      pipelines:
        traces:
          receivers: [otlp]
          processors: [batch]
          exporters: [otlp/jaeger]
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: otel-collector
  namespace: deployforge
spec:
  replicas: 1
  selector:
    matchLabels:
      app: otel-collector
  template:
    metadata:
      labels:
        app: otel-collector
    spec:
      containers:
      - name: collector
        image: otel/opentelemetry-collector-contrib:0.92.0
        args: ["--config=/conf/config.yaml"]
        ports:
        - containerPort: 4317
          name: otlp-grpc
        - containerPort: 4318
          name: otlp-http
        volumeMounts:
        - name: config
          mountPath: /conf
        resources:
          requests:
            memory: 256Mi
            cpu: 100m
          limits:
            memory: 512Mi
            cpu: 500m
      volumes:
      - name: config
        configMap:
          name: otel-collector-config
---
apiVersion: v1
kind: Service
metadata:
  name: otel-collector
  namespace: deployforge
spec:
  selector:
    app: otel-collector
  ports:
  - name: otlp-grpc
    port: 4317
    targetPort: 4317
  - name: otlp-http
    port: 4318
    targetPort: 4318
```

3. **Deploy a trace-generating test pod that sends OTLP spans to the Collector:**

```yaml
# trace-generator.yaml
apiVersion: batch/v1
kind: Job
metadata:
  name: trace-generator
  namespace: deployforge
spec:
  template:
    spec:
      containers:
      - name: tracegen
        image: ghcr.io/open-telemetry/opentelemetry-collector-contrib/telemetrygen:v0.92.0
        args:
        - traces
        - --otlp-endpoint=otel-collector:4317
        - --otlp-insecure
        - --traces=10
        - --service=api-gateway
        - --rate=1
      restartPolicy: Never
  backoffLimit: 1
```

4. **Port-forward to Jaeger and verify traces appear:**

```bash
kubectl port-forward -n deployforge svc/jaeger-query 16686:16686 &
```

Navigate to http://localhost:16686, select the `api-gateway` service, and click "Find Traces."

### Verification

```bash
# Jaeger should be running
kubectl get pods -n deployforge -l app=jaeger
# → jaeger-xxx   1/1 Running

# OTel Collector should be running
kubectl get pods -n deployforge -l app=otel-collector
# → otel-collector-xxx   1/1 Running

# Trace generator job should have completed
kubectl get jobs -n deployforge
# → trace-generator   1/1

# Jaeger API should return services
kubectl port-forward -n deployforge svc/jaeger-query 16686:16686 &
sleep 3
curl -s http://localhost:16686/api/services | jq '.data'
# → Should include "api-gateway"

# Traces should exist
curl -s 'http://localhost:16686/api/traces?service=api-gateway&limit=5' | jq '.data | length'
# → 10 (or more)
```

<details>
<summary>Show solution</summary>

```bash
#!/bin/bash
set -euo pipefail

NS="deployforge"

echo "=== Step 1: Deploy Jaeger ==="
cat <<'EOF' | kubectl apply -f -
apiVersion: apps/v1
kind: Deployment
metadata:
  name: jaeger
  namespace: deployforge
spec:
  replicas: 1
  selector:
    matchLabels:
      app: jaeger
  template:
    metadata:
      labels:
        app: jaeger
    spec:
      containers:
      - name: jaeger
        image: jaegertracing/all-in-one:1.53
        ports:
        - containerPort: 16686
          name: ui
        - containerPort: 4317
          name: otlp-grpc
        - containerPort: 4318
          name: otlp-http
        env:
        - name: COLLECTOR_OTLP_ENABLED
          value: "true"
        resources:
          requests:
            memory: 256Mi
            cpu: 100m
          limits:
            memory: 512Mi
            cpu: 500m
---
apiVersion: v1
kind: Service
metadata:
  name: jaeger-collector
  namespace: deployforge
spec:
  selector:
    app: jaeger
  ports:
  - name: otlp-grpc
    port: 4317
    targetPort: 4317
  - name: otlp-http
    port: 4318
    targetPort: 4318
---
apiVersion: v1
kind: Service
metadata:
  name: jaeger-query
  namespace: deployforge
spec:
  selector:
    app: jaeger
  ports:
  - name: ui
    port: 16686
    targetPort: 16686
EOF

echo "=== Step 2: Deploy OTel Collector ==="
cat <<'EOF' | kubectl apply -f -
apiVersion: v1
kind: ConfigMap
metadata:
  name: otel-collector-config
  namespace: deployforge
data:
  config.yaml: |
    receivers:
      otlp:
        protocols:
          grpc:
            endpoint: 0.0.0.0:4317
          http:
            endpoint: 0.0.0.0:4318
    processors:
      batch:
        timeout: 5s
        send_batch_size: 1024
    exporters:
      otlp/jaeger:
        endpoint: jaeger-collector:4317
        tls:
          insecure: true
    service:
      pipelines:
        traces:
          receivers: [otlp]
          processors: [batch]
          exporters: [otlp/jaeger]
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: otel-collector
  namespace: deployforge
spec:
  replicas: 1
  selector:
    matchLabels:
      app: otel-collector
  template:
    metadata:
      labels:
        app: otel-collector
    spec:
      containers:
      - name: collector
        image: otel/opentelemetry-collector-contrib:0.92.0
        args: ["--config=/conf/config.yaml"]
        ports:
        - containerPort: 4317
          name: otlp-grpc
        - containerPort: 4318
          name: otlp-http
        volumeMounts:
        - name: config
          mountPath: /conf
        resources:
          requests:
            memory: 256Mi
            cpu: 100m
          limits:
            memory: 512Mi
            cpu: 500m
      volumes:
      - name: config
        configMap:
          name: otel-collector-config
---
apiVersion: v1
kind: Service
metadata:
  name: otel-collector
  namespace: deployforge
spec:
  selector:
    app: otel-collector
  ports:
  - name: otlp-grpc
    port: 4317
    targetPort: 4317
  - name: otlp-http
    port: 4318
    targetPort: 4318
EOF

echo "=== Step 3: Wait for pods ==="
kubectl wait --for=condition=Ready pod -l app=jaeger -n $NS --timeout=120s
kubectl wait --for=condition=Ready pod -l app=otel-collector -n $NS --timeout=120s

echo "=== Step 4: Generate test traces ==="
cat <<'EOF' | kubectl apply -f -
apiVersion: batch/v1
kind: Job
metadata:
  name: trace-generator
  namespace: deployforge
spec:
  template:
    spec:
      containers:
      - name: tracegen
        image: ghcr.io/open-telemetry/opentelemetry-collector-contrib/telemetrygen:v0.92.0
        args:
        - traces
        - --otlp-endpoint=otel-collector:4317
        - --otlp-insecure
        - --traces=10
        - --service=api-gateway
        - --rate=1
      restartPolicy: Never
  backoffLimit: 1
EOF

kubectl wait --for=condition=Complete job/trace-generator -n $NS --timeout=60s

echo "=== Step 5: Verify traces in Jaeger ==="
kubectl port-forward -n $NS svc/jaeger-query 16686:16686 &
sleep 5

echo "Services in Jaeger:"
curl -s http://localhost:16686/api/services | jq '.data'

echo ""
echo "Trace count for api-gateway:"
curl -s 'http://localhost:16686/api/traces?service=api-gateway&limit=20' | jq '.data | length'

echo ""
echo "Jaeger UI: http://localhost:16686"
```

</details>

---

## Exercise 3: Implement Structured Logging with Fluent Bit and Loki

**Goal:** Deploy Loki and Fluent Bit to the kind cluster. Run a pod that generates structured JSON logs. Verify logs are collected by Fluent Bit and queryable in Loki.

### Steps

1. **Install Loki using Helm:**

```bash
helm repo add grafana https://grafana.github.io/helm-charts
helm repo update

helm install loki grafana/loki-stack \
  --namespace deployforge \
  --set loki.persistence.enabled=false \
  --set promtail.enabled=false \
  --set grafana.enabled=false \
  --wait
```

2. **Deploy Fluent Bit as a DaemonSet with the ConfigMap from section 03:**

Create the RBAC resources (ServiceAccount, ClusterRole, ClusterRoleBinding), the ConfigMap, and the DaemonSet.

3. **Deploy a pod that generates structured JSON logs with correlation IDs:**

```yaml
# structured-log-generator.yaml
apiVersion: v1
kind: Pod
metadata:
  name: log-generator
  namespace: deployforge
  labels:
    app: log-generator
spec:
  containers:
  - name: generator
    image: busybox:1.36
    command: ['sh', '-c']
    args:
    - |
      REQ_ID="req-$(date +%s)"
      TRACE_ID="abcdef1234567890abcdef1234567890"

      while true; do
        echo "{\"level\":\"info\",\"msg\":\"request_started\",\"service\":\"api-gateway\",\"request_id\":\"$REQ_ID\",\"trace_id\":\"$TRACE_ID\",\"http\":{\"method\":\"POST\",\"url\":\"/api/deployments\"}}"
        sleep 2
        echo "{\"level\":\"info\",\"msg\":\"db_query\",\"service\":\"api-gateway\",\"request_id\":\"$REQ_ID\",\"trace_id\":\"$TRACE_ID\",\"db\":{\"system\":\"postgresql\",\"duration_ms\":15}}"
        sleep 2
        echo "{\"level\":\"warn\",\"msg\":\"slow_query\",\"service\":\"api-gateway\",\"request_id\":\"$REQ_ID\",\"trace_id\":\"$TRACE_ID\",\"db\":{\"system\":\"postgresql\",\"duration_ms\":850}}"
        sleep 2
        echo "{\"level\":\"error\",\"msg\":\"connection_timeout\",\"service\":\"api-gateway\",\"request_id\":\"$REQ_ID\",\"trace_id\":\"$TRACE_ID\",\"error\":\"redis connection refused\",\"target\":\"redis:6379\"}"
        sleep 2
        echo "{\"level\":\"info\",\"msg\":\"request_completed\",\"service\":\"api-gateway\",\"request_id\":\"$REQ_ID\",\"trace_id\":\"$TRACE_ID\",\"http\":{\"status_code\":500,\"duration_ms\":1870}}"
        sleep 10

        REQ_ID="req-$(date +%s)"
      done
  restartPolicy: Never
```

4. **Wait for Fluent Bit to collect the logs (~30 seconds).**

5. **Query Loki to verify logs are arriving:**

```bash
kubectl port-forward -n deployforge svc/loki 3100:3100 &
sleep 3

# All logs from deployforge namespace
curl -s 'http://localhost:3100/loki/api/v1/query_range' \
  --data-urlencode 'query={namespace="deployforge"}' \
  --data-urlencode 'limit=5' | jq '.data.result[].values[][1]'

# Filter for error logs
curl -s 'http://localhost:3100/loki/api/v1/query_range' \
  --data-urlencode 'query={namespace="deployforge"} |= "error"' \
  --data-urlencode 'limit=5' | jq '.data.result[].values[][1]'
```

6. **Write a LogQL query that counts errors per minute.**

### Verification

```bash
# Fluent Bit should be running on every node
kubectl get pods -n deployforge -l app=fluent-bit
# → fluent-bit-xxx   Running   (one per node)

# Loki should be running
kubectl get pods -n deployforge -l app=loki
# → loki-0   1/1 Running

# Log generator should be emitting logs
kubectl logs -n deployforge log-generator --tail=5
# → JSON lines with level, msg, service, request_id, trace_id

# Loki should have labels
curl -s http://localhost:3100/loki/api/v1/label | jq '.data'
# → Should include "namespace", "pod", "container"

# Loki should return log lines
curl -s 'http://localhost:3100/loki/api/v1/query_range' \
  --data-urlencode 'query={namespace="deployforge", pod="log-generator"}' \
  --data-urlencode 'limit=3' | jq '.data.result | length'
# → Should be > 0
```

<details>
<summary>Show solution</summary>

```bash
#!/bin/bash
set -euo pipefail

NS="deployforge"

echo "=== Step 1: Install Loki ==="
helm repo add grafana https://grafana.github.io/helm-charts
helm repo update

helm install loki grafana/loki-stack \
  --namespace $NS \
  --set loki.persistence.enabled=false \
  --set promtail.enabled=false \
  --set grafana.enabled=false \
  --wait

echo "=== Step 2: Deploy Fluent Bit RBAC ==="
cat <<'EOF' | kubectl apply -f -
apiVersion: v1
kind: ServiceAccount
metadata:
  name: fluent-bit
  namespace: deployforge
---
apiVersion: rbac.authorization.k8s.io/v1
kind: ClusterRole
metadata:
  name: fluent-bit
rules:
- apiGroups: [""]
  resources: ["namespaces", "pods"]
  verbs: ["get", "list", "watch"]
---
apiVersion: rbac.authorization.k8s.io/v1
kind: ClusterRoleBinding
metadata:
  name: fluent-bit
roleRef:
  apiGroup: rbac.authorization.k8s.io
  kind: ClusterRole
  name: fluent-bit
subjects:
- kind: ServiceAccount
  name: fluent-bit
  namespace: deployforge
EOF

echo "=== Step 3: Deploy Fluent Bit ConfigMap ==="
cat <<'EOF' | kubectl apply -f -
apiVersion: v1
kind: ConfigMap
metadata:
  name: fluent-bit-config
  namespace: deployforge
data:
  fluent-bit.conf: |
    [SERVICE]
        Flush        5
        Log_Level    info
        Daemon       off
        Parsers_File parsers.conf

    [INPUT]
        Name             tail
        Path             /var/log/containers/*.log
        Parser           cri
        Tag              kube.*
        Refresh_Interval 5
        Mem_Buf_Limit    10MB
        Skip_Long_Lines  On

    [FILTER]
        Name                kubernetes
        Match               kube.*
        Kube_URL            https://kubernetes.default.svc:443
        Kube_CA_File        /var/run/secrets/kubernetes.io/serviceaccount/ca.crt
        Kube_Token_File     /var/run/secrets/kubernetes.io/serviceaccount/token
        Merge_Log           On
        Merge_Log_Key       log_parsed
        K8S-Logging.Parser  On
        K8S-Logging.Exclude On

    [OUTPUT]
        Name       loki
        Match      kube.*
        Host       loki.deployforge.svc.cluster.local
        Port       3100
        Labels     namespace=$kubernetes['namespace_name'], pod=$kubernetes['pod_name'], container=$kubernetes['container_name']
        Auto_Kubernetes_Labels Off
        Line_Format json

  parsers.conf: |
    [PARSER]
        Name   cri
        Format regex
        Regex  ^(?<time>[^ ]+) (?<stream>stdout|stderr) (?<logtag>[^ ]*) (?<log>.*)$
        Time_Key    time
        Time_Format %Y-%m-%dT%H:%M:%S.%L%z
EOF

echo "=== Step 4: Deploy Fluent Bit DaemonSet ==="
cat <<'EOF' | kubectl apply -f -
apiVersion: apps/v1
kind: DaemonSet
metadata:
  name: fluent-bit
  namespace: deployforge
  labels:
    app: fluent-bit
spec:
  selector:
    matchLabels:
      app: fluent-bit
  template:
    metadata:
      labels:
        app: fluent-bit
    spec:
      serviceAccountName: fluent-bit
      tolerations:
      - key: node-role.kubernetes.io/control-plane
        operator: Exists
        effect: NoSchedule
      containers:
      - name: fluent-bit
        image: fluent/fluent-bit:2.2
        volumeMounts:
        - name: varlog
          mountPath: /var/log
          readOnly: true
        - name: config
          mountPath: /fluent-bit/etc/
        resources:
          requests:
            cpu: 50m
            memory: 64Mi
          limits:
            cpu: 200m
            memory: 128Mi
      volumes:
      - name: varlog
        hostPath:
          path: /var/log
      - name: config
        configMap:
          name: fluent-bit-config
EOF

echo "=== Step 5: Deploy structured log generator ==="
cat <<'EOF' | kubectl apply -f -
apiVersion: v1
kind: Pod
metadata:
  name: log-generator
  namespace: deployforge
  labels:
    app: log-generator
spec:
  containers:
  - name: generator
    image: busybox:1.36
    command: ['sh', '-c']
    args:
    - |
      REQ_ID="req-$(date +%s)"
      TRACE_ID="abcdef1234567890abcdef1234567890"
      while true; do
        echo "{\"level\":\"info\",\"msg\":\"request_started\",\"service\":\"api-gateway\",\"request_id\":\"$REQ_ID\",\"trace_id\":\"$TRACE_ID\",\"http\":{\"method\":\"POST\",\"url\":\"/api/deployments\"}}"
        sleep 2
        echo "{\"level\":\"error\",\"msg\":\"connection_timeout\",\"service\":\"api-gateway\",\"request_id\":\"$REQ_ID\",\"trace_id\":\"$TRACE_ID\",\"error\":\"redis connection refused\"}"
        sleep 2
        echo "{\"level\":\"info\",\"msg\":\"request_completed\",\"service\":\"api-gateway\",\"request_id\":\"$REQ_ID\",\"trace_id\":\"$TRACE_ID\",\"http\":{\"status_code\":500,\"duration_ms\":1870}}"
        sleep 10
        REQ_ID="req-$(date +%s)"
      done
  restartPolicy: Never
EOF

echo "=== Step 6: Wait and verify ==="
kubectl wait --for=condition=Ready pod -l app=fluent-bit -n $NS --timeout=120s
kubectl wait --for=condition=Ready pod/log-generator -n $NS --timeout=60s

echo "Waiting 30s for logs to be collected..."
sleep 30

kubectl port-forward -n $NS svc/loki 3100:3100 &
sleep 3

echo ""
echo "Labels in Loki:"
curl -s http://localhost:3100/loki/api/v1/label | jq '.data'

echo ""
echo "Log entries from log-generator:"
curl -s 'http://localhost:3100/loki/api/v1/query_range' \
  --data-urlencode 'query={namespace="deployforge", pod="log-generator"}' \
  --data-urlencode 'limit=5' | jq '.data.result[].values[][1]'

echo ""
echo "Error logs only:"
curl -s 'http://localhost:3100/loki/api/v1/query_range' \
  --data-urlencode 'query={namespace="deployforge"} |= "error"' \
  --data-urlencode 'limit=5' | jq '.data.result[].values[][1]'
```

</details>

---

## Exercise 4: Build a Grafana Dashboard with Alerts

**Goal:** Create a Grafana dashboard for DeployForge with RED method panels, configure a Prometheus alerting rule for high error rate, and verify the alert fires by simulating an error spike.

### Steps

1. **Access Grafana (deployed in Exercise 1):**

```bash
kubectl port-forward -n monitoring svc/prometheus-grafana 3000:80 &
```

Navigate to http://localhost:3000 and log in with `admin` / `deployforge-admin`.

2. **Add the Loki datasource:**

Go to Configuration → Data Sources → Add data source → Loki. Set the URL to `http://loki.deployforge:3100`. Save & test.

3. **Add the Jaeger datasource:**

Go to Configuration → Data Sources → Add data source → Jaeger. Set the URL to `http://jaeger-query.deployforge:16686`. Save & test.

4. **Configure Loki derived fields for trace correlation:**

In the Loki datasource settings → Derived Fields:
- Name: `TraceID`
- Regex: `"trace_id":"([a-f0-9]+)"`
- Internal link: select the Jaeger datasource

5. **Create a new dashboard with these panels:**

- **Row 1:** Stat panels — Request Rate, Error %, p99 Latency
- **Row 2:** Time-series — Request Rate by Status Class, Latency Percentiles (p50, p95, p99)
- **Row 3:** Table — Top endpoints by error rate
- **Row 4:** Logs panel — Recent error logs from Loki

6. **Create an alerting rule for high error rate:**

```yaml
# error-rate-alert.yaml
apiVersion: monitoring.coreos.com/v1
kind: PrometheusRule
metadata:
  name: deployforge-error-rate-alert
  namespace: deployforge
  labels:
    release: prometheus
spec:
  groups:
  - name: deployforge.alerts
    rules:
    - alert: APIGatewayHighErrorRate
      expr: |
        sum(rate(http_requests_total{service="api-gateway", status_code=~"5.."}[5m]))
        /
        sum(rate(http_requests_total{service="api-gateway"}[5m])) > 0.05
      for: 2m
      labels:
        severity: critical
        service: api-gateway
      annotations:
        summary: "API Gateway error rate above 5%"
        description: "Error rate is {{ $value | humanizePercentage }}."
        runbook_url: "https://wiki.deployforge.dev/runbooks/api-gateway-errors"
```

7. **Apply the alert rule and verify it's loaded in Prometheus:**

```bash
kubectl apply -f error-rate-alert.yaml

curl -s http://localhost:9090/api/v1/rules?type=alert | \
  jq '.data.groups[] | select(.name=="deployforge.alerts") | .rules[].name'
```

8. **Verify the alert appears in Grafana:**

Navigate to Alerting → Alert Rules in Grafana. You should see the `APIGatewayHighErrorRate` rule.

### Verification

```bash
# Grafana should be accessible
curl -s -o /dev/null -w '%{http_code}' http://localhost:3000/api/health
# → 200

# Loki datasource should be configured
curl -s -u admin:deployforge-admin http://localhost:3000/api/datasources | \
  jq '.[].name'
# → Should include "Loki"

# Alert rule should be loaded in Prometheus
curl -s http://localhost:9090/api/v1/rules?type=alert | \
  jq '.data.groups[] | select(.name=="deployforge.alerts") | .rules[] | {name: .name, state: .state}'
# → { "name": "APIGatewayHighErrorRate", "state": "inactive" }

# Alertmanager should be receiving
curl -s http://localhost:9093/api/v2/status | jq '.cluster.status'
# → "ready"
```

<details>
<summary>Show solution</summary>

```bash
#!/bin/bash
set -euo pipefail

NS="deployforge"

echo "=== Step 1: Access Grafana ==="
kubectl port-forward -n monitoring svc/prometheus-grafana 3000:80 &
sleep 3
echo "Grafana: http://localhost:3000 (admin / deployforge-admin)"

echo "=== Step 2: Add Loki datasource via API ==="
curl -s -X POST \
  -H "Content-Type: application/json" \
  -u admin:deployforge-admin \
  http://localhost:3000/api/datasources \
  -d '{
    "name": "Loki",
    "type": "loki",
    "url": "http://loki.deployforge:3100",
    "access": "proxy",
    "jsonData": {
      "derivedFields": [{
        "name": "TraceID",
        "datasourceUid": "",
        "matcherRegex": "\"trace_id\":\"([a-f0-9]+)\"",
        "url": ""
      }]
    }
  }' | jq '.message'

echo "=== Step 3: Add Jaeger datasource via API ==="
curl -s -X POST \
  -H "Content-Type: application/json" \
  -u admin:deployforge-admin \
  http://localhost:3000/api/datasources \
  -d '{
    "name": "Jaeger",
    "type": "jaeger",
    "url": "http://jaeger-query.deployforge:16686",
    "access": "proxy"
  }' | jq '.message'

echo "=== Step 4: Import RED dashboard ==="
cat <<'DASHBOARD_EOF' > dashboard-import.json
{
  "dashboard": {
    "title": "DeployForge — API Gateway (RED)",
    "uid": "deployforge-api-gw-red",
    "tags": ["deployforge", "api-gateway", "red"],
    "time": { "from": "now-1h", "to": "now" },
    "panels": [
      {
        "title": "Request Rate",
        "type": "stat",
        "gridPos": { "h": 4, "w": 6, "x": 0, "y": 0 },
        "targets": [{
          "expr": "sum(rate(http_requests_total{service=\"api-gateway\"}[5m]))",
          "legendFormat": "req/s"
        }],
        "fieldConfig": { "defaults": { "unit": "reqps" } }
      },
      {
        "title": "Error %",
        "type": "stat",
        "gridPos": { "h": 4, "w": 6, "x": 6, "y": 0 },
        "targets": [{
          "expr": "100 * sum(rate(http_requests_total{service=\"api-gateway\",status_code=~\"5..\"}[5m])) / sum(rate(http_requests_total{service=\"api-gateway\"}[5m]))",
          "legendFormat": "error %"
        }],
        "fieldConfig": {
          "defaults": {
            "unit": "percent",
            "thresholds": { "steps": [
              { "color": "green", "value": null },
              { "color": "yellow", "value": 1 },
              { "color": "red", "value": 5 }
            ]}
          }
        }
      },
      {
        "title": "p99 Latency",
        "type": "stat",
        "gridPos": { "h": 4, "w": 6, "x": 12, "y": 0 },
        "targets": [{
          "expr": "histogram_quantile(0.99, sum by (le) (rate(http_request_duration_seconds_bucket{service=\"api-gateway\"}[5m])))",
          "legendFormat": "p99"
        }],
        "fieldConfig": { "defaults": { "unit": "s", "thresholds": { "steps": [
          { "color": "green", "value": null },
          { "color": "yellow", "value": 0.5 },
          { "color": "red", "value": 1.0 }
        ]}}}
      },
      {
        "title": "Requests by Status Class",
        "type": "timeseries",
        "gridPos": { "h": 8, "w": 12, "x": 0, "y": 4 },
        "targets": [{
          "expr": "sum by (status_class) (rate(http_requests_total{service=\"api-gateway\"}[5m]))",
          "legendFormat": "{{ status_class }}"
        }],
        "fieldConfig": { "defaults": { "unit": "reqps" } }
      },
      {
        "title": "Latency Percentiles",
        "type": "timeseries",
        "gridPos": { "h": 8, "w": 12, "x": 12, "y": 4 },
        "targets": [
          { "expr": "histogram_quantile(0.50, sum by (le) (rate(http_request_duration_seconds_bucket{service=\"api-gateway\"}[5m])))", "legendFormat": "p50" },
          { "expr": "histogram_quantile(0.95, sum by (le) (rate(http_request_duration_seconds_bucket{service=\"api-gateway\"}[5m])))", "legendFormat": "p95" },
          { "expr": "histogram_quantile(0.99, sum by (le) (rate(http_request_duration_seconds_bucket{service=\"api-gateway\"}[5m])))", "legendFormat": "p99" }
        ],
        "fieldConfig": { "defaults": { "unit": "s" } }
      }
    ]
  },
  "overwrite": true
}
DASHBOARD_EOF

curl -s -X POST \
  -H "Content-Type: application/json" \
  -u admin:deployforge-admin \
  http://localhost:3000/api/dashboards/db \
  -d @dashboard-import.json | jq '{status, uid: .uid, url: .url}'

rm dashboard-import.json

echo "=== Step 5: Create alert rule ==="
cat <<'EOF' | kubectl apply -f -
apiVersion: monitoring.coreos.com/v1
kind: PrometheusRule
metadata:
  name: deployforge-error-rate-alert
  namespace: deployforge
  labels:
    release: prometheus
spec:
  groups:
  - name: deployforge.alerts
    rules:
    - alert: APIGatewayHighErrorRate
      expr: |
        sum(rate(http_requests_total{service="api-gateway", status_code=~"5.."}[5m]))
        /
        sum(rate(http_requests_total{service="api-gateway"}[5m])) > 0.05
      for: 2m
      labels:
        severity: critical
        service: api-gateway
      annotations:
        summary: "API Gateway error rate above 5%"
        description: "Error rate is {{ $value | humanizePercentage }}."
        runbook_url: "https://wiki.deployforge.dev/runbooks/api-gateway-errors"
EOF

echo "=== Step 6: Verify ==="
kubectl port-forward -n monitoring svc/prometheus-kube-prometheus-prometheus 9090:9090 &
sleep 3

echo ""
echo "Alert rules loaded:"
curl -s http://localhost:9090/api/v1/rules?type=alert | \
  jq '.data.groups[] | select(.name=="deployforge.alerts") | .rules[] | {name: .name, state: .state}'

echo ""
echo "Datasources in Grafana:"
curl -s -u admin:deployforge-admin http://localhost:3000/api/datasources | jq '.[].name'

echo ""
echo "=== Done! ==="
echo "Dashboard: http://localhost:3000/d/deployforge-api-gw-red"
echo "Prometheus: http://localhost:9090"
echo "Jaeger: kubectl port-forward -n deployforge svc/jaeger-query 16686:16686"
echo "         http://localhost:16686"
```

</details>
