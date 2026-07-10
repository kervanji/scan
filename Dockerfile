FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /app

# Cache dependencies
COPY pom.xml .
RUN mvn -B dependency:go-offline -Prelease || true

COPY src ./src
COPY scripts ./scripts
COPY release-templates ./release-templates
COPY updates ./updates

RUN chmod +x scripts/build-release.sh

ARG PLATFORM=win
ENV PLATFORM=${PLATFORM}

RUN ./scripts/build-release.sh

FROM alpine:3.20 AS export
WORKDIR /out
COPY --from=builder /app/release-output ./release-output
COPY --from=builder /app/updates/version.json ./updates/version.json
