# HabitFlow — Smart Habit Tracker

A complete, working full-stack habit tracker built with **Java 21, Spring Boot 3, Spring JDBC (JdbcTemplate) and MySQL**, with a **Thymeleaf + vanilla JavaScript + Bootstrap 5 + Chart.js** front end.

Every number you see in the UI — streaks, completion percentages, charts, heatmaps, goal progress — is computed in Java from rows in your own MySQL database. There is no mock data layer and no hardcoded statistics.

> **Demo account** (created automatically on first start when `app.seed-demo-data=true`):
> `demo@habitflow.app` / `Demo@12345`
> It ships with 6 habits, ~9 weeks of completion history and 2 goals so the dashboard is not empty. Set `HABITFLOW_SEED_DEMO=false` to skip it.

---

## 1. Feature tour

| Area | What actually works |
|---|---|
| **Auth** | Register, login, logout, BCrypt(12) hashing, session-based Spring Security, CSRF double-submit tokens, protected pages, password change that invalidates the session |
| **Dashboard** | Active habits, habits done today, today's %, current streak, best streak, 7‑day consistency, recent completions, pending today, weekly bar chart, monthly line chart, category doughnut, progress ring |
| **Habits** | Create, edit, delete (with confirmation), view, archive/reactivate, category, description, optional target + unit, frequency (DAILY / WEEKDAYS / WEEKLY / CUSTOM), start date, reminder time, colour, icon, per‑weekday schedule |
| **Tracking** | Tick any past date, undo, optional daily note, previous/next day, date picker, today's pending vs completed, completion %, future dates blocked server-side, duplicate completions blocked by a DB unique key |
| **Streaks** | Current streak, longest streak, total completions, weekly rate, monthly rate, per-habit consistency label — all from real history (rules in §4) |
| **Goals** | Create/edit/delete, link one or many habits, target value + date, progress from real completions (or manual), % bar, milestone messages at 25/50/75/100 %, reopen/complete |
| **Analytics** | Daily/weekly/monthly rates, trend line, per-habit bar chart, category performance, 0–4 level heatmap, best & weakest habits, date-range presets (7/30/90 days), single-habit filter, per-habit statistics table |
| **Insights** | Rule-based, explainable recommendations (top habit, frequently missed habits, best weekday, best time of day, weekly summary, streak/pending nudges). No external AI service is called |
| **Calendar** | Month grid with done / partial / missed / nothing‑scheduled states, day detail panel, month navigation, per-habit filter, links into tracking |
| **Notifications** | Persisted in MySQL: completion confirmations, goal milestones, reminders created when the app is opened near a habit's reminder time, weekly summary. Read / mark all / delete. **In-app only** — no email, SMS or web push |
| **Profile** | Name, email, timezone, member-since, role badge, change password |

---

## 2. Architecture

**One architecture, used consistently:** Thymeleaf renders *page shells only* (no data work in the template layer). Every page then loads its data from the `/api/**` REST layer with `fetch()`. That keeps the backend a pure REST API — exactly what you want to show in an interview — while still giving a server-rendered, bookmarkable URL structure.

```
Browser
  │  GET /dashboard  →  PageController  →  dashboard.html (shell, zero DB work)
  │  fetch('/api/dashboard')  →  JSON  →  dashboard.js renders cards + Chart.js
  ▼
Spring Security filter chain (session cookie + CSRF header)
  ▼
@RestController  ──►  @Service (business rules, streaks, metrics, insights)
                        │
                        └──►  @Repository  (JdbcTemplate + PreparedStatement)
                                ▼
                              MySQL 8
```

```
com.habitflow
├── HabitFlowApplication.java
├── config        SecurityConfig · DataSeeder
├── controller    Auth · Habit · Completion · Dashboard · Analytics · Insight ·
│                 Goal · Notification · Profile · Page
├── service       User · Habit · Completion · Metrics · Dashboard · Analytics ·
│                 Goal · Insight · Notification · StreakCalculator
├── repository    User · Category · Habit · Completion · Goal · Notification  (JdbcTemplate only)
├── model         User · Habit · HabitCategory · HabitCompletion · Goal · Notification · Frequency
├── dto           request/response records + jakarta.validation annotations
├── exception     ApiException · NotFound · BadRequest · Conflict · Forbidden · GlobalExceptionHandler
├── security      UserPrincipal · AppUserDetailsService · CurrentUser · JsonAuthHandlers
└── util          Numbers (percent helpers)
```

