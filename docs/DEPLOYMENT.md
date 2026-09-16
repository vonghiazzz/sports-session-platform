# Deployment — Render + Vercel + Supabase

## 1. Architecture

```text
Browser
  -> Vercel (React + Vite SPA)
  -> Render Web Service (Spring Boot REST API)
  -> Supabase PostgreSQL (Session Pooler, SSL)
```

The browser never receives database credentials and never connects directly to
PostgreSQL. Only the Render backend connects to Supabase. The Vercel build gets
only the public Render origin.

Production deploys use `main`. Development remains on feature branches until
verification, commit/push, and PR merge are complete:

```text
feature branch -> verification -> commit/push -> merge PR into main
               -> Render and Vercel auto-deploy main
```

## 2. Prerequisites

- GitHub access to `vonghiazzz/sports-session-platform`.
- Supabase, Render, and Vercel accounts.
- A reviewed merge into `main`; do not point production services at an
  unfinished feature branch.
- The Render and Vercel production URLs do not belong in source control.

## 3. Supabase Setup

1. Create a Supabase project and retain the database password in a password
   manager.
2. In the project **Connect** dialog, select **Session pooler**.
3. Copy the exact host, port, database, and username shown by Supabase. Shared
   Session Pooler normally uses port `5432`, database `postgres`, and username
   `postgres.<project-ref>`.
4. Convert the connection details to this JDBC form:

   ```text
   jdbc:postgresql://<SESSION_POOLER_HOST>:5432/postgres?sslmode=require
   ```

5. Do not create application tables manually. On the first backend startup,
   Flyway validates and applies immutable migrations V1 through V9; Hibernate
   then validates the resulting schema.

V8 calls PostgreSQL core function `gen_random_uuid()`. Current PostgreSQL
provides it without requiring `uuid-ossp`; the project migrations require no
manual Supabase extension enablement. The migrations also use supported CHECK
constraints, partial indexes, and a sequence.

Do not use a Supabase anon key, service-role key, or Supabase HTTP API URL as a
JDBC credential.

## 4. Render Backend Setup

Create a **Web Service** from the GitHub repository with these exact settings:

| Field | Value |
|---|---|
| Name | `sports-session-platform-api` (suggested) |
| Branch | `main` |
| Root Directory | `backend` |
| Runtime | `Docker` |
| Dockerfile | `Dockerfile` relative to the backend root |
| Compute | Free is acceptable for MVP testing |
| Health Check Path | `/api/health` |

Do not select Node, Yarn, or `yarn start`. The Dockerfile builds the Maven
project using Java 25 and starts the executable Spring Boot JAR. Render injects
`PORT`; Spring listens on `${PORT}` and otherwise defaults locally to `8080`.
No explicit `server.address` is set, so embedded Tomcat binds externally rather
than only to `127.0.0.1`.

## 5. Render Environment Variables

Add these keys in Render. Values below are placeholders only:

```text
SPRING_DATASOURCE_URL=jdbc:postgresql://<SESSION_POOLER_HOST>:5432/postgres?sslmode=require
SPRING_DATASOURCE_USERNAME=postgres.<PROJECT_REF>
SPRING_DATASOURCE_PASSWORD=<SUPABASE_DATABASE_PASSWORD>
APP_CORS_ALLOWED_ORIGINS=https://<VERCEL_PRODUCTION_DOMAIN>
```

`APP_CORS_ALLOWED_ORIGINS` accepts a comma-separated allow-list when more than
one exact trusted origin is required. Never use `*`. Render supplies `PORT`
automatically; it does not need a dashboard value.

## 6. Vercel Frontend Setup

Import the same GitHub repository into Vercel:

| Field | Value |
|---|---|
| Production Branch | `main` |
| Root Directory | `frontend` |
| Framework Preset | `Vite` |
| Build Command | `npm run build` |
| Output Directory | `dist` |

Add this public build-time variable:

```text
VITE_API_BASE_URL=https://<RENDER_SERVICE_NAME>.onrender.com
```

Do not append `/api`; API clients already supply `/api/...`. Do not put a
database URL, password, service-role key, or private token in any `VITE_`
variable because Vite embeds those values in the browser bundle.

`frontend/vercel.json` rewrites browser routes to `index.html`, so direct loads
and refreshes of `/sessions/...`, `/player-session/...`, and `/players/...`
remain handled by React Router. Static files retain filesystem precedence.

## 7. CORS Finalization

After Vercel assigns the stable production URL:

1. Set Render `APP_CORS_ALLOWED_ORIGINS` to that exact origin, without a path
   or trailing slash, for example `https://<project>.vercel.app`.
2. Redeploy/restart the Render service so the environment value is loaded.
3. Verify a browser API request succeeds without a CORS error.

The backend allows only configured origins on `/api/**`, methods `GET`, `POST`,
`PUT`, `DELETE`, and preflight `OPTIONS`. It does not enable credential mode or
wildcard/reflected origins. Local development defaults to
`http://localhost:5173`.

## 8. Deployment Order

1. Create and collect the Supabase Session Pooler connection details.
2. Configure and deploy the Render backend from `main`.
3. Configure and deploy the Vercel frontend from `main` using the Render URL.
4. Put the final Vercel origin in Render CORS configuration and redeploy.
5. Run the production smoke test below.

## 9. Flyway Startup

An empty Supabase database is expected. Startup is:

```text
connect with SSL -> Flyway validate -> migrate V1..V9
                 -> Hibernate ddl-auto=validate -> application ready
```

Flyway is the only schema owner. Do not enable Hibernate `create` or `update`,
do not edit an applied migration, and do not create tables in the dashboard.
If a migration fails, fix the environment/permissions or ship a reviewed new
migration; never rewrite V1–V9.

## 10. Smoke Test

1. Open Render logs and confirm Flyway applies/validates V1–V9.
2. Confirm Spring Boot reports the application started.
3. Request `GET https://<render-host>/api/health` and expect `{"status":"UP"}`.
4. Inspect Supabase Table Editor and confirm the application schema exists.
5. Open the Vercel frontend and confirm no browser CORS errors.
6. Create a Player and confirm `P000001` or the next Player code.
7. Create a Venue and Court.
8. Create and start a Session.
9. Directly open and refresh `/sessions/{sessionId}`; it must not return a
   Vercel 404.
10. Directly open and refresh `/player-session/{token}`.
11. Confirm created data persists in Supabase.
12. Restart/redeploy Render and confirm the same data remains available.

## 11. Redeploy / Rollback Basics

- Normal release: merge a reviewed PR into `main`; Render and Vercel
  auto-deploy that commit.
- Redeploy: use the provider dashboard for the same known-good commit.
- Application rollback: redeploy a previous compatible commit from the
  provider dashboard.
- Database migrations are forward-only. Do not roll back by editing V1–V9.
  Confirm schema compatibility before rolling application code backward.

## 12. Secret Handling

- Store database values only as Render secrets/environment variables.
- Store only `VITE_API_BASE_URL` in Vercel; it is public configuration.
- Never commit `.env`, `.env.local`, `.env.production`, or `.env.*.local`.
- `frontend/.env.example` contains safe placeholders only.
- Do not paste passwords into tickets, pull requests, logs, screenshots, or
  chat.

## 13. Free-Tier Caveats

- A free Render service can cold-start after inactivity, so the first browser
  request may be noticeably slower and frontend polling can temporarily show a
  recoverable network error.
- Supabase and Vercel free-tier quotas and inactivity policies can change;
  monitor provider dashboards before a pilot.
- Free compute is suitable for MVP verification, not a production availability
  guarantee.
