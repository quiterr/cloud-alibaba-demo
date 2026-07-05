# ingress

## 概述

ingress是对nginx的抽象，nginx是ingress的一种实现。

访问顺序：用户->负载均衡器（如果有）->ingress->service->pod
![ingress.png](../../img/ingress.png)

## 先安装helm

```shell
# 一键安装
curl https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 | bash
# 查看版本
helm version
# 安装完成后配置国内 Chart 源（解决国外仓库拉取慢）
helm repo add stable https://kubernetes.oss-cn-hangzhou.aliyuncs.com/charts
helm repo update
```

## 安装ingress-nginx

### 一、步骤 1：Helm 安装 ingress-nginx 控制器

1.1 添加 ingress-nginx 官方仓库
```shell
# 添加仓库
helm repo add ingress-nginx https://kubernetes.github.io/ingress-nginx
# 更新仓库索引
helm repo update
# 拉取chart包（可选离线）
helm pull ingress-nginx/ingress-nginx
```

1.2 部署 ingress-nginx（独立命名空间隔离）
```shell
# 创建专属命名空间
kubectl create ns ingress-nginx

# helm 安装，暴露NodePort方便测试访问
helm install ingress-nginx ingress-nginx/ingress-nginx \
--namespace ingress-nginx \
--set controller.service.type=NodePort \
--set controller.service.nodePorts.http=30081 \
--set controller.service.nodePorts.https=30443

# 执行的时候报了错
Error: INSTALLATION FAILED: Get "https://release-assets.githubusercontent.com/github-production-release-asset/72891330/94734905-b074-485a-a0fb-d4f959052c8d?sp=r&sv=2018-11-09&sr=b&spr=https&se=2026-07-05T09%3A47%3A25Z&rscd=attachment%3B+filename%3Dingress-nginx-4.15.1.tgz&rsct=application%2Foctet-stream&skoid=96c2d410-5711-43a1-aedd-ab1947aa7ab0&sktid=398a6654-997b-47e9-b12b-9515b896b4de&skt=2026-07-05T08%3A46%3A28Z&ske=2026-07-05T09%3A47%3A25Z&sks=b&skv=2018-11-09&sig=7AcHKFUIRH72FeDiogzChlsuuiKSvIFT8CLSyQhyrI0%3D&jwt=eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJnaXRodWIuY29tIiwiYXVkIjoicmVsZWFzZS1hc3NldHMuZ2l0aHVidXNlcmNvbnRlbnQuY29tIiwia2V5Ijoia2V5MSIsImV4cCI6MTc4MzI0MjQzMSwibmJmIjoxNzgzMjQyMTMxLCJwYXRoIjoicmVsZWFzZWFzc2V0cHJvZHVjdGlvbi5ibG9iLmNvcmUud2luZG93cy5uZXQifQ.6bUtPcoXX6TCBoMas3xXx3YGiMH7iUIxZsmCYJLdSQ0&response-content-disposition=attachment%3B%20filename%3Dingress-nginx-4.15.1.tgz&response-content-type=application%2Foctet-stream": dial tcp 185.199.109.133:443: connect: connection refused

# 手动在宿主机上用chrome下载成功了ingress-nginx-4.15.1.tgz，再次安装
helm install ingress-nginx ./ingress-nginx-4.15.1.tgz \
--namespace ingress-nginx \
--set controller.service.type=NodePort \
--set controller.service.nodePorts.http=30081 \
--set controller.service.nodePorts.https=30444 \
--set controller.image.repository=registry.cn-hangzhou.aliyuncs.com/google_containers/nginx-ingress-controller \
--set controller.image.digest="" \
--set controller.admissionWebhooks.image.repository=registry.cn-hangzhou.aliyuncs.com/google_containers/kube-webhook-certgen \
--set controller.admissionWebhooks.image.digest=""
```

1.3 验证安装
```shell
# 查看pod，等待ready
kubectl get pods -n ingress-nginx -w
# 查看svc，确认30080/30443端口
kubectl get svc -n ingress-nginx
# 查看ingress控制器版本
kubectl exec -n ingress-nginx deploy/ingress-nginx-controller -- /nginx-ingress-controller --version
```

