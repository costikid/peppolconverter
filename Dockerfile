# Multi-stage build for FreeAgent to Peppol Converter
FROM maven:3.9-openjdk-17-slim AS build

WORKDIR /app
COPY pom.xml .
COPY src ./src

# Use Railway's cache mount for Maven dependencies
RUN --mount=type=cache,target=/root/.m2,id=maven:maven-cache,sharing=shared mvn clean package -DskipTests

# Runtime stage
FROM openjdk:17-slim

WORKDIR /app
COPY --from=build /app/target/peppol-converter-1.0.0.jar app.jar
COPY config.json config.json

# Railway assigns PORT dynamically, but app listens on 8080
EXPOSE 8080

# Health check for Railway
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