**Layering rule enforced throughout:** controllers only translate HTTP ⇄ DTO and never contain business logic; services own the rules and `@Transactional` boundaries; repositories own SQL. No Spring Data JPA, no Hibernate — every query is hand-written `JdbcTemplate` with `?` placeholders.

### Project layout

```
habitflow/
├── pom.xml
├── database/
│   ├── schema.sql          ← full MySQL schema (+ CREATE DATABASE)
│   └── data.sql            ← optional SQL-only sample data
├── docs/postman/HabitFlow.postman_collection.json
├── src/main/java/com/habitflow/…            (see tree above)
├── src/main/resources/
│   ├── application.properties
│   ├── db/schema.sql       ← auto-applied on start
│   ├── db/schema-h2.sql    ← same tables for the test profile
│   ├── static/css/app.css
│   ├── static/js/          core · auth · dashboard · tracking · habits ·
│   │                       goals · calendar · analytics · insights ·
│   │                       notifications · settings
│   └── templates/          index · login · register · dashboard · tracking ·
│                           habits · goals · calendar · analytics · insights ·
│                           notifications · settings · fragments/layout
└── src/test/java/com/habitflow/service/  StreakCalculator · Numbers ·
                                          CompletionService · UserService
```

---

## 3. Database schema (8 tables)

```sql
users(id PK, full_name, email UNIQUE, password_hash, role, timezone,
      created_at, updated_at)

habit_categories(id PK, name, color, icon, owner_user_id FK→users NULL,
      created_at, UNIQUE(name, owner_user_id))        -- NULL owner = global category

habits(id PK, user_id FK→users, category_id FK→habit_categories,
      name, description, target_value DECIMAL(10,2), target_unit,
      frequency CHECK IN (DAILY,WEEKDAYS,WEEKLY,CUSTOM),
      weekly_target, color, icon, reminder_time TIME, start_date DATE,
      status CHECK IN (ACTIVE,ARCHIVED), created_at, updated_at)
      INDEX idx_habits_user_status(user_id, status)

habit_schedules(id PK, habit_id FK→habits, day_of_week TINYINT CHECK 1..7,
      UNIQUE(habit_id, day_of_week))      -- 1 = Monday … 7 = Sunday

habit_completions(id PK, habit_id FK→habits, user_id FK→users,
      completion_date DATE, note VARCHAR(500), created_at,
      UNIQUE(habit_id, completion_date),  -- ← duplicate-completion guarantee
      INDEX(user_id, completion_date), INDEX(habit_id, completion_date))

goals(id PK, user_id FK→users, title, description, target_value INT,
      manual_progress INT, milestone_reached INT, target_date DATE,
      status CHECK IN (ACTIVE,COMPLETED,ARCHIVED), created_at, updated_at, completed_at)

goal_habits(goal_id FK→goals, habit_id FK→habits, created_at,
      PRIMARY KEY(goal_id, habit_id))

notifications(id PK, user_id FK→users, type, title, message, link,
      is_read TINYINT(1), created_at, INDEX(user_id, is_read))
```

Design notes worth saying out loud in an interview:

* `habit_schedules` is the **single source of truth** for "was this habit due?" — streaks, analytics, calendar and the dashboard all read it through one `StreakCalculator`, so they can never disagree.
* `UNIQUE(habit_id, completion_date)` makes duplicate completions impossible at the storage layer. The service still checks first and returns a friendly `409`, and catches `DuplicateKeyException` for a racing double-click.
* Every completion row carries `user_id` as well as `habit_id`, so list queries can be scoped with a single indexed predicate — and a `JOIN` is not needed just to prove ownership.
* Foreign keys use `ON DELETE CASCADE`, so deleting a habit cleanly removes its schedule, history and goal links inside one transaction. Deleting a user removes everything they own.
* `milestone_reached` stores the last announced milestone (0/25/50/75/100) so a 25 % message is never written twice.

