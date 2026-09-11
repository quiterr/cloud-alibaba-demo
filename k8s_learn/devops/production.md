# 生产级别的配置

## 1. Dockerfile

每个微服务模块目录下，gateway-server、order-service、user-service 各放一份，只改 jar 名称

gateway-server/Dockerfile
```dockerfile
FROM openjdk:17-jdk-slim
WORKDIR /app
COPY target/gateway-server-0.0.1-SNAPSHOT.jar app.jar
# JVM参数，生产推荐配置
ENTRYPOINT ["java","-XX:+UseContainerSupport","-XX:MaxRAMPercentage=70.0","-jar","app.jar"]
```

order-service/Dockerfile
```dockerfile
FROM openjdk:17-jdk-slim
WORKDIR /app
COPY target/order-service-0.0.1-SNAPSHOT.jar app.jar
ENTRYPOINT ["java","-XX:+UseContainerSupport","-XX:MaxRAMPercentage=70.0","-jar","app.jar"]
```

user-service/Dockerfile
```dockerfile
FROM openjdk:17-jdk-slim
WORKDIR /app
COPY target/user-service-0.0.1-SNAPSHOT.jar app.jar
ENTRYPOINT ["java","-XX:+UseContainerSupport","-XX:MaxRAMPercentage=70.0","-jar","app.jar"]
```

## 2. K8s YAML

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

## 3. Jenkinsfile 流水线

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
  - name: maven
    image: maven:3.8.8-openjdk-17
    command: ['cat']
    tty: true
  - name: kaniko
    image: gcr.io/kaniko-project/executor:v1.21.0-debug
    command: ['cat']
    tty: true
  - name: kubectl
    image: alpine/k8s:1.28.0
    command: ['cat']
    tty: true
"""
    }
  }
  environment {
    HARBOR_ADDR = "192.168.133.129:30002"
    PROJECT = "spring_cloud_demo"
    TAG = "v1"
  }
  stages {
    stage('拉取代码') {
      steps {
        checkout scm
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
      parallel {
        stage('gateway') {
          steps {
            container('kaniko') {
              sh '''
/kaniko/executor \
--context="${pwd}/gateway-server" \
--dockerfile="${pwd}/gateway-server/Dockerfile" \
--destination=${HARBOR_ADDR}/${PROJECT}/gateway:${TAG} \
--insecure --skip-tls-verify
'''
            }
          }
        }
        stage('user-service') {
          steps {
            container('kaniko') {
              sh '''
/kaniko/executor \
--context="${pwd}/user-service" \
--dockerfile="${pwd}/user-service/Dockerfile" \
--destination=${HARBOR_ADDR}/${PROJECT}/user-service:${TAG} \
--insecure --skip-tls-verify
'''
            }
          }
        }
        stage('order-service') {
          steps {
            container('kaniko') {
              sh '''
/kaniko/executor \
--context="${pwd}/order-service" \
--dockerfile="${pwd}/order-service/Dockerfile" \
--destination=${HARBOR_ADDR}/${PROJECT}/order-service:${TAG} \
--insecure --skip-tls-verify
'''
            }
          }
        }
      }
    }
    stage('K8s部署应用') {
      steps {
        container('kubectl') {
          sh '''
apk add --no-cache gcompat
kubectl apply -f k8s/gateway.yaml
kubectl apply -f k8s/user-service.yaml
kubectl apply -f k8s/order-service.yaml
kubectl rollout status deployment/gateway
kubectl rollout status deployment/user-service
kubectl rollout status deployment/order-service
'''
        }
      }
    }
  }
}
```

## 4. 目录最终整理
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

## 5. 部署验证命令
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

## 6. checkout scm

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

## 补充说明

### 版本：当前固定`v1`，正式环境建议用 git commit 短 hash 作为镜像 tag，避免覆盖旧镜像。
```groovy
  environment {
    HARBOR_ADDR = "192.168.133.129:30002"
    PROJECT = "spring_cloud_demo"
    // 动态获取git 8位短commit hash
    GIT_SHORT_COMMIT = sh(script: 'git rev-parse --short=8 HEAD', returnStdout: true).trim()
  }
```

2. 配置：把 SpringBoot 配置抽离到 ConfigMap/Secret，不要打包进镜像。


3. 解释程序启动参数 +UseContainerSupport,MaxRAMPercentage=70.0，这里的内存限制与k8s的资源限制是什么关系？

4. 生产环境replicas: 1副本设置为多少比较合适？

5. 网关用的nodeport，生产是不是建议类似ingress，目前主流是ngf？

6. 既然Jenkinsfile放在了项目根目录，还需要拷贝到Jenkins流水线吗？

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
# 拉取镜像
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






