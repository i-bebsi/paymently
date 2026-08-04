# ── Build stage ──
FROM maven:3.9-eclipse-temurin-21-alpine AS builder
WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests -q

# ── Runtime stage ──
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /build/target/*.jar app.jar
RUN mkdir -p /app/logs
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
