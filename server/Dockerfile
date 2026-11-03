FROM eclipse-temurin:17-jdk AS build
WORKDIR /workspace
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle ./gradle
COPY core/model ./core/model
COPY core/signature ./core/signature
COPY server ./server
RUN chmod +x gradlew && ./gradlew --no-daemon -Pchayeon.serverOnly=true :server:installDist

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /workspace/server/build/install/server/ ./
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["/app/bin/server"]
