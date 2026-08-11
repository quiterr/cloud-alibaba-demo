# Jenkins

## 安装Jenkins

```shell
# 添加仓库
helm repo add jenkins https://charts.jenkins.io
# 更新仓库索引
helm repo update jenkins
# 创建命名空间
kubectl create ns jenkins
# 完整安装命令
helm upgrade --install jenkins jenkins/jenkins \
-n jenkins \
--set controller.service.type=NodePort \
--set controller.service.nodePort=30080 \
--set controller.persistence.storageClass=nfs-dynamic-sc \
--set controller.persistence.size=15Gi \
--set controller.resources.requests.cpu=500m \
--set controller.resources.requests.memory=512Mi \
--set controller.resources.limits.cpu=1 \
--set controller.resources.limits.memory=1Gi \
--set prometheus.enabled=false \
--set grafana.enabled=false
# 查看pod状态
kubectl get pods -n jenkins -w
```