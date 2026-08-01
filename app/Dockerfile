# syntax=docker/dockerfile:1
# ──────────────────────────────────────────────────────────────────────────────
# FleetSec Vulnerable App — Dockerfile multi-stage
#
# La app es vulnerable a NIVEL DE APLICACIÓN (V01-V10) a propósito, pero la imagen
# está hardened a NIVEL DE INFRAESTRUCTURA (non-root, JRE alpine, tag pinneado,
# healthcheck) para pasar limpio en el stage de Container/IaC del pipeline (FSEC-13).
# ──────────────────────────────────────────────────────────────────────────────

# ── Stage 1 · build (Maven dentro del contenedor; no requiere Maven local) ─────
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Cachea dependencias antes de copiar el código fuente.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q clean package -DskipTests

# ── Stage 2 · runtime (JRE alpine, non-root, tag pinneado — no :latest) ────────
FROM eclipse-temurin:21-jre-alpine AS runtime

# Usuario no privilegiado (Checkov CKV_DOCKER_3).
RUN addgroup -S fleetsec && adduser -S -G fleetsec fleetsec

WORKDIR /app
COPY --from=build /build/target/vulnerable-app.jar app.jar
COPY reports ./reports
RUN mkdir -p /app/logs && chown -R fleetsec:fleetsec /app

USER fleetsec
EXPOSE 8080

# Healthcheck (Checkov CKV_DOCKER_2) — usa el spec OpenAPI que expone SpringDoc.
HEALTHCHECK --interval=30s --timeout=3s --start-period=45s --retries=3 \
  CMD wget -qO- http://localhost:8080/v3/api-docs >/dev/null 2>&1 || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
