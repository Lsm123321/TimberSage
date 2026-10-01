ARG MAVEN_IMAGE=maven:3.9-eclipse-temurin-17-alpine
ARG RUNTIME_IMAGE=eclipse-temurin:17-jre-alpine

FROM ${MAVEN_IMAGE} AS build

WORKDIR /app

COPY pom.xml ./
COPY src ./src

RUN mvn -DskipTests package

FROM ${RUNTIME_IMAGE}

WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