---

## 4. How streaks and consistency are defined

These rules live in `StreakCalculator` and are covered by `StreakCalculatorTest`.

1. **A day is "scheduled"** when its weekday is in `habit_schedules` **and** the date is on/after `start_date`. Nothing else counts.
2. **Unscheduled days are skipped, never missed.** A Mon/Wed/Fri habit that you complete Mon and Wed has a streak of 2 even though Tuesday was empty.
3. **Current streak** walks backwards from today over scheduled days only and stops at the first scheduled day with no completion. If today is scheduled but not ticked yet, the count starts from yesterday — you are not punished mid-day.
4. **Longest streak** is the best such run anywhere between `start_date` and today.
5. **Consistency %** = `completions on scheduled days ÷ scheduled days` for the window, rounded to one decimal. A zero denominator returns `0`, never `NaN`.
6. **Overall dashboard streaks** apply the same rule per day: a day is "perfect" when every habit due that day was completed; the perfect-day run feeds `currentStreak` / `bestStreak`.

---

## 5. REST API

Base URL `http://localhost:8080`. All `/api/**` routes except `register`, `login`, `csrf`, `health` require a session. Mutating verbs also require the `X-XSRF-TOKEN` header.

| Method | Path | Purpose |
|---|---|---|
| POST | `/api/auth/register` | Create account (201) |
| POST | `/api/auth/login` | Session login (form params `email`, `password`) |
| POST | `/api/auth/logout` | Invalidate session, clear cookies |
| GET | `/api/auth/me` | Current user |
| GET | `/api/auth/csrf` | `{ headerName, token }` for the SPA |
| GET | `/api/auth/health` | Public liveness probe |
| GET/PUT | `/api/profile` | Read / update name, email, timezone |
| PUT | `/api/profile/password` | Change password (re-login required) |
| GET | `/api/categories` | Global + own categories |
| POST | `/api/categories` | Create a private category |
| GET | `/api/habits?status=ALL\|ACTIVE\|ARCHIVED` | List own habits |
| POST | `/api/habits` | Create habit (201) |
| GET/PUT/DELETE | `/api/habits/{id}` | Read / update / delete |
| PATCH | `/api/habits/{id}/status` | `{ "status": "ARCHIVED" }` |
| GET | `/api/habits/{id}/stats?from=&to=` | Streaks + rates for one habit |
| GET | `/api/tracking?date=YYYY-MM-DD` | Day view for tracking |
| POST | `/api/habits/{habitId}/complete` | `{ date, note }` → 201, `409` if already done |
| DELETE | `/api/habits/{habitId}/complete?date=` | Undo |
| GET | `/api/completions?from=&to=` | Raw history |
| GET | `/api/calendar?from=&to=&habitId=` | Day-by-day status |
| GET | `/api/dashboard` | All dashboard aggregates |
| GET | `/api/analytics?from=&to=&habitId=` | Rates, charts, heatmap, per-habit stats |
| GET | `/api/insights` | Rule-based insight cards |
| GET/POST | `/api/goals` | List / create goals |
| GET/PUT/DELETE | `/api/goals/{id}` | Read / update / delete |
| PATCH | `/api/goals/{id}/status` | Complete / reopen |
| GET | `/api/notifications?unreadOnly=&limit=` | List (limit ≤ 100) |
| GET | `/api/notifications/unread-count` | Badge count |
| PATCH | `/api/notifications/{id}/read` | Mark one read |
| POST | `/api/notifications/read-all` | Mark all read |
| DELETE | `/api/notifications/{id}` | Delete |

**Error contract** (`GlobalExceptionHandler`):

