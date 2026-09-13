# 生产级别的配置

## Dockerfile

每个微服务模块目录下，gateway-server、order-service、user-service 各放一份，只改 jar 名称

gateway-server/Dockerfile
```dockerfile
FROM 192.168.133.129:30002/spring_cloud_demo/eclipse-temurin:17-jre
WORKDIR /app
COPY target/gateway-server-0.0.1-SNAPSHOT.jar app.jar
# JVM参数，生产推荐配置，JAVA_OPTS后续由k8s yaml传入
ENTRYPOINT ["sh","-c","java $JAVA_OPTS -jar app.jar"]
```

order-service/Dockerfile
```dockerfile
FROM 192.168.133.129:30002/spring_cloud_demo/eclipse-temurin:17-jre
WORKDIR /app
COPY target/order-service-0.0.1-SNAPSHOT.jar app.jar
ENTRYPOINT ["sh","-c","java $JAVA_OPTS -jar app.jar"]
```

user-service/Dockerfile
```dockerfile
FROM 192.168.133.129:30002/spring_cloud_demo/eclipse-temurin:17-jre
WORKDIR /app
COPY target/user-service-0.0.1-SNAPSHOT.jar app.jar
ENTRYPOINT ["sh","-c","java $JAVA_OPTS -jar app.jar"]
```

## K8s YAML

单独新建 `k8s` 文件夹，统一存放所有资源

gateway.yaml
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: gateway
  namespace: default
spec:
  replicas: 1
  selector:
    matchLabels:
      app: gateway
  template:
    metadata:
      labels:
        app: gateway
    spec:
      containers:
      - name: gateway
        image: 192.168.133.129:30002/spring_cloud_demo/gateway:v1
        ports:
        - containerPort: 8080
        resources:
          requests:
            memory: "512Mi"
            cpu: "200m"
          limits:
            memory: "1Gi"
            cpu: "1000m"
        readinessProbe:
          httpGet:
            path: /actuator/health
            port: 8080
          initialDelaySeconds: 20
          periodSeconds: 5
        livenessProbe:
          httpGet:
            path: /actuator/health
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 10
---
apiVersion: v1
kind: Service
metadata:
  name: gateway
  namespace: default
spec:
  type: NodePort
  selector:
    app: gateway
  ports:
  - port: 80
    targetPort: 8080
    nodePort: 30080
```

user-service.yaml
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: user-service
  namespace: default
spec:
  replicas: 1
  selector:
    matchLabels:
      app: user-service
  template:
    metadata:
      labels:
        app: user-service
    spec:
      containers:
      - name: user-service
        image: 192.168.133.129:30002/spring_cloud_demo/user-service:v1
        ports:
        - containerPort: 8081
        resources:
          requests:
            memory: "512Mi"
            cpu: "200m"
          limits:
            memory: "1Gi"
            cpu: "1000m"
        readinessProbe:
          httpGet:
            path: /actuator/health
            port: 8081
          initialDelaySeconds: 20
          periodSeconds: 5
        livenessProbe:
          httpGet:
            path: /actuator/health
            port: 8081
          initialDelaySeconds: 30
          periodSeconds: 10
---
apiVersion: v1
kind: Service
metadata:
  name: user-service
  namespace: default
spec:
  type: ClusterIP
  selector:
    app: user-service
  ports:
  - port: 80
    targetPort: 8081
```

order-service.yaml
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: order-service
  namespace: default
spec:
  replicas: 1
  selector:
    matchLabels:
      app: order-service
  template:
    metadata:
      labels:
        app: order-service
    spec:
      containers:
      - name: order-service
        image: 192.168.133.129:30002/spring_cloud_demo/order-service:v1
        ports:
        - containerPort: 8082
        resources:
          requests:
            memory: "512Mi"
            cpu: "200m"
          limits:
            memory: "1Gi"
            cpu: "1000m"
        readinessProbe:
          httpGet:
            path: /actuator/health
            port: 8082
          initialDelaySeconds: 20
          periodSeconds: 5
        livenessProbe:
          httpGet:
            path: /actuator/health
            port: 8082
          initialDelaySeconds: 30
          periodSeconds: 10
