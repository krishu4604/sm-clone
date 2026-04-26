# StarMaker Clone

A static StarMaker-style frontend with:

- a Java backend for form submissions and admin login
- an admin panel at `/admin.html`
- local JSON storage in `data/submissions.json`

## Run the Java backend

Use the included batch file:

```bat
run-java-server.bat
```

Or run manually:

```powershell
javac JavaBackend.java
java JavaBackend
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

- Submitted records are stored in `data/submissions.json`.
- `node_modules`, compiled Java classes, logs, and saved submission data are ignored by Git.
- The older Node backend files are still present, but the Java backend is the intended runtime.