```json
{
  "timestamp": "2026-10-21T09:14:02.531Z",
  "status": 409,
  "error": "Conflict",
  "message": "This habit is already marked complete for 2026-10-21.",
  "path": "/api/habits/12/complete",
  "fieldErrors": { "name": "Habit name is required" }
}
```

| Status | When |
|---|---|
| 400 | Validation failure, future date, pre-start date, archived habit, bad range |
| 401 | No session (JSON, never an HTML redirect on `/api/**`) |
| 403 | CSRF token missing/invalid |
| 404 | Resource missing **or belongs to another user** (no existence leak) |
| 409 | Duplicate completion, duplicate email |
| 500 | Unexpected — generic message, stack trace only in the log |

A ready-made **Postman collection** is at `docs/postman/HabitFlow.postman_collection.json` (9 folders, ~35 requests, test scripts that capture the session cookie, CSRF token, habit/goal/notification ids automatically, plus three explicit security checks).

---

## 6. Security decisions

* **BCrypt strength 12** via a `PasswordEncoder` bean; the raw password never reaches the database or the logs. `Login` hides user-not-found so the response is always "Incorrect email or password."
* **Session-based auth** with `sessionFixation().migrateSession()` and `maximumSessions(5)`.
* **CSRF**: `CookieCsrfTokenRepository` with `HttpOnly=false` (so JS can read `XSRF-TOKEN`) and the double-submit header `X-XSRF-TOKEN`. `register` is explicitly ignored so a brand-new visitor can sign up; everything else is protected.
* **User isolation** is enforced in SQL, not in the controller: every repository method takes `userId` and puts it in the `WHERE` clause. `CurrentUser` reads the id from the `SecurityContext`; a request can never name a different user.
* **Parameterised SQL everywhere** — `?` placeholders and `PreparedStatement`-backed `JdbcTemplate` calls; no string concatenation of user input.
* **Secrets from environment**: `HABITFLOW_DB_URL`, `HABITFLOW_DB_USER`, `HABITFLOW_DB_PASSWORD`. Nothing sensitive is committed.

---

## 7. Run it on Windows (beginner walkthrough)

### Step 1 — Java 21
Download the **JDK 21** installer (e.g. Eclipse Temurin 21), run it, then in a new Command Prompt:

```bat
java -version
```

Expect `openjdk version "21.x"`. If not, add `JAVA_HOME` = `C:\Program Files\Eclipse Adoptium\jdk-21…` and put `%JAVA_HOME%\bin` first in `Path`.

### Step 2 — Eclipse
1. Download **Eclipse IDE for Enterprise Java and Web Developers** (2024‑09 or newer) and extract it.
2. `Window ▸ Preferences ▸ Java ▸ Installed JREs ▸ Add… ▸ Standard VM` → point at the JDK 21 folder → tick it as default.
3. `File ▸ Import ▸ Maven ▸ Existing Maven Projects` → root folder = `habitflow` (the folder containing `pom.xml`) → **Finish**.
4. Right‑click the project ▸ `Maven ▸ Update Project` ▸ tick *Force Update* ▸ OK. The `src/main/java` and `src/test/java` folders turn into source folders and the red markers disappear.

### Step 3 — MySQL Server + Workbench
1. Install **MySQL Community Server 8.x** and **MySQL Workbench** (MySQL Installer → "Developer Default").
2. During setup choose a root password you will remember (e.g. `Root@12345`).
3. Open Workbench → click your local connection.
4. `File ▸ Open SQL Script…` → `database/schema.sql` → click the ⚡ **Execute** button.
5. Right‑click the SCHEMAS panel ▸ **Refresh All** → `habitflow_db` now lists 8 tables.

### Step 4 — Configure credentials (never hardcode them)
`src/main/resources/application.properties` already reads environment variables with local defaults:

```properties
spring.datasource.url=${HABITFLOW_DB_URL:jdbc:mysql://localhost:3306/habitflow_db?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true}
spring.datasource.username=${HABITFLOW_DB_USER:root}
spring.datasource.password=${HABITFLOW_DB_PASSWORD:root}
```

Either edit the defaults, or (cleaner) set them in the environment:

