# Team Setup Guide — USP Health Center System

This gets you from a fresh clone to a running app on your own machine. Follow every step in order — skipping ahead is where most setup problems come from.

## What you need installed first

1. **Java 17 or newer** — check with `java -version` in a terminal
2. **PostgreSQL** (the version doesn't have to match exactly, but 16+ is recommended) — download from postgresql.org
3. **Git**
4. **VS Code** (recommended) with the "Extension Pack for Java" installed

## Step 1: Clone the repo

```
git clone https://github.com/lanuongog/usp-health-center-system.git
cd usp-health-center-system
```

## Step 2: Confirm PostgreSQL is actually running

- Search Windows for **"Services"**
- Find `postgresql-x64-<version>` in the list
- Status should say **"Running"**. If not, right-click → Start.

## Step 3: Find YOUR PostgreSQL port

Don't assume it's 5432 — it varies by machine. Run:

```
netstat -ano | findstr LISTENING | findstr 543
```

Note the port number that shows up (e.g. `5432`, `5433`, `5434`).

## Step 4: Create the project database

Open **pgAdmin 4**:
1. Expand **Servers → PostgreSQL** (enter your server password if asked)
2. Right-click **Databases → Create → Database...**
3. Name it exactly: `usp_health_center`
4. Save

## Step 5: Create your own `application.properties`

This file is **intentionally not included in the repo** (it holds a real database password, which should never be committed to GitHub). You need to create it yourself:

1. In the project, go to `src/main/resources/`
2. Create a new file named exactly `application.properties`
3. Paste this in, editing the two marked lines with YOUR actual port and password:

```properties
spring.application.name=demo

# EDIT THESE TWO — use YOUR port from Step 3 and YOUR PostgreSQL password
spring.datasource.url=jdbc:postgresql://localhost:5434/usp_health_center
spring.datasource.password=YOUR_PASSWORD_HERE

spring.datasource.username=postgres
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
server.tomcat.basedir=./tomcat-temp
```

4. Save

## Step 6: Run it

```
.\mvnw.cmd clean spring-boot:run
```

Wait for the log to show `Started DemoApplication` with **no red ERROR lines**. First run will take longer since Maven downloads all the dependencies.

## Step 7: Confirm it works

Visit `http://localhost:8080/` in your browser. You should see the Admin Reporting Dashboard login page.

**Default login:** username `admin`, password `admin123` (this account and 4 sample appointments are created automatically the first time the app runs — no manual database setup needed beyond creating the empty database itself).

---

## Common errors and exact fixes

### "Connection refused" / "connection timeout expired"
Your port in `application.properties` doesn't match what PostgreSQL is actually using. Re-run the Step 3 `netstat` command and fix the port number.

### "password authentication failed for user postgres"
Your password in `application.properties` is wrong. If you don't know your PostgreSQL password, open pgAdmin's Query Tool (connected to any database) and run:
```sql
ALTER USER postgres PASSWORD 'newpassword';
```
Then use that same password in `application.properties`.

### "Existing directory ... is not owned by [username]"
This is a Windows Temp-folder permission quirk. It's already fixed in this project via the `server.tomcat.basedir=./tomcat-temp` line — make sure that line is in your `application.properties`.

### "Unable to determine Dialect without JDBC metadata"
Your `application.properties` still has a placeholder or missing value (commonly the password field left as `CHANGE_ME` or similar). Double-check both the URL and password lines are filled in with real values.

### App starts fine but `/` shows a blank page or old content
Do a hard refresh in your browser: **Ctrl+F5**. Or try an incognito/private window to rule out caching.

### "Invalid character constant" or a compile error mentioning a file you didn't expect
A file got corrupted, usually from a copy-paste issue. Re-copy that specific file's content fresh from the repo (or ask in the group chat) and re-save it — don't try to hand-fix a corrupted file.

## Working directly on your own feature

Once your local setup is running:
1. Create a new branch for your module: `git checkout -b feature/your-module-name`
2. Follow the same pattern as `Appointment.java` / `AppointmentRepository.java` / `DashboardController.java` for your own entity/feature
3. Commit small, working pieces with clear messages — don't wait until everything's done to commit
4. Push your branch: `git push -u origin feature/your-module-name`
5. Let the team know so it can be reviewed and merged into `main`

## If none of the above fixes it

Post the **exact error text** from your terminal in the group chat — not just "it's not working." The specific error message is what actually tells us what's wrong.
