# ============================================================
#  Multi-stage Dockerfile — Lazada Affiliate Link Converter
# ============================================================
#
# Stage 1: Build the fat JAR with Maven
# Stage 2: Minimal JRE runtime image (~200MB total)
#
# JVM flags are tuned for free-tier hosts with 256–512 MB RAM.
# ============================================================

# --- Stage 1: Build ---
FROM maven:3.9-eclipse-temurin-21-alpine AS builder

WORKDIR /build

# Copy dependency descriptors first for better layer caching
COPY pom.xml .
RUN mvn dependency:go-offline -q

# Copy source and build
COPY src ./src
RUN mvn package -DskipTests -q

# --- Stage 2: Runtime ---
FROM eclipse-temurin:21-jre-alpine AS runtime

WORKDIR /app

# Non-root user for security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

# Copy the fat JAR from the build stage
COPY --from=builder /build/target/lazada-affiliate-converter-*.jar app.jar

# Expose the default port (overridable via PORT env var)
EXPOSE 8080

# JVM startup flags tuned for low-memory environments:
#   -Xms64m  : Start with 64MB heap
#   -Xmx256m : Max 256MB heap (leaves headroom for non-heap + OS in 512MB containers)
#   -XX:+UseSerialGC : Lighter GC for single-core free-tier instances
ENTRYPOINT ["java", \
    "-Xms64m", "-Xmx256m", \
    "-XX:+UseSerialGC", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "app.jar"]
