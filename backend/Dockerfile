# ---------- 构建阶段 ----------
# 用 JDK 17 编译（pom 里 release=17），与运行阶段版本保持一致
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# 先只拷 pom，让依赖层可缓存：改代码不会重新拉依赖
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q clean package -DskipTests

# ---------- 运行阶段 ----------
FROM eclipse-temurin:17-jre

WORKDIR /app
ENV TZ=Asia/Shanghai \
    JAVA_OPTS="-Xms256m -Xmx512m"

# 本地存储目录（挂卷持久化）
RUN mkdir -p /app/data/upload

COPY --from=build /app/target/campusfix.jar /app/app.jar

EXPOSE 8080

# 端口用环境变量覆盖，便于 compose 或其他编排调整
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar --server.port=${SERVER_PORT:-8080}"]
