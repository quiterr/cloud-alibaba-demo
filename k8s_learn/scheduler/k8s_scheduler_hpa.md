# k8s 水平自动扩缩容

## 一、HPA 核心概念
1. 全称
   
*  Horizontal Pod Autoscaler，Pod 水平自动扩缩容控制器
*  Horizontal：水平扩容 —— 增加 / 减少 Pod 副本数量（横向）
*  区别 VPA（Vertical Pod Autoscaler）：垂直扩容，修改单个 Pod 的 CPU / 内存资源配额

2. 核心作用

*    根据监控指标自动调整 Deployment/StatefulSet 副本数：
*    流量高峰 → 自动增加 Pod，扛住压力
*    业务低峰 → 自动减少 Pod，节省服务器资源
*    无需人工手动 kubectl patch 改 replicas

4. 工作依赖组件（必备）
  
*   Metrics Server：集群监控指标采集核心，HPA 必须依赖它获取 Pod CPU / 内存使用率；
*   无 Metrics Server 时 HPA 无法读取指标，永远不会自动扩缩容。

5. 支持扩缩的控制器

* ✅ 支持：Deployment、ReplicaSet、StatefulSet
* ❌ 不支持：DaemonSet（每个节点固定 1 个 Pod，不能扩缩）、Job/CronJob

6. HPA 两类监控指标
   
**内置核心指标（无需额外部署组件）**
*   cpu：CPU 使用率百分比
*   memory：内存使用率百分比

**自定义外部指标（业务指标，需 Prometheus Adapter）**
*   QPS、请求延迟、队列长度、订单量等业务指标

7. 扩缩容冷却窗口（防抖动）
   
**默认配置：**
*    扩容冷却：3 分钟（连续触发扩容，3 分钟内不会再次扩容，避免瞬间爆大量 Pod）
*    缩容冷却：5 分钟（防止流量短暂波动反复删 Pod）
   
可通过 behavior 自定义快慢扩缩规则。

## 二、前置实操：部署 Metrics Server（必须先装）
1. 部署 Metrics Server 官方清单
```shell
kubectl apply -f https://github.com/kubernetes-sigs/metrics-server/releases/latest/download/components.yaml
```

如果镜像拉取失败，编辑清单，把镜像替换为阿里云：
`registry.aliyuncs.com/google_containers/metrics-server:v0.7.1`

同时给 metrics-server pod 增加启动参数，跳过 tls 节点证书校验：
```yaml
args:
- --kubelet-insecure-tls
```

2. 验证 Metrics Server 正常运行
```shell
# 查看pod就绪
kubectl get pod -n kube-system | grep metrics-server

# 查看节点资源指标（能输出代表采集正常）
kubectl top node

# 查看pod资源指标
kubectl top pod
```

输出 CPU、内存数值代表环境就绪，可以使用 HPA。

## 三、实操 1：基础 HPA（CPU + 内存双指标，Deployment 演示）

**步骤 1：部署压测用无状态服务（php-apache，自带压测负载）**

php-deploy.yaml
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: php-apache
spec:
  replicas: 1
  selector:
    matchLabels:
      app: php-apache
  template:
    metadata:
      labels:
        app: php-apache
    spec:
      containers:
      - name: php-apache
        image: registry.k8s.io/hpa-example
        resources:
          # 关键：必须配置requests，HPA计算使用率基于requests
          requests:
            cpu: 100m       # 0.1核CPU，10% CPU
            memory: 128Mi
        ports:
        - containerPort: 80
---
# 暴露Service方便压测
apiVersion: v1
kind: Service
metadata:
  name: php-svc
spec:
  selector:
    app: php-apache
  ports:
  - port: 80
    targetPort: 80
  type: ClusterIP
```

部署：
```shell
kubectl apply -f php-deploy.yaml
kubectl get deploy,pod,svc
```

**步骤 2：创建 HPA 资源（命令行快速创建，无需写 yaml）**

需求：
* 最小副本：1，最大副本：10
* CPU 目标使用率 70%，内存目标使用率 70%

```shell
kubectl autoscale deployment php-apache --min=1 --max=10 --cpu-percent=70 --memory-percent=70
```

**步骤 3：查看 HPA 状态**

```shell
# 查看所有hpa
kubectl get hpa

# 详细描述，看指标、事件、扩缩记录
kubectl describe hpa php-apache
```

关键字段解读：
* Targets：当前 PodCPU / 内存使用率 | 目标阈值
* Min replicas / Max replicas：最小最大副本限制
* Replicas：当前运行副本数

**步骤 4：压力测试，触发自动扩容**

新开终端创建压测 Pod，持续疯狂请求服务拉高 CPU：
```shell
kubectl run load-generator --image=busybox --rm -it -- /bin/sh -c "while true; do wget -q -O- http://php-svc; done"
```

持续观察 HPA 和 Deployment 副本变化：
```shell
kubectl get hpa php-apache -w
kubectl get deploy php-apache -w
```

现象：CPU 使用率持续超过 70% → HPA 逐步增加副本至最大值 10。

**步骤 5：停止压测，观察自动缩容**

直接关闭压测终端，等待 5 分钟冷却窗口，HPA 自动减少 Pod 回到最小副本 1。

**步骤 6：HPA yaml 完整写法（生产推荐，便于版本管理）**

替代autoscale命令

hpa-basic.yaml
```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: php-apache
spec:
  # 绑定需要扩缩的Deployment
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: php-apache
  minReplicas: 1
  maxReplicas: 10
  # 双指标：CPU、内存
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70
  - type: Resource
    resource:
      name: memory
      target:
        type: Utilization
        averageUtilization: 70
```

# 部署
```shell
kubectl apply -f hpa-basic.yaml
# 删除
kubectl delete hpa php-apache
```

## 四、实操 2：自定义扩缩容行为（快慢扩容、缩容保护）

默认冷却规则业务容易抖动，通过 behavior 自定义扩缩速度，生产必配：

hpa-behavior.yaml
```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: php-apache
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: php-apache
  minReplicas: 1
  maxReplicas: 10
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70
  behavior:
    # 扩容：快速扩容，无等待冷却
    scaleUp:
      stabilizationWindowSeconds: 0
      policies:
      - type: Percent
        value: 100  # 一次最多扩容100%副本
        periodSeconds: 15
    # 缩容：慢缩容，等待10分钟稳定再删Pod，防抖动
    scaleDown:
      stabilizationWindowSeconds: 600
      policies:
      - type: Percent
        value: 10
        periodSeconds: 60
```

字段说明：
* scaleUp.stabilizationWindowSeconds:0：流量突增立刻扩容，不等待 3 分钟冷却，应对突发峰值
* scaleDown.stabilizationWindowSeconds:600：低负载持续 10 分钟才缩容，避免流量瞬间起伏反复创建删除 Pod

## 五、实操 3：HPA 适配 StatefulSet（有状态应用自动扩缩）

HPA 同样支持 StatefulSet，语法完全一致，仅修改 scaleTargetRef 绑定 STS：

hpa-sts.yaml
```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: sts-hpa
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: StatefulSet
    name: sts-demo # 替换为你的sts名称
  minReplicas: 2
  maxReplicas: 8
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        averageUtilization: 75
```

部署后，负载升高会自动新增有序 Pod（sts-demo-3、sts-demo-4），负载降低逆序删除大号 Pod。

**关键注意点**

StatefulSet 扩容会自动创建 PVC，缩容只会删除 Pod，不会自动删除 PVC，需要手动清理存储。

