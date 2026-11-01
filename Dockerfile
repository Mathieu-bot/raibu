# ============================================
# Stage 1: Build the application
# ============================================
FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /app

# Build configuration first: this layer only changes when the dependencies change
COPY gradlew .
COPY gradle gradle
COPY build.gradle settings.gradle lombok.config ./
RUN chmod +x ./gradlew

# Resolve and cache the dependencies in their own layer (re-run only if the files above change)
RUN ./gradlew dependencies --no-daemon > /dev/null

# Sources change often: copy them after the dependency layer
COPY src src
RUN ./gradlew bootJar --no-daemon -x test

# ============================================
# Stage 2: Run the application
# ============================================
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Run as an unprivileged user
RUN addgroup -S raibu && adduser -S raibu -G raibu
COPY --from=build --chown=raibu:raibu /app/build/libs/app.jar app.jar
USER raibu

# Sized for small free-tier instances (512 MB): cap the heap at 70% of the container memory
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -XX:TieredStopAtLevel=1"

# The application listens on $PORT (default 8080, see application.yml)
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD wget -qO- http://localhost:${PORT:-8080}/ping || exit 1

ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
