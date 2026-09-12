# Jenkins

## 安装Jenkins

前置准备
```shell
# 设置代理
export http_proxy=http://192.168.133.1:7897
export https_proxy=http://192.168.133.1:7897
# 集群内部无需代理
export no_proxy=localhost,127.0.0.1,192.168.133.0/24,10.96.0.0/12,10.244.0.0/16
# 添加仓库
helm repo add jenkins https://charts.jenkins.io
# 更新仓库索引
helm repo update jenkins

# 创建命名空间
kubectl create ns jenkins
```

jenkins-values.yaml
```yaml
# Jenkins主服务配置
controller:
  # 先不下插件
  installPlugins: []
  image:
    registry: docker.io
    repository: jenkins/jenkins
    tag: lts
  # 管理员初始密码（可自定义）
  admin:
    password: Admin@Jenkins123
  # 网页端口暴露NodePort，30180访问
  serviceType: NodePort
  nodePort: 30180
  # 资源限制，低配虚拟机可调小
  resources:
    requests:
      cpu: "1000m"
      memory: "2Gi"
    limits:
      cpu: "2000m"
      memory: "4Gi"

# 持久化存储，使用你的NFS存储类
persistence:
  enabled: true
  storageClass: nfs-dynamic-sc
  size: 5Gi

# RBAC权限：Jenkins操作集群创建Agent Pod、部署应用
rbac:
  create: true
  readSecrets: true

serviceAccount:
  create: true
  name: jenkins
  annotations:
    kubernetes.io/service-account.name: jenkins

# 开启动态Agent
agent:
  enabled: true
```

**这个代理一定不能加，加了就算打开图形界面，也会报网络错误。**
代理可以后续在Jenkins图形界面加。
```yaml
  env:
    - name: http_proxy
      value: "http://192.168.133.1:7897"
    - name: https_proxy
      value: "http://192.168.133.1:7897"
    - name: no_proxy
      value: "localhost,127.0.0.1,192.168.133.0/24,10.96.0.0/12,10.244.0.0/16,.svc,.cluster.local,mirrors.huaweicloud.com"
```

**这个配置我感觉也没什么卵用，加了之后在图形界面还是docker官方地址，还是要手动改。**
```yaml
  env:
    - name: JENKINS_UC
      value: "https://mirrors.huaweicloud.com/jenkins/update-center.json"
    - name: JENKINS_UC_DOWNLOAD
      value: "https://mirrors.huaweicloud.com/jenkins"
    - name: JENKINS_UC_EXPERIMENTAL
      value: "https://mirrors.huaweicloud.com/jenkins/updates/experimental/update-center.json"
    - name: JENKINS_INCREMENTALS_REPO_MIRROR
      value: "https://mirrors.huaweicloud.com/jenkins/incrementals"
```

安装和卸载命令
```shell
helm install jenkins jenkins/jenkins -n jenkins -f jenkins-values.yaml

helm uninstall jenkins -n jenkins
# 确认statefulset已经消失
kubectl get statefulset -n jenkins
kubectl delete pvc jenkins -n jenkins
# 去node1上把pvc删干净
rm -rf  /exports/k8s-pv/jenkins-jenkins-pvc-*
```

检查pod
```shell
# 实时查看pod启动日志
kubectl get pods -n jenkins -w
#排错
kubectl describe pod jenkins-0 -n jenkins
kubectl logs -f jenkins-0 -n jenkins -c init
kubectl logs -f jenkins-0 -n jenkins -c jenkins

```

## 访问Jenkins

获取 Jenkins 管理员密码。
```shell
# 方式1 chart自带命令
kubectl exec --namespace jenkins -it svc/jenkins -c jenkins -- /bin/cat /run/secrets/additional/chart-admin-password && echo

# 方式2 直接读取secret
kubectl get secret jenkins -n jenkins -o jsonpath="{.data.chart-admin-password}" | base64 -d && echo
```

之前安装的时候也有配置管理员密码。

获取 Jenkins 访问地址
```shell
kubectl get svc jenkins -n jenkins
```

输出
```shell
NAME      TYPE       CLUSTER-IP     EXTERNAL-IP   PORT(S)          AGE
jenkins   NodePort   10.111.61.93   <none>        8080:31434/TCP   23h
```

三个节点的IP，任意一个都可以，端口必须是上面命令查到的端口
`http://192.168.133.129:31434`

## 安装插件

1、首先需要配置华为云的UC地址
```text
https://mirrors.huaweicloud.com/jenkins/update-center.json
```

2、接着需要设置代理，都是图形界面，不多阐述。

具体要装哪些插件，听豆包的吧。

##  配置Jenkins

### 网页配置
- 添加云
- 添加pod模板
- 添加容器：jnlp和maven

具体怎么添加，听豆包的吧。

### 创建 harbor 凭证
K8s 集群执行命令
```shell
kubectl create secret docker-registry jenkins-harbor-secret \
-n jenkins \
--docker-server=192.168.133.129:30002 \
--docker-username=admin \
--docker-password=Admin@123456
```

### 安装pipeline插件

名字就叫pipeline

