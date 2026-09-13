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
cat > k8s/overlays/dev/kustomization.yaml <<EOF
apiVersion: kustomize.config.k8s.io/v1beta1
kind: Kustomization
resources:
  - ../../base
images:
  - name: gateway
    newName: 192.168.133.129:30002/spring_cloud_demo/gateway
    newTag: ${GIT_SHORT_COMMIT}
  - name: user-service
    newName: 192.168.133.129:30002/spring_cloud_demo/user-service
    newTag: ${GIT_SHORT_COMMIT}
  - name: order-service
    newName: 192.168.133.129:30002/spring_cloud_demo/order-service
    newTag: ${GIT_SHORT_COMMIT}
EOF

# 打印渲染后的yaml，方便调试
echo "=====kustomize渲染结果====="
kubectl kustomize k8s/overlays/dev

echo "=====开始apply，幂等，存在就更新全部配置，不存在就新建====="
kubectl apply -k k8s/overlays/dev -n default

echo "=====查看deployment当前状态====="
kubectl get deploy gateway -n default

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
