FROM maven:3.9.16-eclipse-temurin-25-noble AS build

WORKDIR /workspace

COPY pom.xml ./
RUN mvn -B -ntp dependency:go-offline

COPY src ./src
RUN mvn -B -ntp -DskipTests package

FROM eclipse-temurin:25-jre-noble AS runtime

WORKDIR /app

RUN groupadd --system spring \
    && useradd --system --gid spring --no-create-home spring

COPY --from=build --chown=spring:spring \
    /workspace/target/sports-session-platform-0.0.1-SNAPSHOT.jar \
    /app/application.jar

USER spring:spring

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/application.jar"]