## 创建一条测试流水线，验证整套环境
新建任务 → 流水线，Pipeline 脚本，完整示例（适配 JDK17+Kaniko 推送到 Harbor）
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

  stages {
    stage('拉取代码') {
      steps {
        git url: 'https://github.com/quiterr/cloud-alibaba-demo.git'
      }
    }
    stage('Maven编译打包') {
      steps {
        container('maven') {
          sh 'mvn clean package -DskipTests'
        }
      }
    }
    stage('Kaniko构建镜像推送Harbor') {
      steps {
        container('kaniko') {
          sh '''
            /kaniko/executor \
            --context=`pwd` \
            --dockerfile=`pwd`/Dockerfile \
            --destination=192.168.133.129:30002/spring-cloud-demo/gateway:v1 \
            --insecure
          '''
        }
      }
    }
  }
}

```

### 拉取镜像

流水线运行起来后卡住：
```shell
Started by user unknown or anonymous
[Pipeline] Start of Pipeline
[Pipeline] podTemplate
[Pipeline] {
[Pipeline] node
Created Pod: k8s jenkins/spring-cloud-demo-1-153sq-wmrgb-5gfld
[PodInfo] jenkins/spring-cloud-demo-1-153sq-wmrgb-5gfld
	Container [jnlp] waiting [ContainerCreating] No message
	Container [kaniko] waiting [ContainerCreating] No message
	Container [maven] waiting [ContainerCreating] No message
	Pod [Pending][ContainersNotReady] containers with unready status: [jnlp maven kaniko]
[PodInfo] jenkins/spring-cloud-demo-1-153sq-wmrgb-5gfld
	Container [jnlp] waiting [ContainerCreating] No message
	Container [kaniko] waiting [ContainerCreating] No message
	Container [maven] waiting [ContainerCreating] No message
	Pod [Pending][ContainersNotReady] containers with unready status: [jnlp maven kaniko]
Still waiting to schedule task
‘spring-cloud-demo-1-153sq-wmrgb-5gfld’ is offline
```

看一下这个pod在干什么
```shell
kubectl describe pod spring-cloud-demo-1-153sq-wmrgb-5gfld -n jenkins
```

没啥问题，就是拉取镜像慢了点。后续拉取成功了
```shell
Events:
  Type    Reason     Age    From               Message
  ----    ------     ----   ----               -------
  Normal  Scheduled  21m    default-scheduler  Successfully assigned jenkins/spring-cloud-demo-1-153sq-wmrgb-5gfld to k8s-node3
  Normal  Pulling    21m    kubelet            Pulling image "jenkins/inbound-agent:jdk17"
  Normal  Pulled     10m    kubelet            Successfully pulled image "jenkins/inbound-agent:jdk17" in 10m50.186s (10m50.187s including waiting)
  Normal  Created    10m    kubelet            Created container jnlp
  Normal  Started    10m    kubelet            Started container jnlp
  Normal  Pulling    10m    kubelet            Pulling image "maven:3.9.8-eclipse-temurin-17"
  Normal  Pulled     9m13s  kubelet            Successfully pulled image "maven:3.9.8-eclipse-temurin-17" in 1m1.717s (1m1.901s including waiting)
  Normal  Created    9m12s  kubelet            Created container maven
  Normal  Started    9m12s  kubelet            Started container maven
  Normal  Pulling    9m12s  kubelet            Pulling image "gcr.io/kaniko-project/executor:v1.22.0-debug"
  Normal  Pulled     8m59s  kubelet            Successfully pulled image "gcr.io/kaniko-project/executor:v1.22.0-debug" in 13.733s (13.733s including waiting)
  Normal  Created    8m59s  kubelet            Created container kaniko
  Normal  Started    8m59s  kubelet            Started container kaniko
[root@k8s-node1 ~]# kubectl get pod spring-cloud-demo-1-153sq-wmrgb-5gfld -n jenkins
NAME                                    READY   STATUS    RESTARTS   AGE
spring-cloud-demo-1-153sq-wmrgb-5gfld   3/3     Running   0          22m

```

### 解决agent和Jenkins的jdk版本不一致问题
Jenkins 的 console output 一直在打印这个
```shell
[PodInfo] jenkins/spring-cloud-demo-1-153sq-wmrgb-5gfld
	Container [jnlp] waiting [ContainerCreating] No message
	Container [kaniko] waiting [ContainerCreating] No message
	Container [maven] waiting [ContainerCreating] No message
	Pod [Pending][ContainersNotReady] containers with unready status: [jnlp maven kaniko]
```

按照豆包的指导，查看jnlp的日志，在刷屏报错
```shell
# 执行命令
kubectl logs spring-cloud-demo-1-153sq-wmrgb-5gfld -c jnlp -n jenkins

# 输出内容
WARNING: LinkageError while performing UserRequest:hudson.slaves.SlaveComputer$SlaveVersion@2f27b54
java.lang.UnsupportedClassVersionError: Failed to load hudson.slaves.SlaveComputer$SlaveVersion
        at hudson.remoting.RemoteClassLoader.loadClassFile(RemoteClassLoader.java:472)
        at hudson.remoting.RemoteClassLoader.loadRemoteClass(RemoteClassLoader.java:301)
        at hudson.remoting.RemoteClassLoader.loadWithMultiClassLoader(RemoteClassLoader.java:277)
        at hudson.remoting.RemoteClassLoader.findClass(RemoteClassLoader.java:236)
        at java.base/java.lang.ClassLoader.loadClass(Unknown Source)
        at java.base/java.lang.ClassLoader.loadClass(Unknown Source)
        at java.base/java.lang.Class.forName0(Native Method)
        at java.base/java.lang.Class.forName(Unknown Source)
        at hudson.remoting.MultiClassLoaderSerializer$Input.resolveClass(MultiClassLoaderSerializer.java:133)
        at java.base/java.io.ObjectInputStream.readNonProxyDesc(Unknown Source)
        at java.base/java.io.ObjectInputStream.readClassDesc(Unknown Source)
        at java.base/java.io.ObjectInputStream.readOrdinaryObject(Unknown Source)
        at java.base/java.io.ObjectInputStream.readObject0(Unknown Source)
        at java.base/java.io.ObjectInputStream.readObject(Unknown Source)
        at java.base/java.io.ObjectInputStream.readObject(Unknown Source)
        at hudson.remoting.UserRequest.deserialize(UserRequest.java:312)
        at hudson.remoting.UserRequest.perform(UserRequest.java:196)
        at hudson.remoting.UserRequest.perform(UserRequest.java:50)
        at hudson.remoting.Request$2.run(Request.java:391)
        at hudson.remoting.InterceptingExecutorService.lambda$wrap$0(InterceptingExecutorService.java:81)
        at java.base/java.util.concurrent.FutureTask.run(Unknown Source)
        at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(Unknown Source)
        at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(Unknown Source)
        at hudson.remoting.Engine$1.lambda$newThread$0(Engine.java:312)
        at java.base/java.lang.Thread.run(Unknown Source)
Caused by: java.lang.UnsupportedClassVersionError: hudson/slaves/SlaveComputer$SlaveVersion has been compiled by a more recent version of the Java Runtime (class file version 65.0), this version of the Java Runtime only recognizes class file versions up to 61.0
        at java.base/java.lang.ClassLoader.defineClass1(Native Method)
        at java.base/java.lang.ClassLoader.defineClass(Unknown Source)
        at java.base/java.lang.ClassLoader.defineClass(Unknown Source)
        at hudson.remoting.RemoteClassLoader.loadClassFile(RemoteClassLoader.java:470)
        ... 24 more

```

核心是这一行日志。Jenkins Master 主容器使用的是 JDK21，但是 jnlp agent 镜像用的是 `jenkins/inbound‑agent:jdk17`。
Master 用 Java21 编译出来的类，下发给只支持 Java17 的 agent，agent 无法加载类，WebSocket 连接反复建立、立刻崩溃断开，循环重连。
- `class file version 65.0` → **Java‑21**编译的 class
- `class file version 61.0` → **Java‑17**虚拟机
```shell
Caused by: java.lang.UnsupportedClassVersionError: hudson/slaves/SlaveComputer$SlaveVersion has been compiled by a more recent version of the Java Runtime (class file version 65.0), this version of the Java Runtime only recognizes class file versions up to 61.0
```

解决方案就是修改groovy脚本，把jnlp版本改成21。停止当前构建，重新构建。

### 解决git插件未安装的问题
这次报了新的错误：
```shell

Running on spring-cloud-demo-2-1zp88-8tgnp-zbxtt in /home/jenkins/agent/workspace/spring‑cloud‑demo
[Pipeline] {
[Pipeline] stage
[Pipeline] { (拉取代码)
[Pipeline] }
[Pipeline] // stage
[Pipeline] stage
[Pipeline] { (Maven编译打包)
Stage "Maven编译打包" skipped due to earlier failure(s)
[Pipeline] getContext
[Pipeline] }
[Pipeline] // stage
[Pipeline] stage
[Pipeline] { (Kaniko构建镜像推送Harbor)
Stage "Kaniko构建镜像推送Harbor" skipped due to earlier failure(s)
[Pipeline] getContext
[Pipeline] }
[Pipeline] // stage
[Pipeline] }
[Pipeline] // node
[Pipeline] }
[Pipeline] // podTemplate
[Pipeline] End of Pipeline
Also:   org.jenkinsci.plugins.workflow.actions.ErrorAction$ErrorId: 58992385-c093-4e0d-8147-b2e9cdb32938
java.lang.NoSuchMethodError: No such DSL method 'git' found among steps [archive, bat, build, catchError, checkout, container, containerLog, deleteDir, dir, echo, envVarsForTool, error, fileExists, getContext, input, isUnix, library, libraryResource, load, mail, milestone, node, parallel, podTemplate, powershell, properties, pwd, pwsh, readFile, readScmFile, readTrusted, resolveScm, retry, script, sh, sleep, stage, stash, step, timeout, tool, unarchive, unstable, unstash, validateDeclarativePipeline, waitForBuild, waitUntil, warnError, withContext, withCredentials, withEnv, wrap, writeFile, ws] or symbols [agent, all, allBranchesSame, allOf, always, any, anyOf, apiToken, architecture, archiveArtifacts, artifactManager, batchFile, booleanParam, branch, buildButton, buildDiscarder, buildDiscarders, buildRetention, buildingTag, caseInsensitive, caseSensitive, certificate, changeRequest, changelog, changeset, checkoutToSubdirectory, choice, choiceParam, clock, command, computerRetentionCheckInterval, configMapVolume, consoleUrlProvider, containerEnvVar, containerLivenessProbe, containerTemplate, contentSecurityPolicy, cps, credentials, cron, crumb, default, defaultDisplayUrlProvider, defaultFolderConfiguration, defaultView, demand, disableConcurrentBuilds, disableRestartFromStage, disableResume, diskSpace, diskSpaceMonitor, downstream, dumb, durabilityHint, dynamicPVC, emptyDirVolume, emptyDirWorkspaceVolume, envVar, envVars, envVarsFilter, environment, equals, evicted, experimentalFlags, expression, file, fileParam, filePath, fingerprint, fingerprints, frameOptions, freeStyle, freeStyleJob, fromScm, fromSource, genericEphemeralVolume, headRegexFilter, headWildcardFilter, hostPathVolume, hostPathWorkspaceVolume, hyperlink, hyperlinkToModels, inbound, installSource, isRestartedRun, jdk, jnlp, jobBuildDiscarder, jobName, kubeconfig, kubernetes, kubernetesAgent, label, lastDuration, lastFailure, lastGrantedAuthorities, lastStable, lastSuccess, legacy, legacySCM, list, local, location, logRotator, loggedInUsersCanDoAnything, mailer, maven, maven3Mojos, mavenErrors, mavenGlobalConfig, mavenMojos, mavenWarnings, merge, modernSCM, multiBranchProjectDisplayNaming, multibranch, myView, namedBranchesDifferent, never, nfsVolume, nfsWorkspaceVolume, node, nodeProperties, none, nonresumable, not, onFailure, organizationFolder, override, overrideIndexTriggers, paneStatus, parallelsAlwaysFailFast, parameters, password, pattern, permanent, persistentVolumeClaim, persistentVolumeClaimWorkspaceVolume, pipelineTriggers, plainText, plugin, podAnnotation, podEnvVar, podLabel, pollSCM, portMapping, preserveStashes, projectNamingStrategy, proxy, queueItemAuthenticator, quietPeriod, rateLimit, rateLimitBuilds, resourceRoot, responseTime, retainOnlyVariables, run, runParam, schedule, scmRetryCount, scriptApproval, scriptApprovalLink, search, secretEnvVar, secretVolume, security, shell, simpleBuildDiscarder, skipDefaultCheckout, skipStagesAfterUnstable, slave, sourceRegexFilter, sourceWildcardFilter, sshUserPrivateKey, standard, status, string, stringParam, suppressAutomaticTriggering, suppressFolderAutomaticTriggering, swapSpace, tag, text, textParam, timezone, tmpSpace, toolLocation, triggeredBy, unsecured, untrusted, upstream, userSeed, usernameColonPassword, usernamePassword, viewsTabBar, weather, zip] or globals [currentBuild, env, params, pipeline, scm]
	at PluginClassLoader for workflow-cps//org.jenkinsci.plugins.workflow.cps.DSL.invokeMethod(DSL.java:223)
	at PluginClassLoader for workflow-cps//org.jenkinsci.plugins.workflow.cps.CpsScript.invokeMethod(CpsScript.java:124)
	at java.base/jdk.internal.reflect.DirectMethodHandleAccessor.invoke(Unknown Source)
	at java.base/java.lang.reflect.Method.invoke(Unknown Source)
	at org.codehaus.groovy.reflection.CachedMethod.invoke(CachedMethod.java:98)
	at groovy.lang.MetaMethod.doMethodInvoke(MetaMethod.java:325)
	at groovy.lang.MetaClassImpl.invokeMethod(MetaClassImpl.java:1225)
	at groovy.lang.MetaClassImpl.invokeMethod(MetaClassImpl.java:1034)
	at org.codehaus.groovy.runtime.callsite.PogoMetaClassSite.call(PogoMetaClassSite.java:41)
	at org.codehaus.groovy.runtime.callsite.CallSiteArray.defaultCall(CallSiteArray.java:47)
	at org.codehaus.groovy.runtime.callsite.AbstractCallSite.call(AbstractCallSite.java:116)
	at PluginClassLoader for script-security//org.kohsuke.groovy.sandbox.impl.Checker$1.call(Checker.java:180)
	at PluginClassLoader for script-security//org.kohsuke.groovy.sandbox.GroovyInterceptor.onMethodCall(GroovyInterceptor.java:23)
	at PluginClassLoader for script-security//org.jenkinsci.plugins.scriptsecurity.sandbox.groovy.SandboxInterceptor.onMethodCall(SandboxInterceptor.java:163)
	at PluginClassLoader for script-security//org.kohsuke.groovy.sandbox.impl.Checker$1.call(Checker.java:178)
	at PluginClassLoader for script-security//org.kohsuke.groovy.sandbox.impl.Checker.checkedCall(Checker.java:182)
	at PluginClassLoader for script-security//org.kohsuke.groovy.sandbox.impl.Checker.checkedCall(Checker.java:152)
	at PluginClassLoader for workflow-cps//com.cloudbees.groovy.cps.sandbox.SandboxInvoker.methodCall(SandboxInvoker.java:17)
	at PluginClassLoader for workflow-cps//org.jenkinsci.plugins.workflow.cps.LoggingInvoker.methodCall(LoggingInvoker.java:124)
	at WorkflowScript.run(WorkflowScript:37)
	at org.jenkinsci.plugins.pipeline.modeldefinition.ModelInterpreter.delegateAndExecute(ModelInterpreter.groovy:139)
	at org.jenkinsci.plugins.pipeline.modeldefinition.ModelInterpreter.executeSingleStage(ModelInterpreter.groovy:633)
	at org.jenkinsci.plugins.pipeline.modeldefinition.ModelInterpreter.catchRequiredContextForNode(ModelInterpreter.groovy:390)
	at org.jenkinsci.plugins.pipeline.modeldefinition.ModelInterpreter.executeSingleStage(ModelInterpreter.groovy:632)
	at org.jenkinsci.plugins.pipeline.modeldefinition.ModelInterpreter.evaluateStage(ModelInterpreter.groovy:292)
	at org.jenkinsci.plugins.pipeline.modeldefinition.ModelInterpreter.toolsBlock(ModelInterpreter.groovy:521)
	at org.jenkinsci.plugins.pipeline.modeldefinition.ModelInterpreter.evaluateStage(ModelInterpreter.groovy:280)
	at org.jenkinsci.plugins.pipeline.modeldefinition.ModelInterpreter.withEnvBlock(ModelInterpreter.groovy:432)
	at org.jenkinsci.plugins.pipeline.modeldefinition.ModelInterpreter.evaluateStage(ModelInterpreter.groovy:279)
	at org.jenkinsci.plugins.pipeline.modeldefinition.ModelInterpreter.withCredentialsBlock(ModelInterpreter.groovy:464)
	at org.jenkinsci.plugins.pipeline.modeldefinition.ModelInterpreter.evaluateStage(ModelInterpreter.groovy:278)
	at org.jenkinsci.plugins.pipeline.modeldefinition.ModelInterpreter.inDeclarativeAgent(ModelInterpreter.groovy:561)
	at org.jenkinsci.plugins.pipeline.modeldefinition.ModelInterpreter.evaluateStage(ModelInterpreter.groovy:276)
	at org.jenkinsci.plugins.pipeline.modeldefinition.ModelInterpreter.stageInput(ModelInterpreter.groovy:354)
	at org.jenkinsci.plugins.pipeline.modeldefinition.ModelInterpreter.evaluateStage(ModelInterpreter.groovy:265)
	at org.jenkinsci.plugins.pipeline.modeldefinition.ModelInterpreter.inWrappers(ModelInterpreter.groovy:592)
	at org.jenkinsci.plugins.pipeline.modeldefinition.ModelInterpreter.evaluateStage(ModelInterpreter.groovy:263)
	at org.jenkinsci.plugins.pipeline.modeldefinition.ModelInterpreter.withEnvBlock(ModelInterpreter.groovy:432)
	at org.jenkinsci.plugins.pipeline.modeldefinition.ModelInterpreter.evaluateStage(ModelInterpreter.groovy:258)
	at ___cps.transform___(Native Method)
	at PluginClassLoader for workflow-cps//com.cloudbees.groovy.cps.impl.ContinuationGroup.methodCall(ContinuationGroup.java:107)
	at PluginClassLoader for workflow-cps//com.cloudbees.groovy.cps.impl.FunctionCallBlock$ContinuationImpl.dispatchOrArg(FunctionCallBlock.java:118)
	at PluginClassLoader for workflow-cps//com.cloudbees.groovy.cps.impl.FunctionCallBlock$ContinuationImpl.fixArg(FunctionCallBlock.java:87)
	at java.base/jdk.internal.reflect.DirectMethodHandleAccessor.invoke(Unknown Source)
	at java.base/java.lang.reflect.Method.invoke(Unknown Source)
	at PluginClassLoader for workflow-cps//com.cloudbees.groovy.cps.impl.ContinuationPtr$ContinuationImpl.receive(ContinuationPtr.java:71)
	at PluginClassLoader for workflow-cps//com.cloudbees.groovy.cps.impl.CollectionLiteralBlock$ContinuationImpl.dispatch(CollectionLiteralBlock.java:54)
	at PluginClassLoader for workflow-cps//com.cloudbees.groovy.cps.impl.CollectionLiteralBlock$ContinuationImpl.item(CollectionLiteralBlock.java:45)
	at java.base/jdk.internal.reflect.DirectMethodHandleAccessor.invoke(Unknown Source)
	at java.base/java.lang.reflect.Method.invoke(Unknown Source)
	at PluginClassLoader for workflow-cps//com.cloudbees.groovy.cps.impl.ContinuationPtr$ContinuationImpl.receive(ContinuationPtr.java:71)
	at PluginClassLoader for workflow-cps//com.cloudbees.groovy.cps.impl.ConstantBlock.eval(ConstantBlock.java:21)
	at PluginClassLoader for workflow-cps//com.cloudbees.groovy.cps.Next.step(Next.java:84)
	at PluginClassLoader for workflow-cps//com.cloudbees.groovy.cps.Continuable.run0(Continuable.java:142)
	at PluginClassLoader for workflow-cps//org.jenkinsci.plugins.workflow.cps.SandboxContinuable.access$001(SandboxContinuable.java:17)
	at PluginClassLoader for workflow-cps//org.jenkinsci.plugins.workflow.cps.SandboxContinuable.run0(SandboxContinuable.java:48)
	at PluginClassLoader for workflow-cps//org.jenkinsci.plugins.workflow.cps.CpsThread.runNextChunk(CpsThread.java:188)
	at PluginClassLoader for workflow-cps//org.jenkinsci.plugins.workflow.cps.CpsThreadGroup.run(CpsThreadGroup.java:464)
	at PluginClassLoader for workflow-cps//org.jenkinsci.plugins.workflow.cps.CpsThreadGroup$2.call(CpsThreadGroup.java:372)
	at PluginClassLoader for workflow-cps//org.jenkinsci.plugins.workflow.cps.CpsThreadGroup$2.call(CpsThreadGroup.java:302)
	at PluginClassLoader for workflow-cps//org.jenkinsci.plugins.workflow.cps.CpsVmExecutorService.lambda$wrap$4(CpsVmExecutorService.java:143)
	at java.base/java.util.concurrent.FutureTask.run(Unknown Source)
	at hudson.remoting.SingleLaneExecutorService$1.run(SingleLaneExecutorService.java:139)
	at jenkins.util.ContextResettingExecutorService.lambda$wrap$0(ContextResettingExecutorService.java:26)
	at jenkins.security.ImpersonatingExecutorService.lambda$wrap$0(ImpersonatingExecutorService.java:66)
	at jenkins.util.ErrorLoggingExecutorService.lambda$wrap$0(ErrorLoggingExecutorService.java:51)
	at java.base/java.util.concurrent.Executors$RunnableAdapter.call(Unknown Source)
	at java.base/java.util.concurrent.FutureTask.run(Unknown Source)
	at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(Unknown Source)
	at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(Unknown Source)
	at PluginClassLoader for workflow-cps//org.jenkinsci.plugins.workflow.cps.CpsVmExecutorService$1.call(CpsVmExecutorService.java:53)
	at PluginClassLoader for workflow-cps//org.jenkinsci.plugins.workflow.cps.CpsVmExecutorService$1.call(CpsVmExecutorService.java:50)
	at org.codehaus.groovy.runtime.GroovyCategorySupport$ThreadCategoryInfo.use(GroovyCategorySupport.java:136)
	at org.codehaus.groovy.runtime.GroovyCategorySupport.use(GroovyCategorySupport.java:275)
	at PluginClassLoader for workflow-cps//org.jenkinsci.plugins.workflow.cps.CpsVmExecutorService.lambda$categoryThreadFactory$0(CpsVmExecutorService.java:50)
	at java.base/java.lang.Thread.run(Unknown Source)
