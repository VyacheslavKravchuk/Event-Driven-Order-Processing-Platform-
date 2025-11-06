# Этап сборки (builder)
FROM maven:3.8.1-openjdk-17 AS builder
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean install -DskipTests

# Этап запуска (runner)
FROM openjdk:17-jre-slim
WORKDIR /app
COPY --from=builder /app/target/api_gateway-0.0.1-SNAPSHOT.jar /app/api_gateway.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "api_gateway.jar"]