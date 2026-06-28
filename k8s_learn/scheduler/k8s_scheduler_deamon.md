# k8s守护进程

## 一、DaemonSet 核心概念
1. 定义
   
   DaemonSet（DS）保证集群每一个符合条件的节点上，仅运行一份 Pod 副本。
   新节点加入集群，自动部署 Pod；节点下线，自动回收该节点上的 Pod。

2. 典型使用场景（运维必备组件）

*    节点日志收集：filebeat、fluentd
*    节点监控采集：node-exporter
*    网络插件：calico、flannel、cilium
*    存储客户端：ceph-client、nfs-agent
*    节点安全、审计、硬件巡检程序

DS 没有 replicas 字段，副本总数 = 符合调度条件的节点总数。

3. 调度控制：只在部分节点部署

筛选节点：
*  nodeSelector：匹配节点标签，只打标签的节点部署 DS Pod
*  nodeAffinity：复杂多条件亲和调度
*  tolerations：容忍节点污点（master 节点默认有污点，默认不调度 DS 到 master）

## 二、实操完整流程（从零创建、查看、更新、删除）

容器日志默认输出到节点 `/var/log/containers`，部署Fluentd进行采集。

**步骤 1：创建 rbac 权限（Fluentd 需要读取节点 Pod 元数据）**

fluentd-rbac.yaml
```yaml
apiVersion: v1
kind: ServiceAccount
metadata:
  name: fluentd
  namespace: kube-system
---
apiVersion: rbac.authorization.k8s.io/v1
kind: ClusterRole
metadata:
  name: fluentd
rules:
- apiGroups: [""]
  resources:
  - pods
  - namespaces
  verbs: ["get", "list", "watch"]
---
apiVersion: rbac.authorization.k8s.io/v1
kind: ClusterRoleBinding
metadata:
  name: fluentd
roleRef:
  apiGroup: rbac.authorization.k8s.io
  kind: ClusterRole
  name: fluentd
subjects:
- kind: ServiceAccount
  name: fluentd
  namespace: kube-system
```

执行创建
```shell
kubectl apply -f fluentd-rbac.yaml
```

**步骤 2：创建 Fluentd 配置文件 ConfigMap**

fluentd-configmap.yaml
```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: fluentd-config
  namespace: kube-system
data:
  fluent.conf: |
    <source>
      @type tail
      path /var/log/containers/*.log
      pos_file /var/log/fluentd-containers.log.pos
      tag kube.*
      read_from_head true
      <parse>
        @type json
        time_key time
        time_format %Y-%m-%dT%H:%M:%S.%NZ
      </parse>
    </source>

    <filter kube.**>
      @type kubernetes_metadata
    </filter>

    # 输出到标准输出调试，生产替换为 elasticsearch
    <match kube.**>
      @type stdout
    </match>
```

```shell
kubectl apply -f fluentd-configmap.yaml
```

**步骤 3：核心 DaemonSet 清单**

fluentd-ds.yaml
```yaml
apiVersion: apps/v1
kind: DaemonSet
metadata:
  name: fluentd
  namespace: kube-system
  labels:
    app: fluentd
spec:
  selector:
    matchLabels:
      app: fluentd
  # 滚动更新策略，最多同时1个节点停止采集
  updateStrategy:
    type: RollingUpdate
    rollingUpdate:
      maxUnavailable: 1
  template:
    metadata:
      labels:
        app: fluentd
    spec:
      serviceAccountName: fluentd
      # 容忍master污点，控制节点也部署采集器
      tolerations:
      - key: node-role.kubernetes.io/control-plane
        operator: Exists
        effect: NoSchedule
      containers:
      - name: fluentd
        image: fluent/fluentd-kubernetes-daemonset:v1.16-debian
        resources:
          limits:
            cpu: 200m
            memory: 256Mi
          requests:
            cpu: 100m
            memory: 128Mi
        volumeMounts:
        # 挂载宿主机容器日志目录
        - name: var-log
          mountPath: /var/log
        # 挂载fluentd配置
        - name: config-volume
          mountPath: /fluentd/etc
      volumes:
      - name: var-log
        hostPath:
          path: /var/log
      - name: config-volume
        configMap:
          name: fluentd-config
```

```shell
kubectl apply -f fluentd-ds.yaml
```

## 四、查看 DaemonSet 资源状态


```shell
# 1. 查看 DaemonSet 列表
# 完整名称
kubectl get daemonset -n kube-system
# 简写 ds
kubectl get ds -n kube-system

#2. 查看所有 Fluentd Pod（每个节点一个）
kubectl get pod -n kube-system -o wide | grep fluentd

#3. 查看 DaemonSet 详细信息
kubectl describe ds fluentd -n kube-system

#4. 查看 fluentd 实时采集日志
kubectl logs -f fluentd-xxxx -n kube-system # 替换为你集群任意一个fluentd pod名称

#5. 进入 Fluentd Pod 内部验证宿主机目录挂载
kubectl exec -it fluentd-xxxx -n kube-system -- sh
# 查看挂载的宿主机容器日志
ls /var/log/containers
```

## 五、调度控制实操（只在部分节点部署采集器）

案例：仅业务节点（node1/node2）部署 fluentd，排除 node3
```shell
# 给业务节点打标签
kubectl label node k8s-node1 log-collect=enable
kubectl label node k8s-node2 log-collect=enable

# 在 ds template.spec 添加 nodeSelector
nodeSelector:
  log-collect: enable
  
# 重新 apply ds，此时 k8s-node3 不会创建 fluentd Pod。
```

## 六、删除 DaemonSet 资源
```shell
kubectl delete -f fluentd-ds.yaml
kubectl delete -f fluentd-configmap.yaml
kubectl delete -f fluentd-rbac.yaml
```

## 七、生产拓展：日志输出到 Elasticsearch

修改 ConfigMap 内 match 段，替换 stdout 输出为 es：
```shell
<match kube.**>
  @type elasticsearch
  host elasticsearch.default.svc.cluster.local
  port 9200
  logstash_format true
  logstash_prefix k8s-log
</match>
```

## 报错及修复

```shell
# 执行
kubectl describe pod fluentd-bh94t -n kube-system
# 输出
Warning  FailedCreatePodSandBox  9m36s                   kubelet            Failed to create pod sandbox: rpc error: code = Unknown desc = failed to setup network for sandbox "00cb457a053deb75595ac499f985bc662892cf736f469a91a6f2758c7d7ca42c": plugin type="calico" failed (add): error getting ClusterInformation: connection is unauthorized: Unauthorized
```

先检查 calico-node 的权限资源是否完整

```shell
# 查看 calico ServiceAccount
kubectl get sa -n kube-system | grep calico
# 查看 calico ClusterRole/ClusterRoleBinding
kubectl get clusterrole | grep calico
kubectl get clusterrolebinding | grep calico
```

全部都是正常的，豆包建议重新安装calico，重装有效。
但是fluented镜像拉取失败，放弃了。