```bat
setx HABITFLOW_DB_PASSWORD "YourRootPassword"
```

and reopen the terminal / restart Eclipse.

### Step 5 — Build
In Eclipse: `Run ▸ Run As ▸ Maven build…` → Goals `clean package` → **Run**.
Or in a terminal at the project root:

```bat
mvn clean package
```

Spring Boot also applies `src/main/resources/db/schema.sql` on start, so the tables exist even if you skipped Workbench.

### Step 6 — Run
* Eclipse: right‑click `HabitFlowApplication.java` ▸ `Run As ▸ Java Application`.
* or terminal: `mvn spring-boot:run`
* or the packaged jar: `java -jar target/habitflow.jar`

Wait for `Tomcat started on port 8080`. Then open **http://localhost:8080**.

### Step 7 — Use it
1. Land on the marketing page → **Sign in to the demo** (`demo@habitflow.app` / `Demo@12345`) or **Create free account**.
2. **Daily Tracking** → tick a habit, add a note, undo it.
3. **My Habits** → create a habit, pick category/days/colour/reminder, archive and reactivate it.
4. **Goals** → link a goal to a habit and watch the percentage come from real completions.
5. **Analytics** → switch presets 7 / 30 / 90 days and filter by habit.
6. **Calendar** → click any day to see what was due and what was done.
7. **Profile & Settings** → rename yourself, change the password (you will be signed out).

### Step 8 — Test with Postman
1. Import `docs/postman/HabitFlow.postman_collection.json`.
2. Run **01 Auth ▸ Login** first (it stores the session cookie and CSRF token).
3. Then any other request. Expected responses are written into each request's description.

### Step 9 — Run the tests
```bat
mvn test
```
27 tests, no MySQL needed (H2 in MySQL-compatibility mode via the `test` profile). To run one class:

```bat
mvn test -Dtest=StreakCalculatorTest
```

### Troubleshooting

| Symptom | Fix |
|---|---|
| `Communications link failure` / `Access denied for user` | MySQL not running (Services ▸ MySQL80 ▸ Start) or wrong password; verify with `mysql -u root -p`. |
| `Unknown database 'habitflow_db'` | Run `database/schema.sql`, or add `createDatabaseIfNotExist=true` to the JDBC URL. |
| `Port 8080 was already in use` | `netstat -ano \| findstr :8080` then `taskkill /PID <pid> /F`; or run with `--server.port=8081`. |
| `Public Key Retrieval is not allowed` | Keep `allowPublicKeyRetrieval=true` in the URL (already there). |
| Pages load but every card says "Could not load…" | You are signed out — the API returns `401`. Sign in again. |
| `403` on POST from Postman | Missing `X-XSRF-TOKEN`; run the CSRF or Login request first. |
| Red X in Eclipse, `release version 21 not supported` | Eclipse is on an older JDK: fix `Installed JREs`, then *Maven ▸ Update Project*. |
| `Table 'habitflow_db.users' doesn't exist` | `spring.sql.init.mode` is `always` by default; check the log for a schema error and re-run `database/schema.sql`. |

---

## 8. What the finished UI looks like

A light theme on a calm purple / blue / teal palette (`#7C6BFF`, `#3B82F6`, `#14B8A6`), Plus Jakarta Sans, 268 px fixed sidebar, sticky top bar, 18 px rounded cards with a soft shadow, and a single spacing rhythm throughout.