---
apiVersion: v1
kind: Service
metadata:
  name: order-service
  namespace: default
spec:
  type: ClusterIP
  selector:
    app: order-service
  ports:
  - port: 80
    targetPort: 8082
```

## Jenkinsfile 流水线

适配当前 Jenkins+K8s+Kaniko 环境

放在项目根目录 `demo/Jenkinsfile`
```groovy
pipeline {
    agent {
        kubernetes {
            yaml """
apiVersion: v1
kind: Pod
spec:
  containers:
  - name: jnlp
    image: jenkins/inbound-agent:jdk21
    resources:
      limits:
        cpu: 2
        memory: 2Gi
  - name: maven
    image: maven:3.9.8-eclipse-temurin-17
    command: ['cat']
    tty: true
    volumeMounts:
    - name: maven-repo
      mountPath: /root/.m2/repository
  - name: kaniko
    image: gcr.io/kaniko-project/executor:v1.22.0-debug
    command: ['cat']
    tty: true
    volumeMounts:
    - name: harbor-auth
      mountPath: /kaniko/.docker
  - name: kubectl
    image: "alpine/k8s:1.28.0"
    command: ['cat']
    tty: true
  volumes:
  - name: harbor-auth
    secret:
      secretName: jenkins-harbor-secret
  - name: maven-repo
    persistentVolumeClaim:
      claimName: maven-repo-pvc
"""
        }
    }
    environment {
        HARBOR_ADDR = "192.168.133.129:30002"
        PROJECT = "spring_cloud_demo"
        HARBOR_USER = "admin"
        HARBOR_PWD = "Admin@123456"
    }

    stages {
        stage('拉取代码') {
            steps {
                checkout scm
            }
        }
        stage('打印版本信息') {
            steps {
                script {
                    def shortHash = sh(
                            script: 'git rev-parse --short=8 HEAD',
                            returnStdout: true
                    ).trim()
                    env.GIT_SHORT_COMMIT = shortHash
                    echo "短Commit哈希：${env.GIT_SHORT_COMMIT}"
                }
            }
        }
        stage('Maven全模块打包') {
            steps {
                container('maven') {
                    sh 'mvn clean package -DskipTests'
                }
            }
        }
        stage('Kaniko构建镜像并推送Harbor') {
            steps {
                // gateway 构建
                container('kaniko') {
                    sh '''
              # 代理 大小写字段全部导出，golang库兼容
              export HTTP_PROXY=http://192.168.133.1:7897
              export HTTPS_PROXY=http://192.168.133.1:7897
              export http_proxy=http://192.168.133.1:7897
              export https_proxy=http://192.168.133.1:7897
              export NO_PROXY=localhost,127.0.0.1,192.168.133.0/24,10.96.0.0/12,10.244.0.0/16,.svc,.cluster.local
              export no_proxy=localhost,127.0.0.1,192.168.133.0/24,10.96.0.0/12,10.244.0.0/16,.svc,.cluster.local
                 /kaniko/executor \
                --context=`pwd`/gateway-server \
                --dockerfile="Dockerfile" \
                --destination=${HARBOR_ADDR}/${PROJECT}/gateway:${GIT_SHORT_COMMIT} \
                --insecure --skip-tls-verify \
                --cache=true \
                --cache-repo=${HARBOR_ADDR}/${PROJECT}/kaniko-cache
              '''
                }

                // user-service 构建
                container('kaniko') {
                    sh '''
              # 代理 大小写字段全部导出，golang库兼容
              export HTTP_PROXY=http://192.168.133.1:7897
              export HTTPS_PROXY=http://192.168.133.1:7897
              export http_proxy=http://192.168.133.1:7897
              export https_proxy=http://192.168.133.1:7897
              export NO_PROXY=localhost,127.0.0.1,192.168.133.0/24,10.96.0.0/12,10.244.0.0/16,.svc,.cluster.local
              export no_proxy=localhost,127.0.0.1,192.168.133.0/24,10.96.0.0/12,10.244.0.0/16,.svc,.cluster.local
                 /kaniko/executor \
                --context=`pwd`/user-service \
                --dockerfile="Dockerfile" \
                --destination=${HARBOR_ADDR}/${PROJECT}/user-service:${GIT_SHORT_COMMIT} \
                --insecure --skip-tls-verify \
                --cache=true \
                --cache-repo=${HARBOR_ADDR}/${PROJECT}/kaniko-cache
              '''
                }

                // order-service 构建
                container('kaniko') {
                    sh '''
              # 代理 大小写字段全部导出，golang库兼容
              export HTTP_PROXY=http://192.168.133.1:7897
              export HTTPS_PROXY=http://192.168.133.1:7897
              export http_proxy=http://192.168.133.1:7897
              export https_proxy=http://192.168.133.1:7897
              export NO_PROXY=localhost,127.0.0.1,192.168.133.0/24,10.96.0.0/12,10.244.0.0/16,.svc,.cluster.local
              export no_proxy=localhost,127.0.0.1,192.168.133.0/24,10.96.0.0/12,10.244.0.0/16,.svc,.cluster.local
                 /kaniko/executor \
                --context=`pwd`/order-service \
                --dockerfile="Dockerfile" \
                --destination=${HARBOR_ADDR}/${PROJECT}/order-service:${GIT_SHORT_COMMIT} \
                --insecure --skip-tls-verify \
                --cache=true \
                --cache-repo=${HARBOR_ADDR}/${PROJECT}/kaniko-cache
              '''
                }
            }
        }
        stage('K8s部署应用') {
            steps {
                container('kubectl') {
                    sh '''
        # gateway
        if kubectl get deployment gateway -n default >/dev/null 2>&1; then
          echo "更新gateway镜像"
          kubectl set image deployment/gateway gateway=192.168.133.129:30002/spring_cloud_demo/gateway:${GIT_SHORT_COMMIT} -n default
        else
          echo "首次部署，创建gateway资源"
          sed "s#placeholder#${GIT_SHORT_COMMIT}#g" k8s/gateway.yaml | kubectl apply -f -
        fi

        # user-service
        if kubectl get deployment user-service -n default >/dev/null 2>&1; then
          echo "更新user-service镜像"
          kubectl set image deployment/user-service user-service=192.168.133.129:30002/spring_cloud_demo/user-service:${GIT_SHORT_COMMIT} -n default
        else
          echo "首次部署，创建user-service资源"
          sed "s#placeholder#${GIT_SHORT_COMMIT}#g" k8s/user-service.yaml | kubectl apply -f -
        fi

        # order-service
        if kubectl get deployment order-service -n default >/dev/null 2>&1; then
          echo "更新order-service镜像"
          kubectl set image deployment/order-service order-service=192.168.133.129:30002/spring_cloud_demo/order-service:${GIT_SHORT_COMMIT} -n default
        else
          echo "首次部署，创建order-service资源"
          sed "s#placeholder#${GIT_SHORT_COMMIT}#g" k8s/order-service.yaml | kubectl apply -f -
        fi

        # 等待所有deployment滚动完成
        kubectl rollout status deployment/gateway --timeout=300s -n default
        kubectl rollout status deployment/user-service --timeout=300s -n default
        kubectl rollout status deployment/order-service --timeout=300s -n default

        '''
                }
            }
        }
    }
}