Finished: FAILURE
```

核心报错日志是这一行：`java.lang.NoSuchMethodError: No such DSL method 'git' found among steps`。
**安装git 和 git client两个插件之后就好了**

### 解决访问github权限问题
接着又报没有权限拉取github代码
```shell
stderr: remote: Invalid username or token. Password authentication is not supported for Git operations.
fatal: Authentication failed for 'https://github.com/quiterr/cloud-alibaba-demo.git/'
```

- github 网页 → Settings -> Credentials -> Personal access tokens（classic），勾选repo。
生成的token复制下来：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。
- 在Jenkins网页，设置->安全->Credentials->添加Credentials，选Username with password，用户名填github不带
邮箱的用户名，密码填刚刚复制的token。ID填github_token。
- 在流水线groovy脚本，设置ID为github_token。
```groovy
        git url: 'https://github.com/quiterr/cloud-alibaba-demo.git',
            credentialsId: 'github_token'
```

### 解决镜像生成问题

Kaniko 在工作目录 `/home/jenkins/agent/workspace/spring‑cloud‑demo` **找不到 Dockerfile 文件**。
```shell
+ /kaniko/executor '--context=/home/jenkins/agent/workspace/spring‑cloud‑demo' '--dockerfile=/home/jenkins/agent/workspace/spring‑cloud‑demo/Dockerfile' '--destination=192.168.133.129:30002/spring-cloud-demo/gateway:v1' --insecure
Error: error resolving dockerfile path: please provide a valid path to a Dockerfile within the build context with --dockerfile
```

修改 kaniko 执行命令：
```groovy
    stage('Kaniko构建镜像推送Harbor') {
    steps {
        container('kaniko') {
            sh '''
            /kaniko/executor \
            --context=`pwd`/gateway-server \
            --dockerfile=`pwd`/gateway-server/Dockerfile \
            --destination=192.168.133.129:30002/spring_cloud_demo/gateway:v1 \
            --insecure
          '''
        }
    }
}
```

### 解决镜像推送harbor问题（含重点问题）

项目名不存在，不是短横线-，是下划线_
```shell
error checking push permissions -- make sure you entered the correct tag name, and that you are authenticated correctly, and try again: checking push permission for "192.168.133.129:30002/spring-cloud-demo/gateway:v1": POST http://192.168.133.129:30002/v2/spring-cloud-demo/gateway/blobs/uploads/: UNAUTHORIZED: project spring-cloud-demo not found: project spring-cloud-demo not found
```

修改项目名为spring_cloud_demo：
```groovy
    stage('Kaniko构建镜像推送Harbor') {
    steps {
        container('kaniko') {
            sh '''
            /kaniko/executor \
            --context=`pwd`/gateway-server \
            --dockerfile=`pwd`/gateway-server/Dockerfile \
            --destination=192.168.133.129:30002/spring_cloud_demo/gateway:v1 \
            --insecure
          '''
        }
    }
}
```

**权限不足（重点问题）**
```shell
error checking push permissions -- make sure you entered the correct tag name, and that you are authenticated correctly, and try again: checking push permission for "192.168.133.129:30002/spring_cloud_demo/gateway:v1": POST http://192.168.133.129:30002/v2/spring_cloud_demo/gateway/blobs/uploads/: UNAUTHORIZED: unauthorized to access repository: spring_cloud_demo/gateway, action: push: unauthorized to access repository: spring_cloud_demo/gateway, action: push
```

构建几秒就失败了，来不及进入容器内部检查配置，可以增加一个sleep
```groovy
    stage('Kaniko构建镜像推送Harbor') {
      steps {
        container('kaniko') {
          sh '''
	      echo "睡眠120秒，抓紧时间进来检查config.json文件"
          sleep 120
            /kaniko/executor \
            --context=`pwd`/gateway-server \
            --dockerfile=`pwd`/gateway-server/Dockerfile \
            --destination=192.168.133.129:30002/spring_cloud_demo/gateway:v1 \
            --insecure \
            --skip-tls-verify \
            --verbosity=debug
          '''
        }
      }
    }
