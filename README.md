# 说明

本项目包括gateway服务、order服务和user服务，order服务通过feign调用user服务。

nacos需要自行准备，并修改服务的nacos IP配置，否则服务不能正常运行。

k8s、Jenkins、harbor等环境都需要自行准备，否则不能实现持续部署。

order服务包含测试shiro，只有加了白名单的url才能允许访问。order还包含powerjob的测试，需要powerjob服务端环境才能正常运行。

下面这篇笔记，记录了项目如何通过Jenkins流水线自动部署到k8s，主要展示思路。
[production.md](k8s_learn/devops/production.md)

由于经常忘记更新笔记，建议还是以项目中的实际配置文件为准。

项目目录大致如下：
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
│   ├── base
│   │   ├── kustomization.yaml              
│   │   ├── gateway.yaml
│   │   ├── user-service.yaml
│   │   └── order-service.yaml
│   └── overlays
│       └── dev           
│           └── .gitkeep
├── Jenkinsfile           
├── pom.xml
└── README.md
```