```

## 项目目录
```text
demo
├── gateway-server
│   ├── Dockerfile
│   └── src
├── user-service
│   ├── Dockerfile
│   └── src
├── order-service
│   ├── Dockerfile
│   └── src
├── k8s
│   ├── gateway.yaml
│   ├── user-service.yaml
│   └── order-service.yaml
├── Jenkinsfile
├── pom.xml
└── README.md
```

## 部署验证命令
```shell
# 查看所有pod
kubectl get pods
# 查看service
kubectl get svc
# 测试网关访问，nodeIP:30080
curl http://192.168.133.185:30080
# 进入order-service pod内部测试feign调用
kubectl exec -it deployment/order-service -- sh
curl http://user-service
```

## 多分支流水线

checkout scm 和 git url的区别？

`scm` 是**Jenkins 内置全局对象**，它读取的是 **Jenkins 项目配置页面里配置的源码管理信息**。

✅特点：

1. 来源不是写死在 Jenkinsfile，是读取 Job 网页配置：仓库地址、分支、凭证、refspec 全部来自 Job 配置页面。
2. **多分支流水线（Multibranch Pipeline）必用**：自动识别触发的分支、PR、tag，自动拉取对应分支代码，不用硬写分支。
3. 会保留 Jenkins 的构建元信息：设置`git remote`、build 变量 `GIT_COMMIT`、`GIT_SHORT_COMMIT`、`GIT_BRANCH` 环境变量。
4. 适合：代码仓库地址写在 Jenkins Job 页面，Jenkinsfile 托管在本仓库（Pipeline from SCM）。

⚠️限制：

- 如果你的 Job 是「流水线脚本」（脚本写在 Jenkins 网页输入框），没有在 Job 页面配置 SCM，直接调用`checkout scm`会报错。
- 仓库地址不能在 Jenkinsfile 内部修改，改仓库要去网页上改 Job 配置。

>
> 👉你的场景：**Jenkinsfile 存放在 git 仓库，使用「Pipeline from SCM」模式，就用`checkout scm`，这是标准做法**。
> 此时流水线自动拿到`GIT_COMMIT`，你写的`git rev‑parse --short=8 HEAD`也可以正常工作。

具体怎么设置比较简单，在Jenkins网页点几下就可以了，真正要写的是项目中的Jenkinsfile。

## 集群权限

之前仅分配了Jenkins命名空间的部署权限，这里给集群级别的权限。
注：后面又给Jenkins分配了default空间的各种权限，总之不管指定命名空间，还是全集群，加权限还是比较简单。

```yaml
apiVersion: rbac.authorization.k8s.io/v1
kind: ClusterRole
metadata:
  name: jenkins-deploy-manager