```

排查secret是否挂载正确
```shell
# 交互式检查
kubectl get pods -n jenkins | grep spring-cloud-demo
kubectl exec -it spring-cloud-demo-11-jz0kb-5hmm2-npp3k -c kaniko -n jenkins -- sh
# 这条命令输出no such file
cat /kaniko/.docker/config.json
# 这条命令输出了正确值
cat /kaniko/.docker/.dockerconfigjson
```

**根因完整复盘**
1. secret 已经成功挂载到 `/kaniko/.docker/`
2. 挂载出来真实文件：`/kaniko/.docker/.dockerconfigjson`（带前置点）
3. kaniko 只认：`/kaniko/.docker/config.json`（不带前置点）

**解决方案**

不使用 `kubernetes.io/dockerconfigjson` secret 类型。改用普通`Opaque Secret`，key 名字手动指定为`config.json`。

```shell
kubectl delete secret jenkins-harbor-secret -n jenkins

# 使用刚才.dockerconfigjson中的auth值
{"auths":{"192.168.133.129:30002":{"username":"admin","password":"Admin@123456","auth":"YWRtaW46QWRtaW5AMTIzNDU2"}}}

# 或者用命令生成auth值：`echo -n "admin:Admin@123456" | base64`

# 新建 Opaque 类型 secret，key 名字强制叫`config.json`
kubectl create secret generic jenkins-harbor-secret \
-n jenkins \
--from-literal=config.json='{"auths":{"192.168.133.129:30002":{"auth":"YWRtaW46QWRtaW5AMTIzNDU2"}}}'

