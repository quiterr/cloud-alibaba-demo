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
      parallel {
        stage('gateway') {
          steps {
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
                --insecure --skip-tls-verify
                --cache=true \
                --cache-repo=${HARBOR_ADDR}/${PROJECT}/kaniko-cache \
'''
            }
          }
        }
        stage('user-service') {
          steps {
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
                --insecure --skip-tls-verify
                --cache=true \
                --cache-repo=${HARBOR_ADDR}/${PROJECT}/kaniko-cache \
'''
            }
          }
        }
        stage('order-service') {
          steps {
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
                --insecure --skip-tls-verify
                --cache=true \
                --cache-repo=${HARBOR_ADDR}/${PROJECT}/kaniko-cache \
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

kubectl set image deployment/gateway gateway=192.168.133.129:30002/spring_cloud_demo/gateway:${GIT_SHORT_COMMIT}
kubectl set image deployment/user-service user-service=192.168.133.129:30002/spring_cloud_demo/user-service:${GIT_SHORT_COMMIT}
kubectl set image deployment/order-service order-service=192.168.133.129:30002/spring_cloud_demo/order-service:${GIT_SHORT_COMMIT}

# 等待所有deployment滚动完成
kubectl rollout status deployment/gateway
kubectl rollout status deployment/user-service
kubectl rollout status deployment/order-service

'''
        }
      }
    }
  }
}
