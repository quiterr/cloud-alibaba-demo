# 资源调度-deployment

## 创建与配置文件解析

这部分省略，前面已经学过了。

## 滚动更新

一、前置基础回顾
Deployment 不直接管理 Pod，通过 ReplicaSet（RS）实现版本隔离：
* 初始版本 → 生成 旧 RS；
* 修改镜像 / 配置执行 kubectl apply → 创建 全新 RS；
* 滚动更新逻辑：逐步扩容新 RS、逐步缩容旧 RS，实现业务不中断；
* 所有历史 RS 默认保留，支持一键回滚旧版本。

滚动更新是 Deployment 默认更新策略，对比另外一种更新方式 Recreate（先删所有旧 Pod，再起新 Pod，会中断业务，仅测试用）。

二、滚动更新两个核心控制参数（spec.strategy.rollingUpdate）
```yaml
spec:
  strategy:
    type: RollingUpdate  # 默认策略，不停机发布
    rollingUpdate:
      maxSurge: 25%       # 更新期间最多比原副本数多出多少Pod
      maxUnavailable: 25% # 更新期间最多不可用的Pod数量
```
参数详解（百分比 / 数字两种写法）
maxSurge 最大激增
* 百分比：基于当前 replicas 计算，向上取整；
* 含义：更新过程中，临时新增 Pod 上限，提升发布速度；
* 例：replicas=4，maxSurge=25% → 最多同时 4+1=5 个 Pod。

maxUnavailable 最大不可用
* 更新过程中，同时下线旧 Pod 的最大数量；
* replicas=4，maxUnavailable=25% → 最多同时下线 1 个旧 Pod；

**两者不能同时为 0，否则无法发布。**

另一种策略：Recreate（重建）
```yaml
spec:
  strategy:
    type: Recreate
```
流程：全部删除旧 Pod → 等待全部销毁 → 再创建新 Pod，业务会断流，仅适用于数据库、单机独占程序。

**实操 1：基础滚动更新环境准备**
1. deploy-rolling.yaml 初始版本 v1（nginx:1.21-alpine）
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: nginx-roll
  labels:
    app: nginx
spec:
  replicas: 4
  selector:
    matchLabels:
      app: nginx
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxSurge: 25%
      maxUnavailable: 25%
  template:
    metadata:
      labels:
        app: nginx
    spec:
      containers:
      - name: nginx
        image: nginx:1.21-alpine
        ports:
        - containerPort: 80
```

实操命令
```shell
kubectl apply -f deploy-rolling.yaml
# 一次性查看三层资源
kubectl get deploy,rs,pods --show-labels
# 现象：只存在1 个 RS，4 个 Pod。
# 持续监控窗口（新开终端，全程观察发布过程）
kubectl get deploy,rs,pods -w
```

**实操 2：执行滚动更新（升级镜像到 v2 nginx:1.23-alpine）**

**两种更新方式**


```shell
#方式 1：修改 yaml 重新 apply（推荐，版本留存）
#修改镜像字段：image: nginx:1.23-alpine，保存后执行：
kubectl apply -f deploy-rolling.yaml
#方式 2：命令行直接修改镜像（快速测试）
kubectl set image deployment nginx-roll nginx=nginx:1.23-alpine
```

实时观测滚动更新流程（对照 -w 窗口输出）
1. 创建全新 ReplicaSet（新 hash 后缀），DESIRED=1，新增 1 个 Pod（maxSurge=25%）；
2. 新 Pod 就绪后，旧 RS 缩减 1 个 Pod（maxUnavailable=25%）；
3. 循环往复：新 RS 逐步扩容到 4，旧 RS 逐步缩容到 0；
4. 发布完成后：新 RS DESIRED=4，旧 RS DESIRED=0，但不会删除，保留用于回滚。

**验证两个 RS 共存**
```shell
kubectl get rs
```
输出会出现两行 RS：
* 新 RS：DESIRED=4，CURRENT=4
* 旧 RS：DESIRED=0，CURRENT=0

**验证版本记录（发布历史）**
```shell
kubectl rollout history deployment nginx-roll
#输出每条 CHANGE-CAUSE 对应一次发布记录。
```

**实操 3：自定义滚动更新速度（修改 maxSurge/maxUnavailable）**

**场景：业务要求发布更平稳，同时只更新 1 个 Pod**

修改策略：固定数字，不使用百分比

```shell
spec:
  replicas: 4
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxSurge: 1
      maxUnavailable: 1
```

重新 apply，再次执行镜像升级，观察发布节奏变慢，每次只增减 1 个 Pod。

**实操 4：暂停 / 恢复滚动发布（灰度分批发布场景）**
1. 暂停发布
```shell
#暂停发布
kubectl rollout pause deployment nginx-roll
#恢复发布，继续完成更新
kubectl rollout resume deployment nginx-roll
```
**适用场景**

灰度发布：先放 10% 流量验证业务，无问题再 resume 全量发布。

**实操 5：发布失败回滚（核心故障自救能力）**
```shell
#模拟故障：升级为不存在的镜像 nginx:9999
kubectl set image deployment nginx-roll nginx=nginx:9999
```

**观测故障现象**
* 新 RS 创建 Pod，但镜像拉取失败 ErrImagePull；
* 滚动更新停滞，不会继续下线更多旧 Pod（受 maxUnavailable 限制）；
* 业务大部分旧 Pod 正常运行，仅少量故障 Pod，无大面积中断。

**两种回滚方案**

方案 1：一键回滚到上一个稳定版本（最常用）
```shell
kubectl rollout undo deployment nginx-roll
```

**方案 2：指定历史版本号回滚**
```shell
#先查看历史版本号
kubectl rollout history deploy nginx-roll
#假设版本 1 是稳定初始版本，指定回滚
kubectl rollout undo deployment nginx-roll --to-revision=1
#查看回滚实时进度
kubectl rollout status deployment nginx-roll
#输出 deployment "nginx-roll" successfully rolled out 代表完成。
```

**实操 6：控制历史版本保留数量（清理无用 RS）**

默认所有历史 RS 永久保存，集群多版本堆积会占用资源，通过 revisionHistoryLimit 设置保留条数：
```yaml
spec:
  revisionHistoryLimit: 3 # 仅保留最近3次发布的RS，更早自动删除
```

默认值：10；

设置为 0：不保留任何历史，无法回滚，生产禁止。

**清理命令**
```shell
kubectl delete -f deploy-rolling.yaml
kubectl get deploy,rs,pods
```
