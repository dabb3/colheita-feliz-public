FROM eclipse-temurin:21-jdk AS build
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

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/server/build/install/server/ ./
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["/app/bin/server"]

