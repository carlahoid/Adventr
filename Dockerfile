# syntax=docker/dockerfile:1
# Multi-arch (linux/amd64, linux/arm64): both Temurin base images are published for each.

FROM eclipse-temurin:21-jdk AS build
WORKDIR /build
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q dependency:go-offline
COPY src src
# Tests need Docker (Testcontainers) and run in CI / locally with ./mvnw verify, not in the image build.
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q -Dmaven.test.skip=true package

FROM eclipse-temurin:21-jre
RUN groupadd --system --gid 10001 adventr \
	&& useradd --system --uid 10001 --gid adventr --home-dir /app --no-create-home adventr \
	&& mkdir -p /app /data/images \
	&& chown adventr:adventr /data/images
WORKDIR /app
COPY --from=build /build/target/adventr.jar app.jar
USER adventr
# Explicit heap limit so the whole stack fits on a small host; override via APP_JAVA_OPTS in .env.
ENV JAVA_OPTS="-Xmx384m -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
