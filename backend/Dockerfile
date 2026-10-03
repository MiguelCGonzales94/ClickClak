FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /app

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

COPY src/ src/
RUN chmod +x mvnw && ./mvnw -q -DskipTests package

FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

RUN apk add --no-cache curl \
    && addgroup -S clickclak \
    && adduser -S clickclak -G clickclak

COPY --from=build /app/target/backend-0.1.0-SNAPSHOT.jar app.jar

USER clickclak
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
