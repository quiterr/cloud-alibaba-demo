# helm

## 一、Helm3 基础认知

**三大核心名词**

1. **Chart**：K8s 应用安装包（模板 + 参数，类似 yum/rpm 包）
2. **Repository**：Chart 仓库（存放各类应用 Chart）
3. **Release**：Chart 安装到集群后的**实例**，一个 Chart 可多 Release

> Helm3 移除 Tiller，直接使用 kubectl 权限，无需单独服务

**Helm3 一键安装**
```shell
# 官方一键安装脚本
curl https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 | bash
# 验证版本
helm version
```

**Chart 标准目录结构（helm create 生成）**
```text
mychart/
├── Chart.yaml       # Chart元数据（名称、版本、依赖）
├── values.yaml      # 全局默认配置（镜像、副本、资源、存储）
├── templates/       # K8s资源模板Deployment/Service/PVC/Secret
│   ├── _helpers.tpl # 公共模板函数（标签、命名规范）
│   ├── deployment.yaml
│   ├── service.yaml
│   └── pvc.yaml
└── charts/          # 子依赖Chart（如内置redis/mysql）
```

## 二、Helm 全量核心命令（带详细注释）

1. 仓库管理
```shell
# 添加Bitnami官方仓库（redis/nginx/mysql生产级Chart）
helm repo add bitnami https://charts.bitnami.com/bitnami
# 国内阿里云镜像（访问更快）
helm repo add aliyun-bitnami https://mirrors.aliyuncs.com/bitnami-charts/
# 更新本地仓库索引缓存
helm repo update
# 查看已添加仓库
helm repo list
# 搜索仓库内redis Chart
helm search repo redis
# 查看Chart详情、默认参数
helm show values bitnami/redis
# 拉取Chart到本地解压修改
helm pull bitnami/redis --version 17.10.1
tar -zxvf redis-17.10.1.tgz
```

2. Release 生命周期（安装 / 查看 / 升级 / 回滚 / 卸载）
```shell
# 1. 安装Release（指定命名空间，不存在自动创建）
helm install redis bitnami/redis -n redis --create-namespace
# -f 指定自定义配置文件覆盖默认values
helm install redis bitnami/redis -f redis-values.yaml -n redis
# --set 临时覆盖单个参数（优先级最高）
helm install redis bitnami/redis --set auth.password=Redis@123 -n redis

# 2. 查看集群所有Release
helm list -A
# 查看指定Release运行状态、资源清单
helm status redis -n redis
# 渲染模板（仅输出yaml，不部署，调试用）
helm template redis bitnami/redis -f redis-values.yaml

# 3. 升级Release（修改values后重新发布）
helm upgrade redis bitnami/redis -f redis-values.yaml -n redis
# 查看Release升级历史（版本号用于回滚）
helm history redis -n redis

# 4. 回滚到历史版本（1=第一次安装版本）
helm rollback redis 1 -n redis

# 5. 卸载Release（删除所有k8s资源，PVC默认保留）
helm uninstall redis -n redis
# 卸载同时删除PVC存储
helm uninstall redis -n redis --delete-pvc
```

3. 依赖管理（Chart 嵌套 redis/mysql）
```shell
# 下载Chart.yaml中定义的子依赖
helm dependency update ./mychart
# 打包Chart为tgz安装包
helm package ./mychart
```

## 三、Redis Helm 实战 1：单机版 Redis（测试 / 开发）

**步骤 1：自定义配置 redis-values.yaml**
```yaml
# 部署模式 standalone单机 / sentinel哨兵高可用
architecture: standalone

# 账号密码认证
auth:
  enabled: true
  password: "Redis@2026" # 固定密码，生产建议Secret注入

# Master主节点配置
master:
  # 副本数单机固定1
  replicaCount: 1
  # 容器资源限制
  resources:
    requests:
      cpu: 100m
      memory: 256Mi
    limits:
      cpu: 500m
      memory: 512Mi
  # 持久化存储开启
  persistence:
    enabled: true
    size: 1Gi
    storageClass: "nfs-dynamic-sc" # 使用你集群NFS存储类
    accessModes:
      - ReadWriteOnce

# Service服务类型
service:
  type: ClusterIP # 集群内部访问，测试可改为NodePort
  port: 6379

# 关闭监控、副本（单机不需要）
replica:
  replicaCount: 0
sentinel:
  enabled: false
```

**步骤 2：执行安装命令**
```shell
# 创建命名空间并部署单机redis
helm install redis bitnami/redis -f redis-values.yaml -n redis --create-namespace
```

