# syntax=docker/dockerfile:1.6
FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /workspace

COPY . .
RUN --mount=type=cache,target=/root/.m2,sharing=locked cd eureka-server && mvn -B -U -DskipTests -Dmaven.wagon.http.retryHandler.count=10 -Dmaven.wagon.httpconnectionManager.ttlSeconds=25 package

FROM eclipse-temurin:17-jre
WORKDIR /app

COPY --from=build /workspace/eureka-server/target/*.jar app.jar
EXPOSE 8761

ENV JAVA_OPTS=""
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
