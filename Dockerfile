# ============================================
# Stage 1: Build the application
# ============================================
# Use Eclipse Temurin JDK 21 on Alpine Linux for a lightweight build environment
FROM eclipse-temurin:21-jdk-alpine AS build

# Set the working directory inside the container
WORKDIR /app

# Copy Gradle wrapper files first (these change less frequently, enabling better layer caching)
COPY gradlew .
COPY gradle gradle

# Copy Gradle build configuration files
COPY build.gradle .
COPY settings.gradle .
COPY lombok.config .

# Copy the source code
COPY src src

# Make the Gradle wrapper executable
RUN chmod +x ./gradlew

# Build the application
# -x test: Skip tests during Docker build to speed up the process
# The JAR file will be generated in build/libs/
RUN ./gradlew build -x test

# ============================================
# Stage 2: Run the application
# ============================================
# Use Eclipse Temurin JRE 21 on Alpine Linux for a minimal runtime environment
# JRE is smaller than JDK since we only need to run the app, not compile it
FROM eclipse-temurin:21-jre-alpine

# Set the working directory for the runtime
WORKDIR /app

# Copy the built JAR file from the build stage
# This copies only the necessary artifact, keeping the final image small
COPY --from=build /app/build/libs/*.jar app.jar

# Expose port 8080 for the Spring Boot application
# This is the default port configured in application.yml
EXPOSE 8080

# Health check to ensure the container is running properly
# Checks the /ping endpoint every 30 seconds
# If it fails 3 times, the container is considered unhealthy
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/ping || exit 1

# Run the application
# -Djava.security.egd: Improves startup time by using non-blocking entropy source
# -Xmx: Set maximum heap size (can be overridden via environment variables)
ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