```

### 解决kaniko拉取镜像的错误
```shell
error building image: unable to complete operation after 0 attempts, last error: Get "https://index.docker.io/v2/": dial tcp 199.16.156.39:443: connect: connection refused
```

在流水线groovy脚本中给kaniko设置代理
```groovy
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
--dockerfile=`pwd`/gateway-server/Dockerfile \
--destination=192.168.133.129:30002/spring_cloud_demo/gateway:v1 \
--insecure \
--skip-tls-verify \
--verbosity=debug
'''
```

## 创建 PVC 持久化 Maven 本地仓库
每次构建都重新下载依赖，非常慢。

1. 新建 pvc `maven-repo-pvc.yaml`
```yaml
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: maven-repo-pvc
  namespace: jenkins
spec:
  accessModes:
    - ReadWriteOnce
  resources:
    requests:
      storage: 10Gi
```

```shell
kubectl apply -f maven-repo-pvc.yaml
```

2. 修改流水线 podTemplate，增加 volume 和 volumeMounts，把 pvc 挂载给 maven 容器
```groovy
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
    image: registry.cn-hangzhou.aliyuncs.com/kaniko-project/executor:v1.22.0-debug
    command: ['cat']
    tty: true
    volumeMounts:
    - name: harbor-auth
      mountPath: /kaniko/.docker
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

```

## 自动化部署

### 权限准备
Jenkins slave Pod 默认用`default` sa，权限不足，需要创建 RBAC：
```yaml
# jenkins-deploy-rbac.yaml
apiVersion: rbac.authorization.k8s.io/v1
kind: Role
metadata:
  namespace: default
  name: jenkins-deploy-role
rules:
  # 管理deployment：创建/查询/更新/删除
  - apiGroups: ["apps"]
    resources: ["deployments"]
    verbs: ["get", "list", "create", "update", "patch", "delete"]
  # 管理service：创建/查询/更新/删除
  - apiGroups: [""]
    resources: ["services"]
    verbs: ["get", "list", "create", "update", "patch", "delete"]
  # 可选：查看pod、rollout状态（流水线等待滚动更新需要）
  - apiGroups: [""]
    resources: ["pods"]
    verbs: ["get","list"]
  # 可选：rollout status 底层需要访问replicasets
  - apiGroups: ["apps"]
    resources: ["replicasets"]
    verbs: ["get", "list"]
---
apiVersion: rbac.authorization.k8s.io/v1
kind: RoleBinding
metadata:
  name: jenkins-deploy-rb
  namespace: default
subjects:
  - kind: ServiceAccount
    name: default
    namespace: jenkins
roleRef:
  kind: Role
  name: jenkins-deploy-role
  apiGroup: rbac.authorization.k8s.io
```

```shell
# 先删除旧的role&rolebinding
kubectl delete role jenkins-deploy-role -n default
kubectl delete rolebinding jenkins-deploy-rb -n default

# 应用新权限
kubectl apply -f rbac-jenkins-deploy.yaml
```

### 流水线补充部署代码

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
  # 新增kubectl容器
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
        // 和你kaniko推送保持一致镜像地址
        IMAGE = "192.168.133.129:30002/spring_cloud_demo/gateway:v1"
        DEPLOY_NS = "default"
        DEPLOY_NAME = "gateway"
    }

  stages {
    stage('拉取代码') {
      steps {
        git url: 'https://github.com/quiterr/cloud-alibaba-demo.git'
      }
    }
    stage('Maven编译打包') {
      steps {
        container('maven') {
          sh 'mvn clean package -DskipTests'
        }
      }
    }
    stage('Kaniko构建镜像推送Harbor') {
      steps {
        container('kaniko') {
          sh '''
            /kaniko/executor \
            --context=`pwd` \
            --dockerfile=`pwd`/Dockerfile \
            --destination=${IMAGE} \
            --insecure
          '''
        }
      }
    }
      // ==========新增自动部署阶段============
      stage('部署到K8s') {
          steps {
              container('kubectl') {
                  sh """
            /usr/bin/kubectl set image deployment/${DEPLOY_NAME} ${DEPLOY_NAME}=${IMAGE} -n ${DEPLOY_NS}
            /usr/bin/kubectl rollout status deployment/${DEPLOY_NAME} -n ${DEPLOY_NS} --timeout=300s
          """
              }
          }
      }
  }
}
```

### kubectl镜像拉取问题（重点问题）

上一章节最开始kubectl镜像用的`bitnami/kubectl:1.28.0`，拉取的时候报not found，无论怎么改版本号都是一样，bitnami的镜像根本拉取不下来。

最后没办法，用了`alpine/k8s:1.28.0`镜像。

### gateway的部署yaml

如果不创建gateway的Deployment，Jenkins流水线会报错
```shell
error from server (NotFound): deployments.apps "gateway" not found
```

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

```

```shell
kubectl apply -f gateway-deploy.yaml
```

## 微服务部署后查看日志
```shell
# 查看pod是否正常运行
kubectl get pods
# 查看pod的事件
kubectl describe pod harbor-nginx-86458b74c6-vdb25
# 直接看日志，-f 实时跟踪输出（类似tail -f）
kubectl logs -f deployment/gateway -n default
# 或者用pod名字
kubectl logs -f gateway-6c4997c6d5-6ppzv -n default
# Pod 发生过重启、崩溃退出，看上一次退出前的日志
kubectl logs --previous deployment/gateway -n default
# 进入容器内部交互调试，进入后可以直接看日志文件
kubectl exec -it deployment/gateway -n default -- sh
# 或者pod名称
kubectl exec -it gateway-6c4997c6d5-6ppzv -n default -- sh
# 日志保存到本地文件
kubectl logs deployment/gateway -n default > gateway.log
```

查看应用启动参数
```shell
cat /proc/1/cmdline
```


## 遇到的其他错误

第一次创建流水线的时候报错
```text
HTTP ERROR 403 No valid crumb was included in the request
```

`No valid crumb` 是新版 Jenkins 的 **CSRF 跨站防护校验失败**，NodePort 访问经常出现这个保存 403 问题。

解决办法，禁用CSRF保护
```shell
kubectl patch statefulset jenkins -n jenkins -p '{"spec":{"template":{"spec":{"containers":[{"name":"jenkins","env":[{"name":"JAVA_OPTS","value":"-Dcasc.reload.token=$(POD_NAME) -Dhudson.security.csrf.GlobalCrumbIssuerConfiguration.DISABLE_CSRF_PROTECTION=true "}]}]}}}}'

# 重启jenkins
kubectl rollout restart statefulset jenkins -n jenkins
kubectl get pods -n jenkins -w
```














