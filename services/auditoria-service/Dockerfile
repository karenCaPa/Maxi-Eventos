# syntax=docker/dockerfile:1
# ---------- Etapa 1: compilación con Gradle (dentro de Docker) ----------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
# Primero los archivos de Gradle, para aprovechar la caché de capas
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN chmod +x gradlew
COPY src src
# Caché de BuildKit para ~/.gradle (dependencias y distribución de Gradle).
# sharing=locked: los builds en paralelo de compose no pueden usar la misma caché de Gradle a la vez.
RUN --mount=type=cache,target=/root/.gradle,sharing=locked \
    ./gradlew bootJar --no-daemon

# ---------- Etapa 2: imagen de ejecución ----------
FROM eclipse-temurin:21-jre
WORKDIR /app
# El plain jar está desactivado en build.gradle: en build/libs solo queda el jar ejecutable
COPY --from=build /workspace/build/libs/*.jar app.jar
# La memoria de la JVM se controla con JAVA_TOOL_OPTIONS en docker-compose.yml;
# JAVA_OPTS queda vacío para ajustes puntuales (si se define, prevalece sobre JAVA_TOOL_OPTIONS)
ENV JAVA_OPTS=""
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
