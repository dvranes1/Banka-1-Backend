FROM eclipse-temurin:23-jdk AS build
WORKDIR /workspace

COPY gradle gradle
COPY gradlew gradlew
COPY gradlew.bat gradlew.bat
COPY settings.gradle gradle.properties ./
COPY company-observability-starter/build.gradle company-observability-starter/build.gradle
COPY company-observability-starter/src company-observability-starter/src
COPY NotificationService/build.gradle NotificationService/build.gradle
COPY NotificationService/src NotificationService/src

RUN chmod +x gradlew && ./gradlew --no-daemon :NotificationService:clean :NotificationService:bootJar

FROM eclipse-temurin:23-jre
WORKDIR /app

COPY --from=build /workspace/NotificationService/build/libs/*.jar app.jar

EXPOSE 8006

ENTRYPOINT ["java", "-jar", "app.jar"]
