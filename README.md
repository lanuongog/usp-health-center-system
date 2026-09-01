# USP Health Center - Admin Reporting & Analytics Prototype

Working Spring Boot + PostgreSQL prototype for the Admin Reporting & Analytics feature.

## What this includes

- `Appointment` entity + repository (with 4 pre-seeded sample records)
- **A real visual dashboard** at `http://localhost:8080/` — login form, summary cards, bar charts for visit reason/doctor breakdown, a date-range filter, and a full appointments table
- `/dashboard` - view all appointments (raw JSON, used internally by the dashboard page)
- `/dashboard/summary` - aggregated stats: total count, no-show rate, breakdown by visit reason, breakdown by doctor
- `/dashboard/summary/filter?startDate=YYYY-MM-DD&endDate=YYYY-MM-DD` - same stats, filtered by date range
- Login-protected access (Spring Security) - only an ADMIN-role user can reach `/dashboard/**`
- Auto-seeded admin user: **username `admin`, password `admin123`**
- Auto-seeded 4 sample appointments (so the dashboard isn't empty on first run)

## How to set this up (replacing your existing project files)

### 1. Back up first (just in case)
Rename your current project folder, e.g. `usp-health-center-system` -> `usp-health-center-system-OLD`.

### 2. Get a fresh copy of the Maven wrapper
Since this package only contains the *source* files (not the `mvnw`/`mvnw.cmd`/`.mvn` wrapper scripts), copy those three items from your OLD folder into this new one:
- `mvnw`
- `mvnw.cmd`
- `.mvn` (whole folder)

(These don't change project-to-project, so your existing ones are fine to reuse.)

### 3. Edit the one file you must customize
Open `src/main/resources/application.properties` and change these two lines to match your setup:
```
spring.datasource.url=jdbc:postgresql://localhost:5434/usp_health_center
spring.datasource.password=CHANGE_ME
```
- Replace `5434` with your actual PostgreSQL port (check with `netstat -ano | findstr LISTENING | findstr 543` if unsure)
- Replace `CHANGE_ME` with your real PostgreSQL password

### 4. Make sure the database exists
In pgAdmin, confirm a database named `usp_health_center` exists (create it if not: right-click Databases -> Create -> Database... -> name it `usp_health_center`).

### 5. Run it
```
.\mvnw.cmd clean spring-boot:run
```

Look for `Started DemoApplication` with no red ERROR lines.

### 6. Test it — the real dashboard
- Visit `http://localhost:8080/` in your browser (the base URL now shows the actual dashboard page, not the Whitelabel error)
- Log in with `admin` / `admin123` using the on-page login form
- You should see: 3 summary cards (Total, No-Shows, No-Show Rate), a bar chart of appointments by visit reason, a bar chart by doctor caseload, a date filter, and a full table of all appointments
- Try the date filter (e.g. 2026-08-10 to 2026-08-12) — the cards and charts update to reflect only that range
- Click "Log Out" to confirm it returns to the login form

### 7. Once confirmed working, commit and push
```
git add .
git commit -m "Working prototype: reporting dashboard with login-protected access"
git push
```

## Troubleshooting

**"Invalid character constant" error on startup** - a file got corrupted during editing. Delete the file mentioned in the error and recreate it fresh by copying from this package.

**Whitelabel error page at `/dashboard`** - check you're logged in; try incognito mode and re-enter `admin` / `admin123` when prompted.

**Connection refused to PostgreSQL** - confirm the PostgreSQL service is running (Windows Services -> `postgresql-x64-18` -> should say "Running") and that the port in `application.properties` matches what `netstat` shows.

