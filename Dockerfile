# 1. Frontend: Vite build into frontend/dist
FROM node:24-alpine AS frontend
WORKDIR /app/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

# 2. Backend: fat jar with the frontend bundled in as static resources
FROM eclipse-temurin:25-jdk AS backend
WORKDIR /app/backend
COPY backend/gradlew backend/settings.gradle.kts backend/build.gradle.kts ./
COPY backend/gradle ./gradle
# Resolve dependencies in their own layer so code changes don't re-download them.
RUN ./gradlew dependencies --no-daemon -q > /dev/null
COPY backend/src ./src
COPY --from=frontend /app/frontend/dist /app/frontend/dist
RUN ./gradlew buildFatJar --no-daemon -q

# 3. Runtime
FROM eclipse-temurin:25-jre
RUN useradd --system --no-create-home app
USER app
WORKDIR /app
COPY --from=backend /app/backend/build/libs/hottest-climb-all.jar app.jar
# Blank disables /admin; the "admin" development default must never reach a deployment.
ENV ADMIN_PASSWORD=""
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
