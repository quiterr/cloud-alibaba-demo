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