* **Landing (`/`)** — gradient headline, "app preview" card with three sample habit rows and a progress bar, six feature cards, numbered onboarding list, stack card with the run commands, closing CTA. Sticky nav with *Sign in* / *Get started*.
* **Login / Register** — split layout: form card on the left with inline validation, gradient brand panel on the right showing the demo credentials and three value props.
* **Dashboard** — greeting line + date, six stat tiles (icon chip top-right, delta note below), a "Today's habits" list with circular check buttons that turn teal when done, a progress ring card (`2 / 5`, "3 left"), a 30-day consistency bar, a weekly bar chart (bars change colour at 50 % and 100 %), a monthly area line chart, a category doughnut with a bottom legend, a "recently completed" feed and a "top streak" card.
* **Daily Tracking** — date stepper (`‹ Today · 21 Oct ›` + native date input), a teal progress ring, Done/Left chips, one row per due habit with checkbox, icon, category, reminder, streak and either *Add note* or *Undo*. A note modal with a 500-character counter. If you navigate to a future date, a warning banner appears and every checkbox is disabled.
* **My Habits** — segmented ALL/ACTIVE/ARCHIVED filter, search box, three-column card grid. Each card: coloured icon tile, category, frequency chip, weekday chip, reminder chip, "Since …" and an "Analytics →" link, plus a ⋮ menu for Edit / Archive / Delete. The create/edit modal has a colour swatch row, a 16-icon picker, seven day toggles and a frequency-dependent weekly target.
* **Goals** — four summary tiles (active, completed, average progress, closest milestone), goal cards with `progress / target`, a thick gradient progress bar, percentage, linked-habit chips, days-remaining line, milestone badge and the milestone message in teal.
* **Analytics** — date-range inputs + 7/30/90 presets + habit selector; three headline metrics with sub-notes; trend line; horizontal per-habit bar chart; category bar chart; a heatmap grid shaded in four steps with a legend; a statistics table (habit, current, longest, total, range %, label chip); "Best performing" and "Below 60 %" columns.
* **Calendar** — month grid colour-coded: teal = all done, amber = partial, red = missed, grey = nothing scheduled (never counted as missed). Today has a ring, the selected day a purple border. Right panel shows the day's habits and an "Open this day in tracking" button.
* **Insights** — two-column cards with a tinted icon, title, explanation, and a type chip. Refreshing shows a toast. The page states plainly that the logic is rule-based Java, not a trained model.
* **Notifications** — filter chips All/Unread, "Mark all read", rows with type icon, message, relative time, optional link, mark-read and delete buttons, unread highlighted with a purple left border.
* **Profile & Settings** — avatar with initials, name/email/role/member-since, "BCrypt protected account" note, profile form, password form with confirmation, and a sign-out card.
* **Everywhere** — skeleton loaders while fetching, purpose-written empty states ("Nothing scheduled — unscheduled days are never counted as missed"), toast notifications for success/error, a destructive-action confirm modal, and a responsive breakpoint set at 1100 px / 992 px / 576 px where the sidebar collapses into an overlay drawer.

To capture screenshots for a report: run the app, sign in as the demo user, press `Win + Shift + S`, and save into `docs/screenshots/` (`dashboard.png`, `tracking.png`, `habits.png`, `analytics.png`, `calendar.png`, `goals.png`, `insights.png`). The demo data makes every chart non-empty.

---

## 9. Publishing to GitHub

```bat
git init
git add .
git commit -m "HabitFlow: Spring Boot + JDBC + MySQL habit tracker"
git branch -M main
git remote add origin https://github.com/<your-username>/habitflow.git
git push -u origin main
```

`.gitignore` already excludes `target/`, Eclipse metadata, IDE folders, `*.log` and local secret files. Before pushing, confirm `application.properties` contains only environment-variable defaults — no real password.

## 10. Possible next steps

1. Add `@WithMockUser`-based MockMvc tests for the controllers and a `@JdbcTest` repository test.
2. Export analytics as CSV/PDF.
3. Optional AI summary behind an `AiInsightService` interface with a `RuleBasedInsightService` (default) and an HTTP implementation enabled by config — the current design already isolates it.
4. Add Flyway migrations so schema changes are versioned instead of `CREATE TABLE IF NOT EXISTS`.
5. Extend reminders with a scheduled `@Scheduled` job plus a service worker for real browser notifications (would require VAPID keys and HTTPS).

---

Built as a portfolio project: Java 21 · Spring Boot 3.3 · Spring Web · Spring JDBC · Spring Security · Bean Validation · MySQL 8 · Maven · JUnit 5 · Mockito · Thymeleaf · Bootstrap 5 · Chart.js.
