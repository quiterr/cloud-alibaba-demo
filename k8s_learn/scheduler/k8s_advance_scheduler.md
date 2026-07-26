# 高级调度

## 一、InitContainer 初始化容器（前置执行、就绪后业务容器才启动）

**作用**

Pod 启动顺序：Init 容器串行执行全部成功 → 业务容器并行启动；常用于拉配置、等待数据库 / NFS 就绪、权限初始化。

1. 带注释完整 yaml init-demo.yaml

```yaml
apiVersion: v1
kind: Pod
metadata:
  name: init-demo-pod
  namespace: default
spec:
  # 初始化容器数组，串行执行，全部成功才启动业务容器
  initContainers:
  - name: wait-nfs-ready
    image: busybox:1.35
    # 循环探测NFS挂载目录，直到目录存在才退出
    command: ['sh', '-c', 'until test -d /data/nfs; do echo "等待NFS存储就绪"; sleep 2; done']
    volumeMounts:
    - name: nfs-storage
      mountPath: /data/nfs
  - name: init-config
    image: busybox:1.35
    command: ['sh', '-c', 'echo "init config success" > /data/config/app.conf']
    volumeMounts:
    - name: config-volume
      mountPath: /data/config
  # 业务容器
  containers:
  - name: business-app
    image: nginx:alpine
    ports:
    - containerPort: 80
    volumeMounts:
    - name: config-volume
      mountPath: /etc/nginx/conf.d
    - name: nfs-storage
      mountPath: /usr/share/nginx/html
  volumes:
  - name: config-volume
    emptyDir: {}
  - name: nfs-storage
    persistentVolumeClaim:
      claimName: dynamic-app-pvc
```

2. 实操 Shell 命令（带注释）
```shell
# 1. 创建pod
kubectl apply -f init-demo.yaml

# 2. 查看pod状态，Init阶段会显示 Init:0/2
kubectl get pods init-demo-pod -w

# 3. 查看init容器日志（指定容器名）
kubectl logs init-demo-pod -c wait-nfs-ready
kubectl logs init-demo-pod

# 4. 查看pod完整事件，观察初始化流程
kubectl describe pod init-demo-pod

# 5. 清理资源
kubectl delete -f init-demo.yaml
```

## 二、污点 (Taint) & 容忍 (Tolerations) 节点调度隔离

**核心概念**

1. Taint：打在**节点**上，排斥 Pod；
2. Tolerations：打在**Pod**上，允许 Pod 调度到带污点节点；
3. 三种污点策略 effect：
    - NoSchedule：新 Pod 不能调度，已存在 Pod 不受影响
    - PreferNoSchedule：尽量不调度，无其他节点时允许调度
    - NoExecute：不仅不调度，还会驱逐当前节点上无对应容忍的 Pod

2.1 节点污点操作 shell 命令
```shell
# 1. 给节点k8s-node1打污点：key=node-type,value=storage,NoSchedule
kubectl taint nodes k8s-node1 node-type=storage:NoSchedule

# 2. 查看节点污点
kubectl describe node k8s-node1 | grep Taints

# 3. 删除污点（减号-）
kubectl taint nodes k8s-node1 node-type=storage:NoSchedule-

# 4. NoExecute 污点，5分钟后驱逐无容忍Pod
kubectl taint nodes k8s-node1 special=gpu:NoExecute --toleration-seconds=300
```

2.2 Pod 容忍 yaml toleration-demo.yaml
```yaml
apiVersion: v1
kind: Pod
metadata:
  name: toleration-demo
spec:
  containers:
  - name: nginx
    image: nginx:alpine
  # 容忍配置，匹配节点污点key=node-type value=storage
  tolerations:
  - key: "node-type"       # 匹配污点key
    operator: "Equal"      # Equal/Exists；Exists不用写value
    value: "storage"       # 污点value
    effect: "NoSchedule"   # 匹配污点策略
  # 容忍NoExecute污点，驱逐等待时间120s
  - key: "special"
    operator: Equal
    value: "gpu"
    effect: NoExecute
    tolerationSeconds: 120
```

```shell
kubectl apply -f toleration-demo.yaml
kubectl get pod toleration-demo -o wide # 查看运行节点
kubectl delete pod toleration-demo
```

## 三、亲和性与反亲和（Pod 间调度约束）

**分类**

1. Pod 亲和性 podAffinity：**希望和指定 Pod 部署在同一节点 / 区域**
2. Pod 反亲和 podAntiAffinity：**禁止和同标签 Pod 部署在同一节点**（高可用必备）
3. 节点亲和 nodeAffinity：强制 / 优先调度到匹配标签的节点

### 3.1 Pod 反亲和实战（高可用，多副本分散不同节点）

