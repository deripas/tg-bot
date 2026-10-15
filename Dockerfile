FROM eclipse-temurin:25-jdk AS build
WORKDIR /src
COPY . .
# native libraries of OpenCV and Tesseract only for the platform of the image, not for all of them
ARG TARGETARCH
RUN --mount=type=cache,target=/root/.m2 \
    case "$TARGETARCH" in arm64) platform=linux-arm64 ;; *) platform=linux-x86_64 ;; esac \
    && ./mvnw -q -B -DskipTests -Djavacpp.platform=$platform package

FROM eclipse-temurin:25-jre
# the Java API of OpenCV loads all its modules, highgui among them, which is linked against GTK2
RUN apt-get update \
    && apt-get install -y --no-install-recommends libgtk2.0-0 \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app

COPY --from=build /src/bot-app/target/bot-app-*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "--enable-native-access=ALL-UNNAMED", "-jar", "app.jar"]
