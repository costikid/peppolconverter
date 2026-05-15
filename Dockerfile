# Multi-stage build for FreeAgent to Peppol Converter
FROM maven:3.9-eclipse-temurin-17-alpine AS build

WORKDIR /app
COPY pom.xml .
COPY src ./src

RUN mvn clean package -DskipTests

# Runtime stage
FROM eclipse-temurin:17-jre-jammy

WORKDIR /app
COPY --from=build /app/target/peppol-converter-1.0.0.jar app.jar
<<<<<<< HEAD
COPY generate-config.sh .

# Make script executable
RUN chmod +x generate-config.sh

=======
COPY config.json.example config.json

>>>>>>> 5b7840b (Fix Railway deployment: Dockerfile overhaul, env var config support, QuickBooks extraction strategy, updated controllers and services)
# Railway assigns PORT dynamically; fall back to 8080 locally
EXPOSE 8080

RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*

# Health check for Railway
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD curl -f http://localhost:${PORT:-8080}/actuator/health || exit 1

<<<<<<< HEAD
# Generate config.json from environment variables, then start the app
ENTRYPOINT ["sh", "-c", "./generate-config.sh && exec java -jar app.jar --server.port=${PORT:-8080}"]
=======
ENTRYPOINT ["sh", "-c", "exec java -jar app.jar --server.port=${PORT:-8080}"]
>>>>>>> 5b7840b (Fix Railway deployment: Dockerfile overhaul, env var config support, QuickBooks extraction strategy, updated controllers and services)
