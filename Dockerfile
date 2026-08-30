FROM maven:3.9.16-eclipse-temurin-25 AS build

WORKDIR /workspace

COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw \
    && ./mvnw --batch-mode --no-transfer-progress dependency:go-offline

COPY src src
RUN ./mvnw --batch-mode --no-transfer-progress package -DskipTests

FROM eclipse-temurin:25-jre-noble

RUN groupadd --system --gid 10001 platform \
    && useradd --system --uid 10001 --gid platform --create-home platform

WORKDIR /app
COPY --from=build /workspace/target/collectors-auction-platform-0.1.0-SNAPSHOT.jar app.jar

USER 10001
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
