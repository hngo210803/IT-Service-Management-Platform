FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
COPY src ./src
RUN mvn -B -ntp package

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S itsm && adduser -S -G itsm itsm
WORKDIR /app
COPY --from=build /src/target/itsm-platform-*.jar app.jar
USER itsm
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
