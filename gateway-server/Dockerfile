FROM eclipse-temurin:17-jre
VOLUME /tmp
# 暴露服务端口（网关默认9999，按需修改）
EXPOSE 9999
COPY target/*.jar app.jar
ENTRYPOINT ["java","-jar","app.jar"]