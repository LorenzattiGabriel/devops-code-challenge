# Kubernetes Manifests Setup Guide

Complete guide to deploy the ticketing microservice to Kubernetes.

---

## 📁 Manifest Files Overview

### 1️⃣ **namespace.yaml**
**Purpose**: Isolates resources in a dedicated namespace

```yaml
apiVersion: v1
kind: Namespace
metadata:
  name: ticketing
```

**What it does:**
- Creates a logical separation for all ticketing application resources
- Enables better resource management and access control
- Allows multiple environments (dev, staging, prod) on same cluster

**Deploy:**
```bash
kubectl apply -f namespace.yaml
```

---

### 2️⃣ **configmap.yaml**
**Purpose**: Stores non-sensitive configuration data

**What it contains:**
- `application.properties` - Spring Boot configuration
- Database connection settings (host, port, database name)
- Redis configuration
- JPA/Hibernate settings
- Logging levels
- Cache configuration

**What it does:**
- Externalizes configuration from application code
- Makes configuration changes without rebuilding images
- Easy to update and rollback configurations

**Deploy:**
```bash
kubectl apply -f configmap.yaml
```

**Usage in Pod:**
```yaml
volumeMounts:
  - name: config
    mountPath: /app/config/application.properties
    subPath: application.properties
```

---

### 3️⃣ **secret.yaml**
**Purpose**: Stores sensitive data (passwords, tokens)

**What it contains:**
- MySQL root password (base64 encoded)
- MySQL user password (base64 encoded)
- Redis password (base64 encoded)

**What it does:**
- Securely stores credentials
- Encrypted at rest in etcd
- Can be used as environment variables or files
- Not displayed in plain text in kubectl output

**Deploy:**
```bash
kubectl apply -f secret.yaml
```

**Usage in Pod:**
```yaml
env:
  - name: MYSQL_PASSWORD
    valueFrom:
      secretKeyRef:
        name: ticketing-secrets
        key: mysql-password
```

**⚠️ Security Best Practice:**
- Use external secret management (AWS Secrets Manager, HashiCorp Vault)
- Rotate secrets regularly
- Never commit secrets to Git (use sealed-secrets or SOPS)

---

### 4️⃣ **deployment.yaml**
**Purpose**: Defines how to deploy and run the application

**What it contains:**
- **Replicas**: 3 pods for high availability
- **Update Strategy**: Rolling update (zero downtime)
- **Resource Limits**: CPU and memory constraints
- **Health Checks**: Liveness and readiness probes
- **Security Context**: Non-root user, read-only filesystem
- **Environment Variables**: From ConfigMap and Secrets
- **Init Containers**: Database migration (optional)

**What it does:**
- Ensures desired number of pods are always running
- Handles pod failures and restarts
- Performs rolling updates without downtime
- Monitors application health
- Enforces resource limits

**Deploy:**
```bash
kubectl apply -f deployment.yaml
```

**Key Features:**
```yaml
spec:
  replicas: 3                    # High availability
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxSurge: 1                # Max 4 pods during update
      maxUnavailable: 0          # Always 3+ pods running
  
  resources:
    requests:
      memory: "512Mi"            # Guaranteed resources
      cpu: "500m"
    limits:
      memory: "1Gi"              # Maximum resources
      cpu: "1000m"
  
  livenessProbe:
    httpGet:
      path: /actuator/health     # Health check endpoint
      port: 8080
    initialDelaySeconds: 60
    periodSeconds: 10
  
  readinessProbe:
    httpGet:
      path: /actuator/health/readiness
      port: 8080
    initialDelaySeconds: 30
    periodSeconds: 5
```

---

### 5️⃣ **service.yaml**
**Purpose**: Exposes the application within the cluster

**What it contains:**
- **Service Type**: ClusterIP (internal only)
- **Port Mapping**: 80 → 8080
- **Selector**: Routes traffic to pods with `app: ticketing-service`

