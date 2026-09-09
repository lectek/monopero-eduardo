FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -q -e -B dependency:go-offline
COPY src src
RUN mvn -q -e -B -DskipTests package

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /build/target/lojagenerica-platform-*.jar app.jar
ENV SPRING_PROFILES_ACTIVE=docker
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
