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
