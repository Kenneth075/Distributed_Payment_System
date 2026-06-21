#FROM ubuntu:latest
#LABEL authors="Kenneth.Edoho"
#
#ENTRYPOINT ["top", "-b"]

# -------------------------------------------------------
# Uses full JDK + Maven to compile and package the app
# -------------------------------------------------------
FROM eclipse-temurin:17-jdk-alpine AS build

WORKDIR /app

# Copy pom.xml first — Docker caches this layer.
# Dependencies are only re-downloaded when pom.xml changes,
# not on every source code change.
COPY pom.xml .
RUN ./mvnw dependency:go-offline -B 2>/dev/null || true

COPY .mvn/ .mvn/
COPY mvnw .
RUN chmod +x mvnw

# Download dependencies (cached layer)
RUN ./mvnw dependency:go-offline -B

# Copy source and build (skip tests — they run in CI)
COPY src ./src
RUN ./mvnw package -DskipTests -B

# -------------------------------------------------------
# Stage 2: Runtime
# Uses slim JRE-only image — no compiler, no Maven, smaller attack surface
# -------------------------------------------------------
FROM eclipse-temurin:17-jre-alpine AS runtime

# Run as non-root user (security best practice)
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

WORKDIR /app

# Copy only the fat JAR from the build stage
COPY --from=build /app/target/payment-auth-service-*.jar app.jar

# Expose the application port
EXPOSE 8080

# JVM flags:
#   -XX:+UseContainerSupport      → respect container CPU/memory limits
#   -XX:MaxRAMPercentage=75.0     → use up to 75% of container RAM for heap
#   -Djava.security.egd=...       → faster SecureRandom startup in containers
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]