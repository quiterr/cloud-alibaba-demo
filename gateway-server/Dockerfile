FROM eclipse-temurin:17-jre
WORKDIR /app
COPY target/gateway-server-0.0.1-SNAPSHOT.jar app.jar
# JVM参数，生产推荐配置
ENTRYPOINT ["java","-XX:+UseContainerSupport","-XX:MaxRAMPercentage=70.0","-jar","app.jar"]