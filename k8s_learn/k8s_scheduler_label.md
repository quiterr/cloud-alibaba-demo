# 资源调度-标签和选择器

## 标签和选择器

一、核心概念

**标签 Label**

*    格式：key=value 键值对，自定义字符串，只能小写字母、数字、-_.
*    作用：给 K8s 资源（Node/Pod/Deployment/Service）打分类标记，用于筛选、分组
*    特点：
*    一个资源可以打多个标签；
*    标签仅用于筛选，不改变资源本身运行逻辑；
*    区分：metadata.labels 资源自身标签 / spec.template.metadata.labels Pod 模板标签

**选择器 Selector**

通过标签过滤资源，分两大类：
*    等值选择器 matchLabels（精准匹配） ：app=nginx 只匹配标签完全相等的资源
*    集合选择器 matchExpressions（多条件、包含 / 排除）：支持 In/NotIn/Exists/DoesNotExist，多条件组合筛选

**三种核心使用场景**

*    控制器绑定：Deployment/DaemonSet 通过spec.selector.matchLabels 绑定自身管理的 Pod
*    Service 流量转发：Service 的 spec.selector 匹配 Pod，实现负载均衡 
*    命令行批量操作 kubectl get/delete -l 标签 筛选资源

二、实操 1：给资源打标签、查看标签

**创建 Deployment 并内置标签**

deploy-label.yaml
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: nginx-web
  # Deployment自身标签
  labels:
    app: nginx
    env: test
    department: dev
spec:
  replicas: 2
  selector:
    matchLabels:
      app: nginx
  template:
    metadata:
      # 生成Pod自带标签
      labels:
        app: nginx
        env: test
    spec:
      containers:
      - name: nginx
        image: nginx:alpine
```

实操命令
```shell
kubectl apply -f deploy-label.yaml

# 查看所有pod并展示标签
kubectl get pods --show-labels
# 查看deployment标签
kubectl get deploy --show-labels
# 只筛选 env=test 的pod
kubectl get pods -l env=test

# 给deployment新增标签
kubectl label deploy nginx-web version=v1.0
# 修改已有标签（--overwrite 必须加）
kubectl label deploy nginx-web env=prod --overwrite
# 删除标签（key- 后缀）
kubectl label deploy nginx-web department-

# 给k8s-node3打上业务标签
kubectl label node k8s-node3 node-type=compute
# 筛选节点
kubectl get node -l node-type=compute
```

三、实操 2：matchLabels 等值选择器（最常用）

Service 使用 selector 匹配 Pod
```yaml
apiVersion: v1
kind: Service
metadata:
  name: nginx-svc
spec:
  type: ClusterIP
  selector:
    app: nginx  #只有标签app=nginx的Pod才会被纳入
  ports:
  - port: 80
    targetPort: 80
```

四、实操 3：matchExpressions 集合表达式选择器（多条件复杂筛选）

支持 4 种运算符：
* In：标签值在列表内
* NotIn：标签值不在列表
* Exists：资源必须存在该标签 key
* DoesNotExist：资源不能有该标签 key

示例 Deployment（多条件匹配 Pod）

deploy-match-expr.yaml
```yaml
apiVersion: apps/v1
kind: nginx-demo
apiVersion: apps/v1
kind: Deployment
metadata:
  name: expr-demo
spec:
  replicas: 2
  selector:
    matchExpressions:
      # 条件1：app标签值必须是 nginx / tomcat
      - key: app
        operator: In
        values: ["nginx","tomcat"]
      # 条件2：必须存在env标签
      - key: env
        operator: Exists
      # 条件3：version 不能是 v2.0
      - key: version
        operator: NotIn
        values: ["v2.0"]
  template:
    metadata:
      labels:
        app: nginx
        env: test
        version: v1.0
    spec:
      containers:
      - name: nginx
        image: nginx:alpine
```

```shell
kubectl apply -f deploy-match-expr.yaml
kubectl get pods --show-labels

# 同时匹配 app=nginx 且 env=prod
kubectl get pods -l app=nginx,env=prod
# app在(nginx,tomcat)内
kubectl get pods -l 'app in (nginx,tomcat)'
# 不存在version标签
kubectl get pods -l 'version notin (v1.0,v2.0)'
```

五、实操 4：批量资源管理（标签批量删除 / 扩容）

```shell
#1. 批量删除所有带 app=nginx 的资源（pod/deploy/svc）
kubectl delete all -l app=nginx

# test环境
kubectl create deploy nginx-test --image=nginx:alpine
kubectl label deploy nginx-test env=test
# prod环境
kubectl create deploy nginx-prod --image=nginx:alpine
kubectl label deploy nginx-prod env=prod

#只查看测试环境资源
kubectl get deploy,pods -l env=test
#批量删除测试环境所有资源
kubectl delete all -l env=test
```

六、标签与选择器核心易错点
* Deployment selector 是不可变字段
* 创建后不能修改 matchLabels，修改只能重建 Deployment；
* yaml 缩进错误、标签 key/value 拼写错误，直接匹配失效；
* Service selector 只匹配 Pod 标签，不匹配 Deployment 标签；
* --overwrite 修改已有标签必须携带，否则报错；
* matchLabels 和 matchExpressions 可同时写，且条件同时满足才匹配；
* 标签值空字符串合法：app=，但筛选时 app= 才能匹配。

七、生产规范

标准业务标签（行业通用）
```yaml
labels:
  app: nginx-front
  env: prod
  version: v1.2.0
  department: web-group
  tier: frontend
```

* app：应用名称
* env：环境 test/dev/prod
* version：版本号
* tier：分层 frontend/backend/db


