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