### 二、步骤 2：准备两套后端测试服务（两个业务 web）

创建两个 Deployment+Service，用于 Ingress 转发测试
test-web.yaml
```yaml
# 服务1：前台网站
apiVersion: apps/v1
kind: Deployment
metadata:
  name: web-front
spec:
  replicas: 2
  selector:
    matchLabels:
      app: web-front
  template:
    metadata:
      labels:
        app: web-front
    spec:
      containers:
      - name: nginx
        image: nginx:alpine
        ports:
        - containerPort: 80
        command: ["/bin/sh","-c"]
        args:
        - echo "====前台页面 front.test.com====" > /usr/share/nginx/html/index.html && nginx -g "daemon off;"
---
apiVersion: v1
kind: Service
metadata:
  name: svc-front
spec:
  selector:
    app: web-front
  ports:
  - port: 80
    targetPort: 80
  type: ClusterIP

# 服务2：后台管理系统
apiVersion: apps/v1
kind: Deployment
metadata:
  name: web-admin
spec:
  replicas: 2
  selector:
    matchLabels:
      app: web-admin
  template:
    metadata:
      labels:
        app: web-admin
    spec:
      containers:
      - name: nginx
        image: nginx:alpine
        ports:
        - containerPort: 80
        command: ["/bin/sh","-c"]
        args:
        - echo "====后台管理 admin.test.com====" > /usr/share/nginx/html/index.html && nginx -g "daemon off;"
---
apiVersion: v1
kind: Service
metadata:
  name: svc-admin
spec:
  selector:
    app: web-admin
  ports:
  - port: 80
    targetPort: 80
  type: ClusterIP
```

创建测试web服务
```shell
kubectl apply -f test-web.yaml
# 验证pod与svc
kubectl get deploy,svc
```

### 三、实操 1：虚拟主机（Host 域名匹配）—— 多域名分流

**原理**：Ingress 中 host 字段实现虚拟主机，相同 Ingress 资源下，不同域名路由到不同 Service，对应 Nginx server_name。

创建虚拟主机ingress-host.yaml
```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: ingress-vhost-demo
  # 指定ingress控制器注解，必须加
  annotations:
    kubernetes.io/ingress.class: "nginx"
spec:
  rules:
  # 第一个虚拟主机：前台域名 front.test.com
  - host: front.test.com
    http:
      paths:
      - path: /
        pathType: Prefix # 前缀匹配
        backend:
          service:
            name: svc-front
            port:
              number: 80
  # 第二个虚拟主机：后台域名 admin.test.com
  - host: admin.test.com
    http:
      paths:
      - path: /
        pathType: Prefix
        backend:
          service:
            name: svc-admin
            port:
              number: 80
```

```shell
# 部署ingress
kubectl apply -f ingress-host.yaml

# 查看ingress规则
kubectl get ingress
kubectl describe ingress ingress-vhost-demo
```

测试访问
```shell
kubectl run test-box --image=busybox --rm -it -- sh
# 前台域名
wget -q -O- http://front.test.com
# 后台域名
wget -q -O- http://admin.test.com
```

### 四、实操 2：路径匹配规则（核心 3 种 pathType 详解）

pathType 三选一：
* Prefix：前缀匹配（最常用）
* Exact：精准完全匹配
* ImplementationSpecific：兼容 nginx 正则匹配

**4.1 Prefix 前缀匹配（示例 /api 转发到后端接口服务）**

新增一个接口服务
```yaml
cat > web-api.yaml <<EOF
apiVersion: apps/v1
kind: Deployment
metadata:
  name: web-api
spec:
  replicas: 1
  selector:
    matchLabels:
      app: web-api
  template:
    metadata:
      labels:
        app: web-api
    spec:
      containers:
      - name: nginx
        image: nginx:alpine
        ports:
        - containerPort: 80
        command: ["/bin/sh","-c"]
        args:
        - echo "====API接口 /api/* ====" > /usr/share/nginx/html/index.html && nginx -g "daemon off;"
---
apiVersion: v1
kind: Service
metadata:
  name: svc-api
spec:
  selector:
    app: web-api
  ports:
  - port: 80
    targetPort: 80
EOF
kubectl apply -f web-api.yaml
```

