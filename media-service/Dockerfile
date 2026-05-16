# syntax=docker/dockerfile:1.6
FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /workspace

COPY . .
RUN --mount=type=cache,target=/root/.m2,sharing=locked mvn -B -U -pl media-service -am -DskipTests -Dmaven.wagon.http.retryHandler.count=10 -Dmaven.wagon.httpconnectionManager.ttlSeconds=25 package

FROM eclipse-temurin:17-jre
WORKDIR /app

COPY --from=build /workspace/media-service/target/*.jar app.jar
EXPOSE 8087

ENV JAVA_OPTS=""
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
