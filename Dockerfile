FROM eclipse-temurin:21-jdk@sha256:ebfbb18d11ff163ee83b21258acf12248af48d22a0fdfdc68fa1a1516c05976a AS build
WORKDIR /app
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
COPY config/spotbugs config/spotbugs
COPY core/build.gradle core/build.gradle
COPY server/build.gradle server/build.gradle
COPY server/src/main server/src/main
COPY src/main/java src/main/java
COPY assets/data assets/data
RUN sed -i 's/\r$//' gradlew && sh ./gradlew --no-daemon :server:installDist

FROM eclipse-temurin:21-jre@sha256:0b99b3376fbb50dbf9c0af8535fdd5b8eb0d3fdfc8b1e4967f383b6919f6901a
WORKDIR /app
COPY --from=build /app/server/build/install/server/ ./
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["/app/bin/server"]

