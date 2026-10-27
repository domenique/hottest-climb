# Hottest Climb

Climbers log the meters they climb during De Warmste Week; a leaderboard runs on the monitor in the bar.

- `/` — register (name + self-chosen 4–6 digit pin) or add meters. No email, no personal data.
- `/scherm` — bar monitor leaderboard, polls every 10 seconds.

## Development

```sh
docker compose up -d            # Postgres on :5432 (podman compose works too)
cd backend && ./gradlew run     # API on :8080, Flyway migrates on startup
cd frontend && npm install && npm run dev   # Vite on :5173, proxies /api to :8080
```

Tests (`cd backend && ./gradlew test`) need a container runtime: the repository tests run against Postgres via Testcontainers.

## Production build

```sh
cd frontend && npm run build    # writes frontend/dist
cd backend && ./gradlew buildFatJar
java -jar backend/build/libs/hottest-climb-all.jar
```

The backend bundles `frontend/dist` into the jar and serves it, so it is one artifact.
Configure with `PORT`, `DATABASE_URL`, `DATABASE_USER`, `DATABASE_PASSWORD`.

## Rules

- A single entry is 1–500 m; names are 2–30 characters and unique regardless of case.
- 5 wrong pins locks that name for 5 minutes. This is per name, not per IP, because the whole gym shares one wifi address.