pod-antiaffinity.yaml
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: nginx-ha
spec:
  replicas: 3 # 3副本，强制分散到不同节点
  selector:
    matchLabels:
      app: nginx-ha
  template:
    metadata:
      labels:
        app: nginx-ha
    spec:
      containers:
      - name: nginx
        image: nginx:alpine
      # Pod反亲和规则
      affinity:
        podAntiAffinity:
          requiredDuringSchedulingIgnoredDuringExecution: # 强制硬约束
          - labelSelector:
              matchExpressions:
              - key: app
                operator: In
                values: ["nginx-ha"]
            topologyKey: "kubernetes.io/hostname" # 按节点hostname隔离
```

```shell
kubectl apply -f pod-antiaffinity.yaml
# 查看每个pod运行节点，3个pod会分布在不同节点
kubectl get pods -o wide -l app=nginx-ha
kubectl delete deploy nginx-ha
```

### 3.2 Pod 亲和：和 redis 调度到同一节点

pod-affinity.yaml
```yaml
apiVersion: v1
kind: Pod
metadata:
  name: app-web
  labels:
    app: web
spec:
  containers:
  - name: web
    image: nginx:alpine
  affinity:
    podAffinity:
      requiredDuringSchedulingIgnoredDuringExecution:
      - labelSelector:
          matchLabels:
            app: redis
        topologyKey: kubernetes.io/hostname
```

### 3.3 节点亲和 nodeAffinity（强制调度带标签节点）

node-affinity.yaml
```yaml
apiVersion: v1
kind: Pod
metadata:
  name: gpu-pod
spec:
  containers:
  - name: app
    image: busybox
    command: ["sleep","3600"]
  affinity:
    nodeAffinity:
      # 硬约束：必须存在标签 node-hw=gpu
      requiredDuringSchedulingIgnoredDuringExecution:
        nodeSelectorTerms:
        - matchExpressions:
          - key: node-hw
            operator: In
            values: ["gpu"]
      # 软约束：优先调度存储节点，无则随便
      preferredDuringSchedulingIgnoredDuringExecution:
      - weight: 80
        preference:
          matchExpressions:
          - key: node-type
            operator: In
            values: ["storage"]
```

```shell
# 给节点打标签
kubectl label node k8s-node1 node-hw=gpu
# 删除节点标签
kubectl label node k8s-node1 node-hw-
# 查看节点标签
kubectl get nodes --show-labels
```

## 四、CronJob 定时任务（替代 linux crontab，K8s 定时调度）

**语法说明**

`分 时 日 月 周`
示例：`0 2 * * *` 每日凌晨 2 点执行清理任务（适配你达梦 7 天数据清理场景）

cronjob-demo.yaml（定时清理日志）
```yaml
apiVersion: batch/v1
kind: CronJob
metadata:
  name: db-clean-job
  namespace: default
spec:
  # 每日凌晨2点执行
  schedule: "0 2 * * *"
  # 并发策略：Forbid禁止并发、Replace覆盖旧Pod、Allow允许并发
  concurrencyPolicy: Forbid
  # 任务失败保留Pod 3天，成功保留1天
  successfulJobsHistoryLimit: 1
  failedJobsHistoryLimit: 3
  # 任务模板
  jobTemplate:
    spec:
      template:
        spec:
          restartPolicy: OnFailure # 失败重启
          containers:
          - name: clean-task
            image: busybox:1.35
            command:
            - sh
            - -c
            - echo "执行达梦数据库7天过期数据清理脚本"
```

CronJob 全套操作 shell 命令
```shell
# 创建定时任务
kubectl apply -f cronjob-demo.yaml

# 查看所有定时任务
kubectl get cronjobs

# 手动立即触发一次任务（测试用）
kubectl create job --from=cronjob/db-clean-job manual-run-$(date +%Y%m%d)

# 查看执行后的job
kubectl get jobs

# 查看任务日志
kubectl logs job/manual-run-20260726

# 删除定时任务
kubectl delete cronjob db-clean-job
```

## 五、综合调度排查常用命令汇总
```shell
# 1. 查看pod调度完整信息（亲和、容忍、污点匹配详情）
kubectl describe pod xxx-pod

# 2. 手动模拟调度预测，判断pod能否调度到指定节点
kubectl get pod xxx-pod -o yaml | kubectl scheduler simulate --node k8s-node1

# 3. 查看节点污点、标签
kubectl describe node k8s-node1
kubectl get nodes --show-labels

# 4. 查看cronjob最近执行记录
kubectl get jobs --sort-by=.metadata.creationTimestamp

# 5. 批量清理完成的旧job
kubectl delete jobs --field-selector status.successful=1
```