rules:
- apiGroups: ["apps"]
  resources: ["deployments", "statefulsets"]
  verbs: ["get", "list", "watch", "update", "patch"]
- apiGroups: [""]
  resources: ["pods", "services"]
  verbs: ["get", "list", "watch"]
---
apiVersion: rbac.authorization.k8s.io/v1
kind: ClusterRoleBinding
metadata:
  name: jenkins-deploy-manager-binding
subjects:
- kind: ServiceAccount
  name: default
  namespace: jenkins
roleRef:
  kind: ClusterRole
  name: jenkins-deploy-manager
  apiGroup: rbac.authorization.k8s.io
```

```shell
kubectl apply -f rbac-jenkins-sa.yaml
```

## 测试微服务

测试启动参数
```shell

```

gateway已经设置了NodePort 30080，任意集群地址加30080都能访问。 浏览器输入
```shell
http://192.168.133.129:30080/user-service/user/1
```
返回结果
```json
{
    "id": 1,
    "username": "张三",
    "age": 20
}
```

测试服务间调用
```shell
http://192.168.133.129:30080/order-service/order/getUser/1
```

返回结果
```text
There was an unexpected error
```

进入order容器内部，执行命令，就知道是因为shiro拦截重定向了。
```shell
curl -v http://127.0.0.1:8082/order/getUser/1
*   Trying 127.0.0.1:8082...
* Established connection to 127.0.0.1 (127.0.0.1 port 8082) from 127.0.0.1 port 52074
* using HTTP/1.x
> GET /order/getUser/1 HTTP/1.1
> Host: 127.0.0.1:8082
> User-Agent: curl/8.18.0
> Accept: */*
>
* Request completely sent off
< HTTP/1.1 302
< Set-Cookie: JSESSIONID=3A590A28F2150D3E3873B0C84D16206A; Path=/; HttpOnly
< Location: http://127.0.0.1:8082/login;jsessionid=3A590A28F2150D3E3873B0C84D16206A
< Content-Length: 0
< Date: Sat, 12 Sep 2026 07:50:17 GMT
<
* Connection #0 to host 127.0.0.1:8082 left intact
```

## 公司生产环境资源配置
由于只有3台ECS，和家里一样的只有2个worker，那么replicas就是2。 每台ECS有8核16G内存。

> 资源分配原则：
> 1. 预留节点系统开销：每台 ECS 预留 **1 核 + 2G 内存** 给操作系统、containerd、kubelet、calico 网络组件，不交给 Pod 调度
     单节点可分配给 Pod 资源：**7 核，14Gi**
     3 节点总可调度资源：`21核，42Gi`
> 2. A、B 是流量大头，资源倾斜；C、D 轻量，资源小。
> 3. 所有服务副本≥2，pod 反亲和，分散在不同 ECS，单 ECS 宕机业务不中断。
> 4. requests 是调度预留，limits 是硬上限；JVM 使用`MaxRAMPercentage=70%`。

各服务资源规格（容器 requests /limits）

| 服务 | 副本数 | CPU(request/limit) | 内存 (request/limit) | 说明 |
| ---- | ---- | ---- | ---- | ---- |
| gateway 网关 | 2 | 200m / 1000m | 512Mi / 1Gi | 网关，流量转发，压力随整体流量变化，2副本跨节点 |
| A 服务（45% 流量） | 2 | 1000m / 2500m | 1Gi / 2Gi | 核心业务，占流量 45%，堆内存充足 |
| B 服务（45% 流量） | 2 | 1000m / 2500m | 1Gi / 2Gi | 核心业务，和 A 同等规格 |
| C 服务（5% 流量） | 2 | 200m / 500m | 256Mi / 512Mi | 轻量附属服务 |
| D 服务（5% 流量） | 2 | 200m / 500m | 256Mi / 512Mi | 轻量附属服务 |

**汇总统计**

总副本数量：`2+2+2+2+2 = 10个Pod`

- 全部 Pod requests 总和：
  CPU：0.2 + 1.0 +1.0 +0.2 +0.2 = 2.6 核 ×2 副本 = **5.2 核**
  内存：0.5 +1+1+0.25+0.25 =3Gi ×2 副本 = **6Gi**

>
> requests 是调度保底，总和很小，3 节点完全足够调度。

- 全部 Pod limits 峰值总和：
  CPU：(1 +2.5+2.5+0.5+0.5) × 2 = **14 核**
  内存：(1+2+2+0.5+0.5) ×2 = **12Gi**

👉 集群可调度资源 21 核 / 42Gi，峰值 limits 总和 14 核、12Gi，**资源富余**，预留余量应对流量波动，同时方便后续开启 HPA 扩容。

**1）节点资源预留，一定要配置 K8s 节点预留**
每台节点 kubelet 配置预留：
```yaml
kubeReserved:
  cpu: "1000m"
  memory: "2Gi"
systemReserved:
  cpu: "0"
  memory: "0"
```
防止业务 Pod 吃光整机资源，导致 kubelet/containerd 卡死节点。

**2）资源规格微调备选方案（如果后续压测发现 A/B 压力更大）**

如果压测 A、B 服务 CPU 持续高，可以把 A/B 调整为 `requests:1.5核，limit:3核，内存request:1.5Gi limit:2.5Gi`。

**3）集群资源余量**

当前方案业务 limits 总内存 12Gi，总 CPU14 核；集群可用 21 核、42Gi。
富余资源可以：

- 部署监控组件：Prometheus+Grafana
- 日志组件：EFK
- Harbor、Jenkins 可以单独部署或者剥离到别的机器，**不要和业务混跑抢占资源**

## JVM启动参数

### UseContainerSupport

**`+UseContainerSupport`**：开启容器内存识别。
JVM 不再读取宿主机整机内存，而是**读取容器 cgroup 的内存限额（就是 K8s 配置的`resources.limits.memory`）**。

### MaxRAMPercentage

**`MaxRAMPercentage=70.0`**：JVM 堆最大内存 = **容器 limit 内存 × 70%**

**内存划分拆解（非常关键，很多人踩坑）**

容器总内存上限 `1Gi` 包含**全部进程内存**：

1. JVM 堆内存（Xmx，716M，`MaxRAMPercentage`控制）
2. JVM 非堆内存：元空间 (Metaspace)、JVM 线程栈、堆外内存 DirectBuffer、JNI、共享库
3. 你的 Spring 应用代码、第三方依赖、Shiro、Netty、文件缓冲区等**堆外占用**

> ⚠️ 坑：**MaxRAMPercentage 算的只是堆，不是 JVM 全部内存**
> 容器 limit = Xmx + 元空间 + 线程栈 + 堆外内存 + 操作系统开销
> 所以不能把 MaxRAMPercentage 设成 95%，很容易容器内存打爆，被 k8s OOM kill。
> 一般生产推荐 **60~75%**，你现在 70% 是合理区间。

### MaxMetaspaceSize
`-XX:MaxMetaspaceSize=256m`限制元空间 (Metaspace) 最大占用内存为 256MB
> JDK8+ 废除永久代 (PermGen)，换成 Metaspace（元空间），**元空间存放类的字节码、类信息、方法、常量池**。
元空间默认**没有上限**，不限制的话，代码 / 类加载泄漏会无限吃内存，最终触发容器 OOM 杀死 Pod。

Prometheus 采集`jvm_metadata_space_used`，设置告警，元空间使用率超过 80% 提前预警，不用等到崩了才发现。

## 生产环境replicas设置
**1、集群节点数量限制**

副本数不能超过可用节点数。比如你集群只有 2 个 worker 节点，最多 2 副本做跨节点高可用。

**2、滚动更新策略配合 replicas**

```yaml
strategy:
  type: RollingUpdate
  rollingUpdate:
    maxSurge: 1 # 滚动升级时，可以比期望副本数再多启动 1 个新 Pod
    maxUnavailable: 0
```

`maxUnavailable:0`：滚动发布的时候，**先拉起新 Pod，新 Pod 就绪后才销毁旧 Pod**，零停机发布。
> 如果 replicas=2，滚动过程中最多同时存在 3 个 Pod（2 旧 + 1 新），注意预留集群内存 CPU 资源。

**3、有状态服务（数据库、Redis）不适用这套**
上面说的全部是**无状态微服务**（gateway/order/user 都是无状态）。
有状态服务（mysql、redis）不能简单靠副本，要用 StatefulSet。

**4、关于资源开销**
每个service limit 内存 1Gi，2 副本就预留 2G 内存。
不要盲目设置很大副本数，结合集群资源预算。

**5、关于家里的试验环境**
家里只有2个node节点。
- gateway：`replicas:2` + pod 反亲和，两个 Pod 分别落在 node1、node2
- order-service：`replicas:2` + pod 反亲和
- user-service：`replicas:2` + pod 反亲和
  后续上 HPA，minReplicas=2。

## 补充说明

1、配置：把 SpringBoot 配置抽离到 ConfigMap/Secret，不要打包进镜像。

2、网关用的nodeport，生产是不是建议类似ingress，目前主流是ngf？

3、发布回滚能力

`kubectl rollout undo deployment/xxx`，流水线可以增加一键回滚 stage，发布异常时快速切回上一个稳定版本。


###  kaniko工作目录的两种写法
```text
# 第一种
--context=`pwd`/gateway-server \

# 第二种
WORKSPACE_DIR="/home/jenkins/agent/workspace/spring_cloud_scm_${BRANCH_NAME}"
--context="${WORKSPACE_DIR}/user-service" \
```

### 下载镜像并使用harbor缓存

1、第一种方案，手动下载镜像并推送
```shell
# 拉取镜像，需要给shell加代理
ctr -n k8s.io images pull --platform linux/amd64 docker.io/library/eclipse-temurin:17-jre
# 拉完验证
ctr -n k8s.io images list | grep eclipse
# 打 tag
ctr -n k8s.io images tag docker.io/library/eclipse-temurin:17-jre 192.168.133.129:30002/spring_cloud_demo/eclipse-temurin:17-jre
# 推送
ctr -n k8s.io images push --platform linux/amd64 --plain-http -u admin:Admin@123456 192.168.133.129:30002/spring_cloud_demo/eclipse-temurin:17-jre
```

2、第二种方案：Harbor 创建代理缓存项目

登录 Harbor 页面，新建项目，类型选 **代理缓存 (Proxy Cache)**
    - 项目名称：`dockerio-proxy`
    - 远程仓库：`Docker Hub`
    - 远程仓库 URL：`https://registry-1.docker.io`
    - 填入你的 dockerhub 账号密码（可选，提高拉取限流上限）

> 访问格式：`192.168.133.129:30002/dockerio-proxy/eclipse-temurin:17-jre`


修改Dockerfile
```dockerfile
# 原来
FROM eclipse-temurin:17-jre
# 改成
FROM 192.168.133.129:30002/dockerio-proxy/eclipse-temurin:17-jre
```

3、使用kaniko的cache参数（设置了，感觉没用）
```text
/kaniko/executor \
--context=`pwd`/gateway-server \
--dockerfile="Dockerfile" \
--destination=${HARBOR_ADDR}/${PROJECT}/gateway:${GIT_SHORT_COMMIT} \
--insecure --skip-tls-verify
--cache=true \
--cache-repo=${HARBOR_ADDR}/${PROJECT}/kaniko-cache \
```

## 遇到的难题

程序一直在CrashLoopBackOff（崩溃）
```shell
[root@k8s-node1 ~]# kubectl get pods
NAME                               READY   STATUS             RESTARTS         AGE
gateway-5ddf8f688c-l2n6h           0/1     Running            8 (5m39s ago)    19m
gateway-5ddf8f688c-ntm22           0/1     CrashLoopBackOff   7 (82s ago)      15m
```

查看pod事件，是由于探针检查失败
```shell
  Warning  Unhealthy         15m (x3 over 15m)    kubelet            Liveness probe failed: Get "http://10.244.107.207:8080/actuator/health": dial tcp 10.244.107.207:8080: connect: connection refused
  Warning  Unhealthy         11m (x39 over 16m)   kubelet            Readiness probe failed: Get "http://10.244.107.207:8080/actuator/health": dial tcp 10.244.107.207:8080: connect: connection refused
  Warning  BackOff           84s (x26 over 7m8s)  kubelet            Back-off restarting failed container gateway in pod gateway-5ddf8f688c-ntm22_default(ff8013a3-b873-4c99-a258-d539126053a9)
```

查看程序日志，已经启动成功了，但是日志没有一直刷，而是停止打印了，说明被k8s干掉了。原因是探针在启动后20秒检查，程序却用了79秒才启动成功。
```shell
2026-09-13T13:10:18.167Z  INFO 7 --- [gateway-server] [           main] c.e.g.GatewayServerApplication           : Started GatewayServerApplication in 79.201 seconds (process running for 86.149)
```

Liveness探针达到failureThreshold次数后就会重启pod，而Readiness不会，失败后只是标记为未就绪，不让流量进来，Readiness会继续探测，一旦成功就会标记为就绪。
```shell
# 可以看到order和user重启次数都为1
[root@k8s-node1 ~]# kubectl get pods
NAME                             READY   STATUS    RESTARTS      AGE
gateway-7fbd55567f-nkxds         1/1     Running   0             4m20s
order-service-5df4496599-8nsv5   0/1     Running   1 (49s ago)   4m20s
user-service-5d94d95757-7v754    0/1     Running   1 (69s ago)   4m20s
```
**探针检查并不是根因，根因有两个，一是内存不够导致启动特别慢，后面把gateway副本改成1了；二是user和order的端口配成和gateway一样了**

## 清理命令

```shell
# 删除3个Deployment：gateway、user-service、order-service
kubectl delete deployment gateway user-service order-service -n default

# 删除对应的Service（存在才删，不存在提示NotFound忽略即可）
kubectl delete service gateway user-service order-service -n default

# 删除configmap（如果这几个服务有单独cm，没有就忽略报错）
kubectl delete configmap gateway user-service order-service -n default

# 删除hpa（如果配置了弹性伸缩，没有就忽略）
kubectl delete hpa gateway user-service order-service -n default

# 清理残留Pod（兜底）
kubectl get pods -n default | grep -E "gateway|user-service|order-service" | awk '{print $1}' | xargs kubectl delete pod -n default

# 查看确认
kubectl get deploy,pod,svc -n default
```