**步骤 3：验证资源、连接 Redis**
```shell
# 查看pod、svc、pvc、secret
kubectl get all,pvc,secret -n redis

# 获取redis密码两种方式
# 方式1：从secret读取
export REDIS_PWD=$(kubectl get secret redis -n redis -o jsonpath="{.data.redis-password}" | base64 -d)
echo $REDIS_PWD

# 进入pod内部连接redis
kubectl exec -it statefulset/redis-master -n redis -- redis-cli -a $REDIS_PWD
# 简单读写测试
127.0.0.1:6379> set testkey 123
127.0.0.1:6379> get testkey
```

**步骤 4：升级示例（扩容存储至 2G）**

修改 redis-values.yaml `master.persistence.size:2Gi`
```shell
helm upgrade redis bitnami/redis -f redis-values.yaml -n redis
# 查看升级记录
helm history redis -n redis
```

## 四、Redis Helm 实战 2：Sentinel 哨兵高可用（生产推荐）

**高可用架构说明**

1 主 + 2 从 + 3 哨兵；主宕机哨兵自动切换从为主，自动更新 Service 后端地址

**高可用 values-sentinel.yaml**
```yaml
# 切换为哨兵架构
architecture: sentinel

# 登录密码
auth:
  enabled: true
  password: "RedisHA@2026"

# 主节点配置
master:
  resources:
    requests:
      cpu: 200m
      memory: 512Mi
    limits:
      cpu: 1000m
      memory: 1Gi
  persistence:
    enabled: true
    size: 20Gi
    storageClass: "nfs-dynamic-sc"

# 从节点 2副本
replica:
  replicaCount: 2
  resources:
    requests:
      cpu: 100m
      memory: 256Mi
    limits:
      cpu: 500m
      memory: 512Mi
  persistence:
    enabled: true
    size: 20Gi

# 哨兵3副本（奇数，选主投票）
sentinel:
  replicaCount: 3
  resources:
    requests:
      cpu: 50m
      memory: 128Mi
    limits:
      cpu: 200m
      memory: 256Mi
  # 哨兵监控名称（客户端连接使用）
  masterSet: mymaster

# 服务
service:
  type: ClusterIP
```

哨兵部署 & 验证命令
```shell
# 部署高可用哨兵redis
helm install redis-ha bitnami/redis -f values-sentinel.yaml -n redis-ha --create-namespace

# 查看所有pod（1主2从3哨兵共6个pod）
kubectl get pods -n redis-ha

# 进入哨兵查看主节点状态
kubectl exec -it statefulset/redis-ha-sentinel -n redis-ha -- redis-cli -p 26379 -a RedisHA@2026
# 查看监控主节点信息
127.0.0.1:26379> SENTINEL masters

# 业务客户端连接地址
# 集群内地址：redis-ha.redis-ha.svc.cluster.local 端口6379
# 哨兵连接：redis-ha-sentinel.redis-ha.svc.cluster.local 26379
```

## 五、常用排错 & 调试命令
```shell
# 1. 渲染模板查看最终yaml，排查语法错误
helm template redis bitnami/redis -f redis-values.yaml --debug

# 2. 查看release所有配置（升级后对比差异）
helm get values redis -n redis

# 3. 查看release完整k8s资源清单
helm get manifest redis -n redis

# 4. 查看pod日志，redis启动异常排查
kubectl logs statefulset/redis-master -n redis

# 5. 回滚测试（升级出错一键恢复）
helm rollback redis 1 -n redis
helm history redis -n redis
```

## 六、清理资源全套命令
```shell
# 卸载单机redis，保留PVC
helm uninstall redis -n redis
# 卸载哨兵redis并删除PVC存储
helm uninstall redis-ha -n redis-ha --delete-pvc
# 删除命名空间（清空所有资源）
kubectl delete ns redis redis-ha
```

## 七、生产最佳实践总结

1. **密码**：不硬编码 values，提前创建 Secret 通过`auth.existingSecret`引用；
2. **存储**：统一指定 StorageClass（如你的 nfs-dynamic-sc），避免动态存储漂移；
3. **高可用**：生产必须使用 sentinel 架构，哨兵副本奇数 3 个；
4. **资源**：必配 requests/limits，防止节点资源抢占；
5. **发布**：升级前先用`helm template`预校验模板，灰度升级；
6. **持久化**：单机 / 主从全部开启 persistence，防止数据丢失；
7. **权限**：使用 ServiceAccount 最小权限，禁止 root 运行 redis 容器。