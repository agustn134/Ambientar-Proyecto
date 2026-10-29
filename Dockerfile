FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace
COPY gradlew build.gradle settings.gradle ./
COPY gradle ./gradle
COPY src ./src
RUN sh ./gradlew --no-daemon bootJar

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
RUN groupadd --system app && useradd --system --gid app --home-dir /app app
COPY --from=build --chown=app:app /workspace/build/libs/prueba-1.0.jar /app/app.jar
COPY --chown=app:app datos/CPdescargatxt.zip /app/datos/CPdescargatxt.zip
USER app
ENV SPRING_PROFILES_ACTIVE=render
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=55.0 -XX:+ExitOnOutOfMemoryError"
EXPOSE 10000
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
