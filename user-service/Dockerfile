FROM 192.168.133.129:30002/spring_cloud_demo/eclipse-temurin:17-jre
WORKDIR /app
COPY target/user-service-0.0.1-SNAPSHOT.jar app.jar
# 启动jar，读取JAVA_OPTS环境变量，JAVA_OPTS由k8s yaml注入
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]