编写前缀匹配 Ingress，同一域名下区分页面 / 接口
```shell
cat > ingress-path-prefix.yaml <<EOF
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: ingress-path-prefix
  annotations:
    kubernetes.io/ingress.class: "nginx"
spec:
  rules:
  - host: api.test.com
    http:
      paths:
      # 所有 /api 开头路径转发到接口服务
      - path: /api
        pathType: Prefix
        backend:
          service:
            name: svc-api
            port:
              number: 80
      # 根路径转发前台页面
      - path: /
        pathType: Prefix
        backend:
          service:
            name: svc-front
            port:
              number: 80
EOF
kubectl apply -f ingress-path-prefix.yaml
```

测试：
* api.test.com/api/list → svc-api
* api.test.com/home → svc-front

**4.2 Exact 精准匹配（仅完全相等路径才命中）**
```shell
cat > ingress-path-exact.yaml <<EOF
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: ingress-path-exact
  annotations:
    kubernetes.io/ingress.class: "nginx"
spec:
  rules:
  - host: exact.test.com
    http:
      paths:
      # 仅访问 /login 才命中，/login/ /login1 都不匹配
      - path: /login
        pathType: Exact
        backend:
          service:
            name: svc-admin
            port:
              number: 80
      - path: /
        pathType: Prefix
        backend:
          service:
            name: svc-front
            port:
              number: 80
EOF
kubectl apply -f ingress-path-exact.yaml
```

测试现象：
* exact.test.com/login 匹配 admin 后台
* exact.test.com/login/ 404，不会命中

**4.3 路径重写（核心注解 nginx.ingress.kubernetes.io/rewrite-target）**

业务场景：Ingress 路径 /api，后端服务只识别根路径 /，必须剥离前缀
```shell
cat > ingress-rewrite.yaml <<EOF
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: ingress-rewrite
  annotations:
    kubernetes.io/ingress.class: "nginx"
    # 路径重写规则：匹配/api/(.*) 转发到 /$1，剥离/api前缀
    nginx.ingress.kubernetes.io/rewrite-target: /$1
spec:
  rules:
  - host: rewrite.test.com
    http:
      paths:
      - path: /api/(.*)
        pathType: Prefix
        backend:
          service:
            name: svc-api
            port:
              number: 80
EOF
kubectl apply -f ingress-rewrite.yaml
```

访问 rewrite.test.com/api/user，后端实际收到请求 /user

### 五、实操 3：多规则优先级、正则匹配、全局 404 兜底

**5.1 路由优先级规则**
* Exact 精准匹配 > Prefix 前缀匹配
* 同类型前缀：路径越长优先级越高（/api/user > /api）
* 不同 host 域名互相隔离，互不干扰

**5.2 正则路径匹配（ImplementationSpecific）**
```shell
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: ingress-regex
  annotations:
    kubernetes.io/ingress.class: "nginx"
spec:
  rules:
  - host: regex.test.com
    http:
      paths:
      # 正则匹配 /user/数字
      - path: /user/[0-9]+
        pathType: ImplementationSpecific
        backend:
          service:
            name: svc-api
            port:
              number: 80
```

### 六、全套排查 & 管理命令
```shell
# 1. 查看所有ingress资源
kubectl get ingress
# 2. 查看ingress详细规则、事件、后端服务
kubectl describe ingress ingress-vhost-demo
# 3. 导出ingress yaml备份
kubectl get ingress ingress-vhost-demo -o yaml > ingress-backup.yaml
# 4. 实时查看ingress控制器日志（排查404/502转发失败）
kubectl logs -f -n ingress-nginx deploy/ingress-nginx-controller
# 5. 删除ingress规则
kubectl delete -f ingress-host.yaml
# 6. 卸载ingress-nginx
helm uninstall ingress-nginx -n ingress-nginx
kubectl delete ns ingress-nginx
```

