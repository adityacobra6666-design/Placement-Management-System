package com.placement.server;

import com.placement.db.DBConnection;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.*;
import java.util.*;

public class PlacementServer {

    public static void main(String[] args) {
        int port = DBConnection.getServerPort();

        // Perform initial DB check to verify connection before printing success
        boolean dbHealthy = false;
        String dbUser = "";
        String dbVersion = "";
        String dbError = "";

        try (Connection testConn = DBConnection.getConnection()) {
            DatabaseMetaData meta = testConn.getMetaData();
            dbVersion = meta.getDatabaseProductVersion();
            try (Statement stmt = testConn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT USER FROM dual")) {
                if (rs.next()) {
                    dbUser = rs.getString(1);
                    dbHealthy = true;
                }
            }
        } catch (SQLException e) {
            dbError = "ORA-" + e.getErrorCode() + ": " + e.getMessage();
            System.err.println("[DB-ERROR] Initial database connection check failed: " + dbError);
        }

        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

            // Static Web Assets Handler
            server.createContext("/", new StaticFileHandler());

            // API Endpoints
            server.createContext("/api/health", new HealthHandler());
            server.createContext("/api/login", new LoginHandler());
            server.createContext("/api/dashboard", new DashboardHandler());
            server.createContext("/api/students", new StudentsHandler());
            server.createContext("/api/companies", new CompaniesHandler());
            server.createContext("/api/drives", new DrivesHandler());
            server.createContext("/api/applications", new ApplicationsHandler());
            server.createContext("/api/check-eligibility", new EligibilityHandler());
            server.createContext("/api/query", new SqlQueryHandler());

            server.setExecutor(null); // default executor
            server.start();

            if (dbHealthy) {
                System.out.println("==================================================");
                System.out.println("STUDENT PLACEMENT MANAGEMENT SYSTEM");
                System.out.println("Backend Server Started Successfully");
                System.out.println("Server URL    : http://localhost:" + port);
                System.out.println("Database User : " + dbUser);
                System.out.println("JDBC Driver   : Oracle JDBC Thin Driver");
                System.out.println("Port Used     : " + port);
                System.out.println("==================================================");
            } else {
                System.err.println("==================================================");
                System.err.println("STUDENT PLACEMENT MANAGEMENT SYSTEM");
                System.err.println("Backend Server Started on port " + port + ", but Database is NOT connected.");
                System.err.println("Database Error: " + dbError);
                System.err.println("==================================================");
            }

        } catch (IOException e) {
            System.err.println("[SERVER-ERROR] Failed to start HTTP server on port " + port + ": " + e.getMessage());
        }
    }

    // --- Structured Logging Helpers ---

    public static void logHttpStart(String method, String endpoint) {
        System.out.println("[HTTP] " + method + " " + endpoint);
    }

    public static void logHttpEnd(int statusCode) {
        System.out.println("[HTTP] Response status: " + statusCode);
    }

    public static void logDbOp(String opType, String desc, int count, long durationMs, boolean isSelect) {
        System.out.println("[DB] Operation: " + opType + " - " + desc);
        System.out.println("[DB] Status: SUCCESS");
        if (isSelect) {
            System.out.println("[DB] Rows returned: " + count);
        } else {
            System.out.println("[DB] Rows affected: " + count);
        }
        System.out.println("[DB] Execution time: " + durationMs + " ms");
    }

    public static void logDbError(String opType, String desc, SQLException e, long durationMs) {
        System.out.println("[DB] Operation: " + opType + " - " + desc);
        String errDetail = "ORA-" + e.getErrorCode() + ": " + e.getMessage();
        System.err.println("[DB] Status: FAILED (" + errDetail.replaceAll("[\\r\\n]", " ") + ")");
        System.out.println("[DB] Execution time: " + durationMs + " ms");
    }

    public static void logDbFailed(String opType, String desc, String reason, long durationMs) {
        System.out.println("[DB] Operation: " + opType + " - " + desc);
        System.out.println("[DB] Status: FAILED (" + reason + ")");
        System.out.println("[DB] Execution time: " + durationMs + " ms");
    }

    public static void sendResponse(HttpExchange exchange, int statusCode, String response, String contentType) throws IOException {
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType + "; charset=UTF-8");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    public static Map<String, String> parseQueryParams(String query) {
        Map<String, String> params = new HashMap<>();
        if (query == null || query.trim().isEmpty()) return params;
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            String[] kv = pair.split("=", 2);
            String k = URLDecoder.decode(kv[0], StandardCharsets.UTF_8);
            String v = kv.length > 1 ? URLDecoder.decode(kv[1], StandardCharsets.UTF_8) : "";
            params.put(k, v);
        }
        return params;
    }

    public static Map<String, String> parseRequestBody(HttpExchange exchange) throws IOException {
        InputStream is = exchange.getRequestBody();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int len;
        while ((len = is.read(buffer)) != -1) {
            baos.write(buffer, 0, len);
        }
        String body = baos.toString(StandardCharsets.UTF_8.name()).trim();
        Map<String, String> map = new HashMap<>();

        if (body.startsWith("{") && body.endsWith("}")) {
            String content = body.substring(1, body.length() - 1).trim();
            List<String> tokens = splitJsonTokens(content);
            for (String token : tokens) {
                int colonIdx = -1;
                boolean inQ = false;
                for (int i = 0; i < token.length(); i++) {
                    char ch = token.charAt(i);
                    if (ch == '\"' && (i == 0 || token.charAt(i - 1) != '\\')) inQ = !inQ;
                    if (ch == ':' && !inQ) { colonIdx = i; break; }
                }
                if (colonIdx > 0) {
                    String k = cleanJsonString(token.substring(0, colonIdx).trim());
                    String v = cleanJsonString(token.substring(colonIdx + 1).trim());
                    map.put(k, v);
                }
            }
        } else {
            // Form encoded
            map = parseQueryParams(body);
        }
        return map;
    }

    private static String cleanJsonString(String val) {
        if (val == null) return "";
        val = val.trim();
        // Remove surrounding quotes if present
        if (val.startsWith("\"") && val.endsWith("\"") && val.length() >= 2) {
            val = val.substring(1, val.length() - 1);
        }
        val = val.replace("\\\"", "\"").replace("\\\\", "\\").replace("\\n", "\n").replace("\\r", "\r");
        // Also strip any leading/trailing escaped backslashes if sent by CLI tools
        if (val.startsWith("\\") && val.length() > 1) {
            val = val.substring(1);
        }
        if (val.endsWith("\\") && val.length() > 1) {
            val = val.substring(0, val.length() - 1);
        }
        return val.trim();
    }

    private static List<String> splitJsonTokens(String s) {
        List<String> list = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\"' && (i == 0 || s.charAt(i - 1) != '\\')) {
                inQuotes = !inQuotes;
            }
            if (c == ',' && !inQuotes) {
                if (sb.length() > 0) list.add(sb.toString().trim());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        if (sb.length() > 0) {
            list.add(sb.toString().trim());
        }
        return list;
    }

    public static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    // --- Static File Handler (Serves HTML, CSS, JS from /web directory) ---
    static class StaticFileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();

            if (path.equals("/")) {
                path = "/index.html";
            }

            File file = new File("web" + path);
            if (!file.exists() || file.isDirectory()) {
                file = new File("web/index.html");
            }

            if (!file.exists()) {
                String notFound = "<h1>404 Not Found</h1><p>Resource not found on Placement Server.</p>";
                sendResponse(exchange, 404, notFound, "text/html");
                return;
            }

            String contentType = "text/plain";
            String fname = file.getName().toLowerCase();
            if (fname.endsWith(".html")) contentType = "text/html";
            else if (fname.endsWith(".css")) contentType = "text/css";
            else if (fname.endsWith(".js")) contentType = "application/javascript";
            else if (fname.endsWith(".png")) contentType = "image/png";
            else if (fname.endsWith(".jpg") || fname.endsWith(".jpeg")) contentType = "image/jpeg";
            else if (fname.endsWith(".svg")) contentType = "image/svg+xml";

            byte[] bytes = Files.readAllBytes(file.toPath());
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    // --- Health Check / Database Status Handler ---
    static class HealthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            long start = System.currentTimeMillis();
            String status = "ERROR";
            String dbVersion = "Unknown";
            String currentUser = "Unknown";
            String errorMsg = "";

            try (Connection conn = DBConnection.getConnection()) {
                DatabaseMetaData meta = conn.getMetaData();
                dbVersion = meta.getDatabaseProductVersion();
                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery("SELECT USER FROM dual")) {
                    if (rs.next()) {
                        currentUser = rs.getString(1);
                        status = "CONNECTED";
                    }
                }
                // Repetitive successful health checks are suppressed to reduce terminal noise
            } catch (SQLException e) {
                errorMsg = e.getMessage();
                // Visible logging on failure
                logHttpStart("GET", "/api/health");
                logDbError("DQL", "Health Check", e, System.currentTimeMillis() - start);
                logHttpEnd(500);
            }

            String json = String.format("{\"status\":\"%s\",\"user\":\"%s\",\"version\":\"%s\",\"error\":\"%s\",\"port\":%d}",
                    status, currentUser, escapeJson(dbVersion), escapeJson(errorMsg), DBConnection.getServerPort());

            int code = "CONNECTED".equals(status) ? 200 : 500;
            sendResponse(exchange, code, json, "application/json");
        }
    }

    // --- Login Handler ---
    static class LoginHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 204, "", "application/json");
                return;
            }

            logHttpStart("POST", "/api/login");
            long start = System.currentTimeMillis();

            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\":\"Method Not Allowed\"}", "application/json");
                logHttpEnd(405);
                return;
            }

            Map<String, String> body = parseRequestBody(exchange);
            String username = body.getOrDefault("username", "").trim();
            String password = body.getOrDefault("password", "").trim();

            if (username.isEmpty() || password.isEmpty()) {
                logDbFailed("DQL", "AUTHENTICATE USER", "Username and password required", System.currentTimeMillis() - start);
                sendResponse(exchange, 400, "{\"success\":false,\"message\":\"Username and password required\"}", "application/json");
                logHttpEnd(400);
                return;
            }

            String sql = "SELECT USER_ID, USERNAME, FULL_NAME, ROLE FROM USERS WHERE USERNAME = ? AND PASSWORD = ?";
            try (Connection conn = DBConnection.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {

                pstmt.setString(1, username);
                pstmt.setString(2, password);

                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        int userId = rs.getInt("USER_ID");
                        String uname = rs.getString("USERNAME");
                        String fullName = rs.getString("FULL_NAME");
                        String role = rs.getString("ROLE");

                        logDbOp("DQL", "AUTHENTICATE USER", 1, System.currentTimeMillis() - start, true);

                        String res = String.format("{\"success\":true,\"message\":\"Login successful\",\"user\":{\"id\":%d,\"username\":\"%s\",\"fullName\":\"%s\",\"role\":\"%s\"}}",
                                userId, escapeJson(uname), escapeJson(fullName), escapeJson(role));

                        sendResponse(exchange, 200, res, "application/json");
                        logHttpEnd(200);
                    } else {
                        logDbFailed("DQL", "AUTHENTICATE USER", "Invalid credentials", System.currentTimeMillis() - start);
                        sendResponse(exchange, 401, "{\"success\":false,\"message\":\"Invalid username or password\"}", "application/json");
                        logHttpEnd(401);
                    }
                }
            } catch (SQLException e) {
                logDbError("DQL", "AUTHENTICATE USER", e, System.currentTimeMillis() - start);
                sendResponse(exchange, 500, "{\"success\":false,\"message\":\"Database error: " + escapeJson(e.getMessage()) + "\"}", "application/json");
                logHttpEnd(500);
            }
        }
    }

    // --- Dashboard Handler ---
    static class DashboardHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            long start = System.currentTimeMillis();
            int totalStudents = 0;
            int placedStudents = 0;
            int totalCompanies = 0;
            int totalDrives = 0;
            int totalApplications = 0;

            try (Connection conn = DBConnection.getConnection();
                 Statement stmt = conn.createStatement()) {

                try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM STUDENTS")) {
                    if (rs.next()) totalStudents = rs.getInt(1);
                }
                try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM STUDENTS WHERE STATUS = 'PLACED'")) {
                    if (rs.next()) placedStudents = rs.getInt(1);
                }
                try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM COMPANIES")) {
                    if (rs.next()) totalCompanies = rs.getInt(1);
                }
                try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM PLACEMENT_DRIVES")) {
                    if (rs.next()) totalDrives = rs.getInt(1);
                }
                try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM PLACEMENT_APPLICATIONS")) {
                    if (rs.next()) totalApplications = rs.getInt(1);
                }

                // Summarized single line instead of multi-line database operation block to avoid terminal noise
                long duration = System.currentTimeMillis() - start;
                System.out.println("[HTTP] GET /api/dashboard -> Status: 200 | Metrics refreshed (" + duration + " ms)");

                String json = String.format("{\"totalStudents\":%d,\"placedStudents\":%d,\"totalCompanies\":%d,\"totalDrives\":%d,\"totalApplications\":%d}",
                        totalStudents, placedStudents, totalCompanies, totalDrives, totalApplications);

                sendResponse(exchange, 200, json, "application/json");

            } catch (SQLException e) {
                logHttpStart("GET", "/api/dashboard");
                logDbError("DQL", "Dashboard Metrics Aggregations", e, System.currentTimeMillis() - start);
                sendResponse(exchange, 500, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}", "application/json");
                logHttpEnd(500);
            }
        }
    }

    // --- Students Handler (GET & POST) ---
    static class StudentsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String method = exchange.getRequestMethod();
            if ("OPTIONS".equalsIgnoreCase(method)) {
                sendResponse(exchange, 204, "", "application/json");
                return;
            }

            logHttpStart(method, "/api/students");
            long start = System.currentTimeMillis();

            if ("GET".equalsIgnoreCase(method)) {
                String sql = "SELECT STUDENT_ID, REGISTER_NO, NAME, DEPARTMENT, CGPA, BACKLOGS, EMAIL, PHONE, STATUS FROM STUDENTS ORDER BY STUDENT_ID ASC";
                StringBuilder sb = new StringBuilder("[");

                try (Connection conn = DBConnection.getConnection();
                     Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery(sql)) {

                    boolean first = true;
                    int count = 0;
                    while (rs.next()) {
                        if (!first) sb.append(",");
                        first = false;
                        sb.append(String.format("{\"id\":%d,\"registerNo\":\"%s\",\"name\":\"%s\",\"department\":\"%s\",\"cgpa\":%.2f,\"backlogs\":%d,\"email\":\"%s\",\"phone\":\"%s\",\"status\":\"%s\"}",
                                rs.getInt("STUDENT_ID"),
                                escapeJson(rs.getString("REGISTER_NO")),
                                escapeJson(rs.getString("NAME")),
                                escapeJson(rs.getString("DEPARTMENT")),
                                rs.getDouble("CGPA"),
                                rs.getInt("BACKLOGS"),
                                escapeJson(rs.getString("EMAIL")),
                                escapeJson(rs.getString("PHONE")),
                                escapeJson(rs.getString("STATUS"))
                        ));
                        count++;
                    }
                    sb.append("]");

                    logDbOp("DQL", "FETCH STUDENTS", count, System.currentTimeMillis() - start, true);

                    sendResponse(exchange, 200, sb.toString(), "application/json");
                    logHttpEnd(200);

                } catch (SQLException e) {
                    logDbError("DQL", "FETCH STUDENTS", e, System.currentTimeMillis() - start);
                    sendResponse(exchange, 500, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}", "application/json");
                    logHttpEnd(500);
                }

            } else if ("POST".equalsIgnoreCase(method)) {
                Map<String, String> body = parseRequestBody(exchange);
                String regNo = body.getOrDefault("registerNo", "").trim();
                String name = body.getOrDefault("name", "").trim();
                String dept = body.getOrDefault("department", "").trim();
                String email = body.getOrDefault("email", "").trim();
                String phone = body.getOrDefault("phone", "").trim();
                double cgpa = Double.parseDouble(body.getOrDefault("cgpa", "0.0"));
                int backlogs = Integer.parseInt(body.getOrDefault("backlogs", "0"));

                if (regNo.isEmpty() || name.isEmpty() || dept.isEmpty() || email.isEmpty()) {
                    logDbFailed("DML", "INSERT STUDENT", "Required fields missing", System.currentTimeMillis() - start);
                    sendResponse(exchange, 400, "{\"success\":false,\"message\":\"Required fields missing\"}", "application/json");
                    logHttpEnd(400);
                    return;
                }

                String sql = "INSERT INTO STUDENTS (STUDENT_ID, REGISTER_NO, NAME, DEPARTMENT, CGPA, BACKLOGS, EMAIL, PHONE, STATUS) " +
                        "VALUES (STUDENTS_SEQ.NEXTVAL, ?, ?, ?, ?, ?, ?, ?, 'UNPLACED')";

                try (Connection conn = DBConnection.getConnection();
                     PreparedStatement pstmt = conn.prepareStatement(sql)) {

                    pstmt.setString(1, regNo);
                    pstmt.setString(2, name);
                    pstmt.setString(3, dept);
                    pstmt.setDouble(4, cgpa);
                    pstmt.setInt(5, backlogs);
                    pstmt.setString(6, email);
                    pstmt.setString(7, phone);

                    int rows = pstmt.executeUpdate();
                    logDbOp("DML", "INSERT STUDENT", rows, System.currentTimeMillis() - start, false);

                    String res = String.format("{\"success\":true,\"message\":\"Student added successfully (%d row affected)\"}", rows);
                    sendResponse(exchange, 200, res, "application/json");
                    logHttpEnd(200);

                } catch (SQLException e) {
                    logDbError("DML", "INSERT STUDENT", e, System.currentTimeMillis() - start);
                    String msg = e.getErrorCode() == 1 ? "Student with Register No or Email already exists!" : e.getMessage();
                    sendResponse(exchange, 400, "{\"success\":false,\"message\":\"" + escapeJson(msg) + "\"}", "application/json");
                    logHttpEnd(400);
                }
            } else {
                sendResponse(exchange, 405, "{\"error\":\"Method Not Allowed\"}", "application/json");
                logHttpEnd(405);
            }
        }
    }

    // --- Companies Handler (GET & POST) ---
    static class CompaniesHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String method = exchange.getRequestMethod();
            if ("OPTIONS".equalsIgnoreCase(method)) {
                sendResponse(exchange, 204, "", "application/json");
                return;
            }

            logHttpStart(method, "/api/companies");
            long start = System.currentTimeMillis();

            if ("GET".equalsIgnoreCase(method)) {
                String sql = "SELECT COMPANY_ID, COMPANY_NAME, INDUSTRY, LOCATION, HR_EMAIL, HR_PHONE, WEBSITE FROM COMPANIES ORDER BY COMPANY_ID ASC";
                StringBuilder sb = new StringBuilder("[");

                try (Connection conn = DBConnection.getConnection();
                     Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery(sql)) {

                    boolean first = true;
                    int count = 0;
                    while (rs.next()) {
                        if (!first) sb.append(",");
                        first = false;
                        sb.append(String.format("{\"id\":%d,\"name\":\"%s\",\"industry\":\"%s\",\"location\":\"%s\",\"hrEmail\":\"%s\",\"hrPhone\":\"%s\",\"website\":\"%s\"}",
                                rs.getInt("COMPANY_ID"),
                                escapeJson(rs.getString("COMPANY_NAME")),
                                escapeJson(rs.getString("INDUSTRY")),
                                escapeJson(rs.getString("LOCATION")),
                                escapeJson(rs.getString("HR_EMAIL")),
                                escapeJson(rs.getString("HR_PHONE")),
                                escapeJson(rs.getString("WEBSITE"))
                        ));
                        count++;
                    }
                    sb.append("]");

                    logDbOp("DQL", "FETCH COMPANIES", count, System.currentTimeMillis() - start, true);

                    sendResponse(exchange, 200, sb.toString(), "application/json");
                    logHttpEnd(200);

                } catch (SQLException e) {
                    logDbError("DQL", "FETCH COMPANIES", e, System.currentTimeMillis() - start);
                    sendResponse(exchange, 500, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}", "application/json");
                    logHttpEnd(500);
                }

            } else if ("POST".equalsIgnoreCase(method)) {
                Map<String, String> body = parseRequestBody(exchange);
                String name = body.getOrDefault("name", "").trim();
                String industry = body.getOrDefault("industry", "").trim();
                String location = body.getOrDefault("location", "").trim();
                String hrEmail = body.getOrDefault("hrEmail", "").trim();
                String hrPhone = body.getOrDefault("hrPhone", "").trim();
                String website = body.getOrDefault("website", "").trim();

                if (name.isEmpty() || industry.isEmpty() || location.isEmpty()) {
                    logDbFailed("DML", "INSERT COMPANY", "Required fields missing", System.currentTimeMillis() - start);
                    sendResponse(exchange, 400, "{\"success\":false,\"message\":\"Company name, industry, and location required\"}", "application/json");
                    logHttpEnd(400);
                    return;
                }

                String sql = "INSERT INTO COMPANIES (COMPANY_ID, COMPANY_NAME, INDUSTRY, LOCATION, HR_EMAIL, HR_PHONE, WEBSITE) " +
                        "VALUES (COMPANIES_SEQ.NEXTVAL, ?, ?, ?, ?, ?, ?)";

                try (Connection conn = DBConnection.getConnection();
                     PreparedStatement pstmt = conn.prepareStatement(sql)) {

                    pstmt.setString(1, name);
                    pstmt.setString(2, industry);
                    pstmt.setString(3, location);
                    pstmt.setString(4, hrEmail);
                    pstmt.setString(5, hrPhone);
                    pstmt.setString(6, website);

                    int rows = pstmt.executeUpdate();
                    logDbOp("DML", "INSERT COMPANY", rows, System.currentTimeMillis() - start, false);

                    sendResponse(exchange, 200, "{\"success\":true,\"message\":\"Company added successfully\"}", "application/json");
                    logHttpEnd(200);

                } catch (SQLException e) {
                    logDbError("DML", "INSERT COMPANY", e, System.currentTimeMillis() - start);
                    String msg = e.getErrorCode() == 1 ? "Company with this name already exists!" : e.getMessage();
                    sendResponse(exchange, 400, "{\"success\":false,\"message\":\"" + escapeJson(msg) + "\"}", "application/json");
                    logHttpEnd(400);
                }
            } else {
                sendResponse(exchange, 405, "{\"error\":\"Method Not Allowed\"}", "application/json");
                logHttpEnd(405);
            }
        }
    }

    // --- Placement Drives Handler (GET & POST) ---
    static class DrivesHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String method = exchange.getRequestMethod();
            if ("OPTIONS".equalsIgnoreCase(method)) {
                sendResponse(exchange, 204, "", "application/json");
                return;
            }

            logHttpStart(method, "/api/drives");
            long start = System.currentTimeMillis();

            if ("GET".equalsIgnoreCase(method)) {
                String sql = "SELECT d.DRIVE_ID, d.COMPANY_ID, c.COMPANY_NAME, d.JOB_ROLE, d.PACKAGE_LPA, " +
                        "d.MIN_CGPA, d.MAX_BACKLOGS, TO_CHAR(d.DRIVE_DATE, 'YYYY-MM-DD') AS DRIVE_DATE, d.STATUS " +
                        "FROM PLACEMENT_DRIVES d JOIN COMPANIES c ON d.COMPANY_ID = c.COMPANY_ID " +
                        "ORDER BY d.DRIVE_DATE DESC";
                StringBuilder sb = new StringBuilder("[");

                try (Connection conn = DBConnection.getConnection();
                     Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery(sql)) {

                    boolean first = true;
                    int count = 0;
                    while (rs.next()) {
                        if (!first) sb.append(",");
                        first = false;
                        sb.append(String.format("{\"id\":%d,\"companyId\":%d,\"companyName\":\"%s\",\"jobRole\":\"%s\",\"packageLpa\":%.2f,\"minCgpa\":%.2f,\"maxBacklogs\":%d,\"driveDate\":\"%s\",\"status\":\"%s\"}",
                                rs.getInt("DRIVE_ID"),
                                rs.getInt("COMPANY_ID"),
                                escapeJson(rs.getString("COMPANY_NAME")),
                                escapeJson(rs.getString("JOB_ROLE")),
                                rs.getDouble("PACKAGE_LPA"),
                                rs.getDouble("MIN_CGPA"),
                                rs.getInt("MAX_BACKLOGS"),
                                rs.getString("DRIVE_DATE"),
                                escapeJson(rs.getString("STATUS"))
                        ));
                        count++;
                    }
                    sb.append("]");

                    logDbOp("DQL", "FETCH PLACEMENT DRIVES", count, System.currentTimeMillis() - start, true);

                    sendResponse(exchange, 200, sb.toString(), "application/json");
                    logHttpEnd(200);

                } catch (SQLException e) {
                    logDbError("DQL", "FETCH PLACEMENT DRIVES", e, System.currentTimeMillis() - start);
                    sendResponse(exchange, 500, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}", "application/json");
                    logHttpEnd(500);
                }

            } else if ("POST".equalsIgnoreCase(method)) {
                Map<String, String> body = parseRequestBody(exchange);
                int companyId = Integer.parseInt(body.getOrDefault("companyId", "0"));
                String jobRole = body.getOrDefault("jobRole", "").trim();
                double packageLpa = Double.parseDouble(body.getOrDefault("packageLpa", "0.0"));
                double minCgpa = Double.parseDouble(body.getOrDefault("minCgpa", "0.0"));
                int maxBacklogs = Integer.parseInt(body.getOrDefault("maxBacklogs", "0"));
                String driveDate = body.getOrDefault("driveDate", "").trim();

                if (companyId <= 0 || jobRole.isEmpty() || driveDate.isEmpty()) {
                    logDbFailed("DML", "INSERT PLACEMENT DRIVE", "Required fields missing", System.currentTimeMillis() - start);
                    sendResponse(exchange, 400, "{\"success\":false,\"message\":\"Company, job role, and drive date are required\"}", "application/json");
                    logHttpEnd(400);
                    return;
                }

                String sql = "INSERT INTO PLACEMENT_DRIVES (DRIVE_ID, COMPANY_ID, JOB_ROLE, PACKAGE_LPA, MIN_CGPA, MAX_BACKLOGS, DRIVE_DATE, STATUS) " +
                        "VALUES (DRIVES_SEQ.NEXTVAL, ?, ?, ?, ?, ?, TO_DATE(?, 'YYYY-MM-DD'), 'ACTIVE')";

                try (Connection conn = DBConnection.getConnection();
                     PreparedStatement pstmt = conn.prepareStatement(sql)) {

                    pstmt.setInt(1, companyId);
                    pstmt.setString(2, jobRole);
                    pstmt.setDouble(3, packageLpa);
                    pstmt.setDouble(4, minCgpa);
                    pstmt.setInt(5, maxBacklogs);
                    pstmt.setString(6, driveDate);

                    int rows = pstmt.executeUpdate();
                    logDbOp("DML", "INSERT PLACEMENT DRIVE", rows, System.currentTimeMillis() - start, false);

                    sendResponse(exchange, 200, "{\"success\":true,\"message\":\"Placement Drive added successfully\"}", "application/json");
                    logHttpEnd(200);

                } catch (SQLException e) {
                    logDbError("DML", "INSERT PLACEMENT DRIVE", e, System.currentTimeMillis() - start);
                    sendResponse(exchange, 400, "{\"success\":false,\"message\":\"" + escapeJson(e.getMessage()) + "\"}", "application/json");
                    logHttpEnd(400);
                }
            } else {
                sendResponse(exchange, 405, "{\"error\":\"Method Not Allowed\"}", "application/json");
                logHttpEnd(405);
            }
        }
    }

    // --- Eligibility Processing Handler ---
    static class EligibilityHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String method = exchange.getRequestMethod();
            if ("OPTIONS".equalsIgnoreCase(method)) {
                sendResponse(exchange, 204, "", "application/json");
                return;
            }

            logHttpStart(method, "/api/check-eligibility");
            long start = System.currentTimeMillis();

            Map<String, String> params;
            if ("GET".equalsIgnoreCase(method)) {
                params = parseQueryParams(exchange.getRequestURI().getQuery());
            } else {
                params = parseRequestBody(exchange);
            }

            int studentId = Integer.parseInt(params.getOrDefault("studentId", "0"));
            int driveId = Integer.parseInt(params.getOrDefault("driveId", "0"));

            if (studentId <= 0 || driveId <= 0) {
                logDbFailed("DQL", "CHECK ELIGIBILITY", "studentId and driveId are required", System.currentTimeMillis() - start);
                sendResponse(exchange, 400, "{\"success\":false,\"message\":\"studentId and driveId are required\"}", "application/json");
                logHttpEnd(400);
                return;
            }

            String sql = "SELECT s.STUDENT_ID, s.NAME, s.REGISTER_NO, s.CGPA, s.BACKLOGS, " +
                    "d.DRIVE_ID, d.JOB_ROLE, d.MIN_CGPA, d.MAX_BACKLOGS, c.COMPANY_NAME " +
                    "FROM STUDENTS s, PLACEMENT_DRIVES d, COMPANIES c " +
                    "WHERE s.STUDENT_ID = ? AND d.DRIVE_ID = ? AND d.COMPANY_ID = c.COMPANY_ID";

            try (Connection conn = DBConnection.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {

                pstmt.setInt(1, studentId);
                pstmt.setInt(2, driveId);

                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        String studentName = rs.getString("NAME");
                        String regNo = rs.getString("REGISTER_NO");
                        double studentCgpa = rs.getDouble("CGPA");
                        int studentBacklogs = rs.getInt("BACKLOGS");

                        String companyName = rs.getString("COMPANY_NAME");
                        String jobRole = rs.getString("JOB_ROLE");
                        double minCgpa = rs.getDouble("MIN_CGPA");
                        int maxBacklogs = rs.getInt("MAX_BACKLOGS");

                        // Simple, clear eligibility business logic:
                        // Student is eligible if CGPA >= minCgpa AND backlogs <= maxBacklogs
                        boolean cgpaEligible = studentCgpa >= minCgpa;
                        boolean backlogsEligible = studentBacklogs <= maxBacklogs;
                        boolean overallEligible = cgpaEligible && backlogsEligible;

                        String reason;
                        if (overallEligible) {
                            reason = String.format("Eligible! Student CGPA (%.2f) meets minimum criteria (%.2f) with no pending backlogs.",
                                    studentCgpa, minCgpa);
                        } else if (!cgpaEligible && !backlogsEligible) {
                            reason = String.format("Not Eligible: Student CGPA (%.2f) is below %.2f AND student has %d backlogs (max %d allowed).",
                                    studentCgpa, minCgpa, studentBacklogs, maxBacklogs);
                        } else if (!cgpaEligible) {
                            reason = String.format("Not Eligible: Student CGPA (%.2f) is below required minimum (%.2f).",
                                    studentCgpa, minCgpa);
                        } else {
                            reason = String.format("Not Eligible: Student has %d backlog(s), company allows maximum %d.",
                                    studentBacklogs, maxBacklogs);
                        }

                        logDbOp("DQL", "CHECK ELIGIBILITY", 1, System.currentTimeMillis() - start, true);

                        String json = String.format("{\"success\":true,\"studentName\":\"%s\",\"regNo\":\"%s\",\"studentCgpa\":%.2f,\"studentBacklogs\":%d,\"companyName\":\"%s\",\"jobRole\":\"%s\",\"minCgpa\":%.2f,\"maxBacklogs\":%d,\"isEligible\":%b,\"status\":\"%s\",\"reason\":\"%s\"}",
                                escapeJson(studentName), escapeJson(regNo), studentCgpa, studentBacklogs,
                                escapeJson(companyName), escapeJson(jobRole), minCgpa, maxBacklogs,
                                overallEligible, (overallEligible ? "ELIGIBLE" : "NOT ELIGIBLE"), escapeJson(reason));

                        sendResponse(exchange, 200, json, "application/json");
                        logHttpEnd(200);
                    } else {
                        logDbFailed("DQL", "CHECK ELIGIBILITY", "Student or Drive record not found", System.currentTimeMillis() - start);
                        sendResponse(exchange, 404, "{\"success\":false,\"message\":\"Student or Drive record not found\"}", "application/json");
                        logHttpEnd(404);
                    }
                }

            } catch (SQLException e) {
                logDbError("DQL", "CHECK ELIGIBILITY", e, System.currentTimeMillis() - start);
                sendResponse(exchange, 500, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}", "application/json");
                logHttpEnd(500);
            }
        }
    }

    // --- Placement Applications Handler (GET, POST & status UPDATE) ---
    static class ApplicationsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String method = exchange.getRequestMethod();
            if ("OPTIONS".equalsIgnoreCase(method)) {
                sendResponse(exchange, 204, "", "application/json");
                return;
            }

            logHttpStart(method, "/api/applications");
            long start = System.currentTimeMillis();

            if ("GET".equalsIgnoreCase(method)) {
                String sql = "SELECT a.APPLICATION_ID, a.STUDENT_ID, s.NAME AS STUDENT_NAME, s.REGISTER_NO, s.CGPA, " +
                        "a.DRIVE_ID, c.COMPANY_NAME, d.JOB_ROLE, d.PACKAGE_LPA, d.MIN_CGPA, " +
                        "TO_CHAR(a.APPLICATION_DATE, 'YYYY-MM-DD') AS APP_DATE, a.ELIGIBILITY_STATUS, a.STATUS, a.REMARKS " +
                        "FROM PLACEMENT_APPLICATIONS a " +
                        "JOIN STUDENTS s ON a.STUDENT_ID = s.STUDENT_ID " +
                        "JOIN PLACEMENT_DRIVES d ON a.DRIVE_ID = d.DRIVE_ID " +
                        "JOIN COMPANIES c ON d.COMPANY_ID = c.COMPANY_ID " +
                        "ORDER BY a.APPLICATION_ID DESC";

                StringBuilder sb = new StringBuilder("[");

                try (Connection conn = DBConnection.getConnection();
                     Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery(sql)) {

                    boolean first = true;
                    int count = 0;
                    while (rs.next()) {
                        if (!first) sb.append(",");
                        first = false;
                        sb.append(String.format("{\"applicationId\":%d,\"studentId\":%d,\"studentName\":\"%s\",\"regNo\":\"%s\",\"studentCgpa\":%.2f,\"driveId\":%d,\"companyName\":\"%s\",\"jobRole\":\"%s\",\"packageLpa\":%.2f,\"minCgpa\":%.2f,\"applicationDate\":\"%s\",\"eligibilityStatus\":\"%s\",\"status\":\"%s\",\"remarks\":\"%s\"}",
                                rs.getInt("APPLICATION_ID"),
                                rs.getInt("STUDENT_ID"),
                                escapeJson(rs.getString("STUDENT_NAME")),
                                escapeJson(rs.getString("REGISTER_NO")),
                                rs.getDouble("CGPA"),
                                rs.getInt("DRIVE_ID"),
                                escapeJson(rs.getString("COMPANY_NAME")),
                                escapeJson(rs.getString("JOB_ROLE")),
                                rs.getDouble("PACKAGE_LPA"),
                                rs.getDouble("MIN_CGPA"),
                                rs.getString("APP_DATE"),
                                escapeJson(rs.getString("ELIGIBILITY_STATUS")),
                                escapeJson(rs.getString("STATUS")),
                                escapeJson(rs.getString("REMARKS"))
                        ));
                        count++;
                    }
                    sb.append("]");

                    logDbOp("DQL", "FETCH PLACEMENT APPLICATIONS", count, System.currentTimeMillis() - start, true);

                    sendResponse(exchange, 200, sb.toString(), "application/json");
                    logHttpEnd(200);

                } catch (SQLException e) {
                    logDbError("DQL", "FETCH PLACEMENT APPLICATIONS", e, System.currentTimeMillis() - start);
                    sendResponse(exchange, 500, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}", "application/json");
                    logHttpEnd(500);
                }

            } else if ("POST".equalsIgnoreCase(method)) {
                Map<String, String> body = parseRequestBody(exchange);
                String action = body.getOrDefault("action", "apply");

                if ("update_status".equals(action)) {
                    int appId = Integer.parseInt(body.getOrDefault("applicationId", "0"));
                    String newStatus = body.getOrDefault("status", "APPLIED").trim();
                    String remarks = body.getOrDefault("remarks", "").trim();

                    if (appId <= 0) {
                        logDbFailed("DML", "UPDATE APPLICATION STATUS", "Application ID required", System.currentTimeMillis() - start);
                        sendResponse(exchange, 400, "{\"success\":false,\"message\":\"Application ID required\"}", "application/json");
                        logHttpEnd(400);
                        return;
                    }

                    String updateSql = "UPDATE PLACEMENT_APPLICATIONS SET STATUS = ?, REMARKS = ? WHERE APPLICATION_ID = ?";
                    try (Connection conn = DBConnection.getConnection();
                         PreparedStatement pstmt = conn.prepareStatement(updateSql)) {

                        pstmt.setString(1, newStatus);
                        pstmt.setString(2, remarks);
                        pstmt.setInt(3, appId);
                        int rows = pstmt.executeUpdate();

                        if ("PLACED".equalsIgnoreCase(newStatus)) {
                            try (PreparedStatement sPstmt = conn.prepareStatement(
                                    "UPDATE STUDENTS SET STATUS = 'PLACED' WHERE STUDENT_ID = (SELECT STUDENT_ID FROM PLACEMENT_APPLICATIONS WHERE APPLICATION_ID = ?)")) {
                                sPstmt.setInt(1, appId);
                                sPstmt.executeUpdate();
                            }
                        }

                        logDbOp("DML", "UPDATE APPLICATION STATUS", rows, System.currentTimeMillis() - start, false);

                        sendResponse(exchange, 200, "{\"success\":true,\"message\":\"Application updated successfully\"}", "application/json");
                        logHttpEnd(200);
                    } catch (SQLException e) {
                        logDbError("DML", "UPDATE APPLICATION STATUS", e, System.currentTimeMillis() - start);
                        sendResponse(exchange, 500, "{\"success\":false,\"message\":\"" + escapeJson(e.getMessage()) + "\"}", "application/json");
                        logHttpEnd(500);
                    }
                    return;
                }

                // Default action: Create new application
                int studentId = Integer.parseInt(body.getOrDefault("studentId", "0"));
                int driveId = Integer.parseInt(body.getOrDefault("driveId", "0"));
                String remarks = body.getOrDefault("remarks", "").trim();

                if (studentId <= 0 || driveId <= 0) {
                    logDbFailed("DML", "INSERT PLACEMENT APPLICATION", "Select both student and drive", System.currentTimeMillis() - start);
                    sendResponse(exchange, 400, "{\"success\":false,\"message\":\"Select both student and drive\"}", "application/json");
                    logHttpEnd(400);
                    return;
                }

                // Check eligibility dynamically before saving
                String eligSql = "SELECT s.CGPA, s.BACKLOGS, d.MIN_CGPA, d.MAX_BACKLOGS FROM STUDENTS s, PLACEMENT_DRIVES d WHERE s.STUDENT_ID = ? AND d.DRIVE_ID = ?";
                String eligStatus = "ELIGIBLE";
                try (Connection conn = DBConnection.getConnection();
                     PreparedStatement ePstmt = conn.prepareStatement(eligSql)) {

                    ePstmt.setInt(1, studentId);
                    ePstmt.setInt(2, driveId);
                    try (ResultSet rs = ePstmt.executeQuery()) {
                        if (rs.next()) {
                            double sCgpa = rs.getDouble("CGPA");
                            int sBacklogs = rs.getInt("BACKLOGS");
                            double dMinCgpa = rs.getDouble("MIN_CGPA");
                            int dMaxBacklogs = rs.getInt("MAX_BACKLOGS");

                            if (sCgpa < dMinCgpa || sBacklogs > dMaxBacklogs) {
                                eligStatus = "NOT ELIGIBLE";
                            }
                        } else {
                            logDbFailed("DML", "INSERT PLACEMENT APPLICATION", "Student or Drive does not exist", System.currentTimeMillis() - start);
                            sendResponse(exchange, 404, "{\"success\":false,\"message\":\"Student or Drive does not exist\"}", "application/json");
                            logHttpEnd(404);
                            return;
                        }
                    }

                    String initialStatus = eligStatus.equals("ELIGIBLE") ? "APPLIED" : "REJECTED";
                    if (remarks.isEmpty() && eligStatus.equals("NOT ELIGIBLE")) {
                        remarks = "Automatically rejected: CGPA or backlog criteria not met.";
                    }

                    String insSql = "INSERT INTO PLACEMENT_APPLICATIONS (APPLICATION_ID, STUDENT_ID, DRIVE_ID, APPLICATION_DATE, ELIGIBILITY_STATUS, STATUS, REMARKS) " +
                            "VALUES (APPLICATIONS_SEQ.NEXTVAL, ?, ?, SYSDATE, ?, ?, ?)";

                    try (PreparedStatement iPstmt = conn.prepareStatement(insSql)) {
                        iPstmt.setInt(1, studentId);
                        iPstmt.setInt(2, driveId);
                        iPstmt.setString(3, eligStatus);
                        iPstmt.setString(4, initialStatus);
                        iPstmt.setString(5, remarks);
                        int rows = iPstmt.executeUpdate();

                        logDbOp("DML", "INSERT PLACEMENT APPLICATION", rows, System.currentTimeMillis() - start, false);

                        String res = String.format("{\"success\":true,\"message\":\"Application submitted (%s). Status: %s\"}", eligStatus, initialStatus);
                        sendResponse(exchange, 200, res, "application/json");
                        logHttpEnd(200);
                    }

                } catch (SQLException e) {
                    logDbError("DML", "INSERT PLACEMENT APPLICATION", e, System.currentTimeMillis() - start);
                    String msg = e.getErrorCode() == 1 ? "This student has already applied for this placement drive!" : e.getMessage();
                    sendResponse(exchange, 400, "{\"success\":false,\"message\":\"" + escapeJson(msg) + "\"}", "application/json");
                    logHttpEnd(400);
                }
            } else {
                sendResponse(exchange, 405, "{\"error\":\"Method Not Allowed\"}", "application/json");
                logHttpEnd(405);
            }
        }
    }

    // --- Dedicated SQL Queries Demonstrator Handler ---
    static class SqlQueryHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 204, "", "application/json");
                return;
            }

            logHttpStart("POST", "/api/query");
            long start = System.currentTimeMillis();

            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\":\"Method Not Allowed\"}", "application/json");
                logHttpEnd(405);
                return;
            }

            Map<String, String> body = parseRequestBody(exchange);
            String sql = body.getOrDefault("query", "").trim();

            if (sql.isEmpty()) {
                logDbFailed("SQL", "EXECUTE QUERY", "SQL query cannot be empty", System.currentTimeMillis() - start);
                sendResponse(exchange, 400, "{\"success\":false,\"message\":\"SQL query cannot be empty\"}", "application/json");
                logHttpEnd(400);
                return;
            }

            if (sql.endsWith(";")) {
                sql = sql.substring(0, sql.length() - 1).trim();
            }

            // Safety filter: Allow educational placement queries; block destructive administrative commands
            String upper = sql.toUpperCase();
            if (upper.startsWith("DROP DATABASE") || upper.startsWith("SHUTDOWN") || upper.contains("GRANT") || upper.contains("USER ")) {
                logDbFailed("SQL", "EXECUTE QUERY", "Restricted administrative command rejected", System.currentTimeMillis() - start);
                sendResponse(exchange, 403, "{\"success\":false,\"message\":\"Restricted command. Only educational DDL/DML/DQL placement queries allowed.\"}", "application/json");
                logHttpEnd(403);
                return;
            }

            try (Connection conn = DBConnection.getConnection();
                 Statement stmt = conn.createStatement()) {

                boolean hasResultSet = stmt.execute(sql);

                if (hasResultSet) {
                    try (ResultSet rs = stmt.getResultSet()) {
                        ResultSetMetaData md = rs.getMetaData();
                        int colCount = md.getColumnCount();

                        StringBuilder colsJson = new StringBuilder("[");
                        for (int i = 1; i <= colCount; i++) {
                            if (i > 1) colsJson.append(",");
                            colsJson.append("\"").append(escapeJson(md.getColumnLabel(i))).append("\"");
                        }
                        colsJson.append("]");

                        StringBuilder rowsJson = new StringBuilder("[");
                        boolean firstRow = true;
                        int rowCount = 0;
                        while (rs.next()) {
                            if (!firstRow) rowsJson.append(",");
                            firstRow = false;
                            rowsJson.append("[");
                            for (int i = 1; i <= colCount; i++) {
                                if (i > 1) rowsJson.append(",");
                                String val = rs.getString(i);
                                rowsJson.append(val == null ? "null" : "\"" + escapeJson(val) + "\"");
                            }
                            rowsJson.append("]");
                            rowCount++;
                        }
                        rowsJson.append("]");

                        logDbOp("DQL", truncateSql(sql), rowCount, System.currentTimeMillis() - start, true);

                        String res = String.format("{\"success\":true,\"type\":\"DQL\",\"columns\":%s,\"rows\":%s,\"rowCount\":%d,\"message\":\"Query executed successfully (%d rows returned)\"}",
                                colsJson.toString(), rowsJson.toString(), rowCount, rowCount);

                        sendResponse(exchange, 200, res, "application/json");
                        logHttpEnd(200);
                    }
                } else {
                    int updateCount = stmt.getUpdateCount();
                    String queryType = "DML";
                    if (upper.startsWith("CREATE") || upper.startsWith("ALTER") || upper.startsWith("DROP")) {
                        queryType = "DDL";
                    }

                    logDbOp(queryType, truncateSql(sql), updateCount, System.currentTimeMillis() - start, false);

                    String res = String.format("{\"success\":true,\"type\":\"%s\",\"affectedRows\":%d,\"message\":\"Command executed successfully. Rows affected: %d\"}",
                            queryType, updateCount, updateCount);
                    sendResponse(exchange, 200, res, "application/json");
                    logHttpEnd(200);
                }

            } catch (SQLException e) {
                logDbError("SQL Execution", truncateSql(sql), e, System.currentTimeMillis() - start);
                String res = String.format("{\"success\":false,\"errorCode\":%d,\"message\":\"ORA-%d: %s\"}",
                        e.getErrorCode(), e.getErrorCode(), escapeJson(e.getMessage()));
                sendResponse(exchange, 400, res, "application/json");
                logHttpEnd(400);
            }
        }

        private static String truncateSql(String sql) {
            if (sql == null) return "";
            sql = sql.replaceAll("\\s+", " ").trim();
            return sql.length() > 60 ? sql.substring(0, 57) + "..." : sql;
        }
    }
}
