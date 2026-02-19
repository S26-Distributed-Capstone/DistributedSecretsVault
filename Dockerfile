# Production-ready Dockerfile: Secure + Layered
# Combines security (non-root user) with performance (layer caching)
FROM eclipse-temurin:25

# Create non-root user
RUN addgroup --system spring && adduser --system spring --ingroup spring

# Set working directory and change ownership
WORKDIR /app
RUN chown spring:spring /app

# Switch to non-root user
USER spring:spring

# Copy dependencies layer (cached unless dependencies change)
ARG DEPENDENCY=target/dependency
COPY --chown=spring:spring ${DEPENDENCY}/BOOT-INF/lib /app/lib
COPY --chown=spring:spring ${DEPENDENCY}/META-INF /app/META-INF

# Copy application layer (rebuilt when your code changes)
COPY --chown=spring:spring ${DEPENDENCY}/BOOT-INF/classes /app

ENTRYPOINT ["java","-cp","/app:/app/lib/*","edu.yu.capstone.DistributedSecretsVault.DistributedSecretsVaultApplication"]
