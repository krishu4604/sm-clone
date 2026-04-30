# StarMaker Clone

A static StarMaker-style frontend with:

- a Java backend for form submissions and admin login
- an admin panel at `/admin.html`
- SQLite storage in `data/submissions.db`
- Render deployment support with a persistent disk

## Run the Java backend

Use the included batch file:

```bat
run-java-server.bat
```

Or run manually:

```powershell
javac -cp "lib/*" JavaBackend.java
java --enable-native-access=ALL-UNNAMED -cp ".;lib/*" JavaBackend
```

The app runs at:

```text
http://localhost:3000
```

The admin panel is at:

```text
http://localhost:3000/admin.html
```

## Default admin login

```text
Username: admin
Password: admin123
```

You can override them with environment variables:

```text
ADMIN_USERNAME=yourname
ADMIN_PASSWORD=yourpassword
```

## Notes

- Submitted records are stored in `data/submissions.db`.
- If `data/submissions.json` already exists, the Java backend imports it into SQLite the first time the database is created.
- `node_modules`, downloaded JDBC jars, compiled Java classes, logs, and saved submission data are ignored by Git.
- The older Node backend files are still present, but the Java backend is the intended runtime.

## Local SQLite driver

For local Java runs, create `lib` and download these jars:

```powershell
mkdir lib
curl.exe -L -o lib/sqlite-jdbc.jar https://repo1.maven.org/maven2/org/xerial/sqlite-jdbc/3.46.1.0/sqlite-jdbc-3.46.1.0.jar
curl.exe -L -o lib/slf4j-api.jar https://repo1.maven.org/maven2/org/slf4j/slf4j-api/2.0.13/slf4j-api-2.0.13.jar
curl.exe -L -o lib/slf4j-simple.jar https://repo1.maven.org/maven2/org/slf4j/slf4j-simple/2.0.13/slf4j-simple-2.0.13.jar
```

## Deploy on Render

This repo includes [render.yaml](</C:/Users/Asus/Videos/starmaker clone/render.yaml>) and [Dockerfile](</C:/Users/Asus/Videos/starmaker clone/Dockerfile>) for deployment on Render.

The Render service mounts a persistent disk at `/data` and the app writes `submissions.db` there via the `DATA_DIR` environment variable, so submissions survive restarts and redeploys.

Set these environment variables in Render:

```text
ADMIN_USERNAME=yourname
ADMIN_PASSWORD=yourpassword
```