**What it does:**
- Provides stable internal DNS name: `ticketing-service.ticketing.svc.cluster.local`
- Load balances traffic across all healthy pods
- Service discovery for other applications

**Deploy:**
```bash
kubectl apply -f service.yaml
```

**Access from another pod:**
```bash
curl http://ticketing-service.ticketing.svc.cluster.local/api/v1/events
```

---

### 6️⃣ **ingress.yaml**
**Purpose**: Exposes the application to external traffic (Internet)

**What it contains:**
- **Host**: `ticketing.example.com` (customize for your domain)
- **Path Routing**: `/` routes to ticketing service
- **TLS/SSL**: HTTPS configuration with Let's Encrypt
- **Annotations**: Nginx-specific configurations

**What it does:**
- Acts as reverse proxy and load balancer
- Terminates SSL/TLS
- Routes external traffic to internal services
- Provides single entry point for multiple services

**Deploy:**
```bash
kubectl apply -f ingress.yaml
```

**Prerequisites:**
- Ingress Controller installed (e.g., Nginx Ingress Controller)
- DNS configured to point to Ingress IP
- TLS certificate (Let's Encrypt with cert-manager)

**Access:**
```bash
curl https://ticketing.example.com/api/v1/events
```

---

### 7️⃣ **hpa.yaml** (Horizontal Pod Autoscaler)
**Purpose**: Automatically scales pods based on load

**What it contains:**
- **Min Replicas**: 3 (minimum pods always running)
- **Max Replicas**: 10 (maximum during high load)
- **Target CPU**: 70% utilization
- **Target Memory**: 80% utilization

**What it does:**
- Monitors CPU and memory usage
- Scales out (adds pods) when usage is high
- Scales in (removes pods) when usage is low
- Handles traffic spikes automatically

**Deploy:**
```bash
kubectl apply -f hpa.yaml
```

**Requirements:**
- Metrics Server installed in cluster

**Monitor:**
```bash
kubectl get hpa -n ticketing
kubectl describe hpa ticketing-hpa -n ticketing
```

**Example Scaling:**
```
Normal load:  3 pods (33% CPU each)
High load:    7 pods (70% CPU each) ← Scales out
Low load:     3 pods (20% CPU each) ← Scales in
```

---

### 8️⃣ **pdb.yaml** (Pod Disruption Budget)
**Purpose**: Ensures availability during maintenance/updates

**What it contains:**
- **Min Available**: 2 pods must always be running
- **Selector**: Applies to `app: ticketing-service`

**What it does:**
- Prevents all pods from being evicted simultaneously
- Ensures service availability during:
  - Node maintenance
  - Cluster upgrades
  - Manual evictions
- Works with HPA and Deployments

**Deploy:**
```bash
kubectl apply -f pdb.yaml
```

**Example:**
```
Scenario: Node drain (maintenance)
- Total pods: 3
- PDB: min 2 available
- Kubernetes drains 1 pod at a time
- Ensures 2+ pods always running
```

---

### 9️⃣ **servicemonitor.yaml**
**Purpose**: Configures Prometheus to scrape metrics

**What it contains:**
- **Endpoints**: `/actuator/prometheus` on port 8080
- **Interval**: Scrape every 30 seconds
- **Selector**: Monitors `app: ticketing-service`

**What it does:**
- Enables observability with Prometheus
- Collects application metrics:
  - HTTP request rates
  - Response times
  - JVM metrics (heap, GC)
  - Database connection pool
  - Cache hit rates
- Feeds data to Grafana dashboards

**Deploy:**
```bash
kubectl apply -f servicemonitor.yaml
```

**Requirements:**
- Prometheus Operator installed
- Spring Boot Actuator with Micrometer

**View Metrics:**
```bash
# Port-forward to Prometheus
kubectl port-forward -n monitoring svc/prometheus-k8s 9090:9090

# Open browser: http://localhost:9090
# Query: http_server_requests_seconds_count{namespace="ticketing"}
```

---

### 🔟 **networkpolicy.yaml**
**Purpose**: Controls network traffic between pods (firewall rules)

**What it contains:**
- **Ingress Rules**: What can connect TO the app
  - Allow from Ingress Controller
  - Allow from same namespace
  - Block everything else
- **Egress Rules**: What the app can connect TO
  - Allow to MySQL (port 3306)
  - Allow to Redis (port 6379)
  - Allow to Kubernetes DNS (port 53)

**What it does:**
- Implements zero-trust networking
- Prevents lateral movement in case of compromise
- Restricts unnecessary communication
- Enhances security posture

**Deploy:**
```bash
kubectl apply -f networkpolicy.yaml
```

**Requirements:**
- Network plugin that supports NetworkPolicy (Calico, Cilium, Weave)

**Security Benefits:**
```
✅ App can only talk to MySQL and Redis
✅ Only Ingress can talk to App
✅ Other namespaces are blocked
❌ Compromised pod can't attack other services
```

---

### 1️⃣1️⃣ **kustomization.yaml**
**Purpose**: Manages and customizes Kubernetes manifests

**What it contains:**
- **Resources**: List of all manifest files
- **Namespace**: Default namespace for all resources
- **Common Labels**: Applied to all resources
- **Name Prefix/Suffix**: For multi-environment deployments

**What it does:**
- Single command deployment of all resources
- Environment-specific customizations (dev, prod)
- Avoids duplicate YAML files
- Base + overlays pattern

**Deploy:**
```bash
kubectl apply -k .
# OR
kustomize build . | kubectl apply -f -
```

**Benefits:**
- Deploy all manifests at once
- Maintain different environments easily
- DRY principle (Don't Repeat Yourself)

**Example Structure:**
```
k8s/
├── base/
│   ├── kustomization.yaml
│   ├── deployment.yaml
│   └── service.yaml
├── overlays/
│   ├── dev/
│   │   └── kustomization.yaml (replicas: 1, small resources)
│   └── prod/
│       └── kustomization.yaml (replicas: 3, large resources)
```

---

## 🚀 Deployment Order

### Step 1: Prerequisites
```bash
# Create namespace first
kubectl apply -f namespace.yaml

# Verify namespace
kubectl get namespace ticketing
```

### Step 2: Configuration & Secrets
```bash
# Deploy secrets (passwords)
kubectl apply -f secret.yaml

# Deploy configuration
kubectl apply -f configmap.yaml

# Verify
kubectl get secrets -n ticketing
kubectl get configmaps -n ticketing
```

### Step 3: Application
```bash
# Deploy application
kubectl apply -f deployment.yaml

# Deploy service (internal access)
kubectl apply -f service.yaml

# Verify pods are running
kubectl get pods -n ticketing
kubectl logs -f <pod-name> -n ticketing
```

### Step 4: External Access
```bash
# Deploy ingress (external access)
kubectl apply -f ingress.yaml

# Verify ingress
kubectl get ingress -n ticketing
```

### Step 5: Autoscaling & High Availability
```bash
# Deploy HPA (autoscaling)
kubectl apply -f hpa.yaml

# Deploy PDB (disruption budget)
kubectl apply -f pdb.yaml

# Verify
kubectl get hpa -n ticketing
kubectl get pdb -n ticketing
```

### Step 6: Monitoring & Security
```bash
# Deploy service monitor (Prometheus)
kubectl apply -f servicemonitor.yaml

# Deploy network policy (firewall)
kubectl apply -f networkpolicy.yaml

# Verify
kubectl get servicemonitor -n ticketing
kubectl get networkpolicy -n ticketing
```

### Step 7: All at Once (using Kustomize)
```bash
# Deploy everything
kubectl apply -k .

# Verify all resources
kubectl get all -n ticketing
```

---

## 🔍 Verification Commands

```bash
# Check all resources
kubectl get all -n ticketing

# Check pod logs
kubectl logs -f deployment/ticketing-app -n ticketing

# Check pod status
kubectl describe pod <pod-name> -n ticketing

# Check service endpoints
kubectl get endpoints -n ticketing

# Check HPA status
kubectl get hpa -n ticketing

# Check events
kubectl get events -n ticketing --sort-by='.lastTimestamp'

# Port-forward for local testing
kubectl port-forward -n ticketing svc/ticketing-service 8080:80

# Test application
curl http://localhost:8080/actuator/health
curl http://localhost:8080/api/v1/events
```

---

## 📊 Monitoring & Observability

### Application Metrics
```bash
# View Prometheus metrics
kubectl port-forward -n ticketing svc/ticketing-service 8080:80
curl http://localhost:8080/actuator/prometheus
```

### Resource Usage
```bash
# Pod resource usage
kubectl top pods -n ticketing

# Node resource usage
kubectl top nodes
```

### Logs
```bash
# Stream logs
kubectl logs -f deployment/ticketing-app -n ticketing

# Last 100 lines
kubectl logs --tail=100 deployment/ticketing-app -n ticketing

# All pods
kubectl logs -l app=ticketing-service -n ticketing
```

---

## 🔄 Updates & Rollbacks

### Rolling Update
```bash
# Update image
kubectl set image deployment/ticketing-app \
  ticketing=your-registry/ticketing:v2 \
  -n ticketing

# Check rollout status
kubectl rollout status deployment/ticketing-app -n ticketing

# Check rollout history
kubectl rollout history deployment/ticketing-app -n ticketing
```

### Rollback
```bash
# Rollback to previous version
kubectl rollout undo deployment/ticketing-app -n ticketing

# Rollback to specific revision
kubectl rollout undo deployment/ticketing-app --to-revision=2 -n ticketing
```

---

## 🧹 Cleanup

```bash
# Delete all resources
kubectl delete -k .

# OR delete namespace (removes everything)
kubectl delete namespace ticketing
```

---

## 🔐 Security Best Practices

1. **Secrets Management**
   - Use external secret managers (AWS Secrets Manager, Vault)
   - Rotate secrets regularly
   - Never commit secrets to Git

2. **RBAC** (Role-Based Access Control)
   - Limit permissions to minimum required
   - Create service accounts for apps
   - Use namespaces for isolation

3. **Network Policies**
   - Enable network policies
   - Default deny all traffic
   - Explicitly allow required connections

4. **Container Security**
   - Use non-root users
   - Read-only filesystems
   - No privileged containers
   - Scan images for vulnerabilities

5. **Resource Limits**
   - Always set resource requests and limits
   - Prevents resource exhaustion
   - Enables proper scheduling

---

## 📚 Additional Resources

- [Kubernetes Documentation](https://kubernetes.io/docs/)
- [Spring Boot on Kubernetes](https://spring.io/guides/gs/spring-boot-kubernetes/)
- [Kustomize Documentation](https://kustomize.io/)
- [Prometheus Operator](https://prometheus-operator.dev/)
- [Nginx Ingress Controller](https://kubernetes.github.io/ingress-nginx/)

---

## 🆘 Troubleshooting

### Pods not starting
```bash
kubectl describe pod <pod-name> -n ticketing
kubectl logs <pod-name> -n ticketing
```

### Service not accessible
```bash
kubectl get svc -n ticketing
kubectl get endpoints -n ticketing
kubectl describe svc ticketing-service -n ticketing
```

### HPA not scaling
```bash
kubectl describe hpa ticketing-hpa -n ticketing
kubectl top pods -n ticketing
# Ensure Metrics Server is installed
kubectl get deployment metrics-server -n kube-system
```

### Network Policy blocking traffic
```bash
kubectl describe networkpolicy -n ticketing
# Temporarily disable to test
kubectl delete networkpolicy ticketing-netpol -n ticketing
```

---

**Ready for production deployment! 🚀**

