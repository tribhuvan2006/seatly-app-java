package com.seatly.app;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Locale;

@Service
public class SeatlyService {
    private final JdbcTemplate jdbc;
    private final SeatAllocationEngine allocationEngine = new SeatAllocationEngine();

    public SeatlyService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Add the new column when upgrading a database created by an older preview build. */
    public void ensureSchemaCompatibility() {
        try {
            jdbc.queryForList("SELECT password_hash FROM users WHERE 1 = 0");
        } catch (DataAccessException missingColumn) {
            jdbc.execute("ALTER TABLE users ADD COLUMN password_hash VARCHAR(255)");
        }
    }

    public void seedDemoData() {
        String demoStudentPassword = "Student@123";
        String demoFacultyPassword = "Faculty@123";
        String[][] seedStudents = {
                {"99250040069", "Bandi Chakradhar Reddy", "chakradharr@student.demo", "CSE - AIML"},
                {"99250040072", "B. Venu", "venu@student.demo", "CSE - AIML"},
                {"99250040073", "B. Kowshik", "kowshik@student.demo", "CSE - AIML"},
                {"99250040078", "Bandi Tribhuvansai", "tribhuvansai@student.demo", "CSE - AIML"},
                {"99250040081", "A. Harish", "harish@student.demo", "CSE - AIML"},
                {"99250040084", "K. Sai Teja", "saiteja@student.demo", "CSE - AIML"},
                {"99250040087", "M. Nikhil", "nikhil@student.demo", "CSE - AIML"},
                {"99250040091", "P. Sravya", "sravya@student.demo", "CSE - AIML"},
                {"99250040102", "R. Divya", "divya@student.demo", "CSE - AIML"},
                {"99250040115", "S. Rohit", "rohit@student.demo", "CSE - AIML"},
                {"99250040122", "V. Keerthi", "keerthi@student.demo", "CSE - AIML"},
                {"99250040131", "T. Akash", "akash@student.demo", "CSE - AIML"},
                {"99250040142", "N. Priyanka", "priyanka@student.demo", "CSE - AIML"},
                {"99250040156", "D. Charan", "charan@student.demo", "CSE - AIML"},
                {"99250040167", "L. Meghana", "meghana@student.demo", "CSE - AIML"}
        };
        for (String[] student : seedStudents) {
            ensureSeedUser(student[0], student[1], student[2], student[3], "STUDENT", demoStudentPassword);
        }
        ensureSeedUser("faculty", "Dr. Priya Sharma", "priya.faculty@college.demo", "Computer Science", "FACULTY", demoFacultyPassword);

        if (count("classrooms") == 0) {
            jdbc.update("INSERT INTO classrooms(name, building, row_count, column_count, capacity) VALUES (?,?,?,?,?)",
                    "C-101", "Main Academic Block · First Floor", 5, 6, 30);
            jdbc.update("INSERT INTO classrooms(name, building, row_count, column_count, capacity) VALUES (?,?,?,?,?)",
                    "C-102", "Main Academic Block · Second Floor", 4, 6, 24);
            jdbc.update("INSERT INTO classrooms(name, building, row_count, column_count, capacity) VALUES (?,?,?,?,?)",
                    "LAB-03", "Innovation Block · Ground Floor", 4, 5, 20);
        }
        if (count("sessions") == 0) {
            long first = insertSession("CAO Mid-Semester Assessment", "Computer Architecture", 1L,
                    LocalDateTime.now().plusDays(1).withHour(10).withMinute(30).withSecond(0).withNano(0), true,
                    "faculty", "Please arrive 15 minutes before the start time.");
            long second = insertSession("Data Structures Lab Evaluation", "Data Structures", 2L,
                    LocalDateTime.now().plusDays(3).withHour(14).withMinute(0).withSecond(0).withNano(0), true,
                    "faculty", "Bring your college ID and lab record.");
            enrollAllStudents(first);
            enrollAllStudents(second);
            allocateSeats(first, "faculty");
            allocateSeats(second, "faculty");
        }
        // Upgrade older databases: populate old empty rosters, allocate remaining seats,
        // and unpublish the plan so faculty reviews it before students rely on it.
        List<Map<String, Object>> oldSessions = jdbc.queryForList("SELECT id FROM sessions");
        for (Map<String, Object> row : oldSessions) {
            long sessionId = ((Number) row.get("id")).longValue();
            Long rosterSize = jdbc.queryForObject("SELECT COUNT(*) FROM session_students WHERE session_id = ?", Long.class, sessionId);
            if (rosterSize != null && rosterSize == 0) {
                enrollAllStudents(sessionId);
                allocateSeats(sessionId, "migration");
                jdbc.update("UPDATE sessions SET published = FALSE WHERE id = ?", sessionId);
            }
        }
        // Passwords are filled only for bundled demo accounts that were created by an earlier build.
        setMissingSeedPassword("faculty", demoFacultyPassword);
        for (String[] student : seedStudents) setMissingSeedPassword(student[0], demoStudentPassword);
    }

    private void ensureSeedUser(String username, String fullName, String email, String department, String role, String password) {
        Long found = jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE username = ?", Long.class, username);
        if (found == null || found == 0) {
            insertUser(username, fullName, email, department, role, PasswordUtil.hash(password));
        }
    }

    private void setMissingSeedPassword(String username, String password) {
        // Avoid an expensive password hash on every startup when this account is already configured.
        if (exists("SELECT COUNT(*) FROM users WHERE username = ? AND (password_hash IS NULL OR password_hash = '')", username)) {
            jdbc.update("UPDATE users SET password_hash = ? WHERE username = ? AND (password_hash IS NULL OR password_hash = '')",
                    PasswordUtil.hash(password), username);
        }
    }

    private void enrollAllStudents(long sessionId) {
        jdbc.update("INSERT INTO session_students(session_id, username) SELECT ?, username FROM users WHERE role = 'STUDENT' " +
                "AND username NOT IN (SELECT username FROM session_students WHERE session_id = ?)", sessionId, sessionId);
    }

    private long count(String table) {
        // The table name comes only from the fixed internal calls above.
        Long result = jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
        return result == null ? 0 : result;
    }

    public Map<String, Object> authenticate(String identifier, String password) {
        if (identifier == null || identifier.isBlank() || password == null || password.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter your registration number/email and password.");
        }
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT username, password_hash FROM users WHERE LOWER(username) = LOWER(?) OR LOWER(email) = LOWER(?)",
                identifier.trim(), identifier.trim());
        if (rows.isEmpty() || !PasswordUtil.verify(password, String.valueOf(rows.get(0).get("password_hash")))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "The login details are incorrect.");
        }
        return findUser(String.valueOf(rows.get(0).get("username")));
    }

    public Map<String, Object> registerStudent(Map<String, Object> body) {
        String username = textValue(body.get("username")).toUpperCase(Locale.ROOT);
        String fullName = textValue(body.get("fullName"));
        String email = textValue(body.get("email")).toLowerCase(Locale.ROOT);
        String department = textValue(body.get("department"));
        String password = textValue(body.get("password"));
        validateStudent(username, fullName, email, department, password);
        if (exists("SELECT COUNT(*) FROM users WHERE username = ?", username)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "That registration number already has an account. Please sign in.");
        }
        if (exists("SELECT COUNT(*) FROM users WHERE LOWER(email) = LOWER(?)", email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "That email is already linked to an account.");
        }
        insertUser(username, fullName, email, department, "STUDENT", PasswordUtil.hash(password));
        return findUser(username);
    }

    public Map<String, Object> createStudent(Map<String, Object> body, Map<String, Object> actor) {
        requireFaculty(actor);
        String username = textValue(body.get("username")).toUpperCase(Locale.ROOT);
        String fullName = textValue(body.get("fullName"));
        String email = textValue(body.get("email")).toLowerCase(Locale.ROOT);
        String department = textValue(body.get("department"));
        String password = textValue(body.get("password"));
        validateStudent(username, fullName, email, department, password);
        if (exists("SELECT COUNT(*) FROM users WHERE username = ?", username)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "That registration number already exists.");
        }
        if (exists("SELECT COUNT(*) FROM users WHERE LOWER(email) = LOWER(?)", email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "That email is already registered.");
        }
        insertUser(username, fullName, email, department, "STUDENT", PasswordUtil.hash(password));
        Map<String, Object> result = new LinkedHashMap<>(findUser(username));
        result.put("message", "Student account created. Share the initial password securely with the student.");
        return result;
    }

    public Map<String, Object> updateStudent(String username, Map<String, Object> body, Map<String, Object> actor) {
        requireFaculty(actor);
        String fullName = textValue(body.get("fullName"));
        String email = textValue(body.get("email")).toLowerCase(Locale.ROOT);
        String department = textValue(body.get("department"));
        if (fullName.isBlank() || email.isBlank() || department.isBlank() || !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a valid name, email, and department.");
        }
        if (!exists("SELECT COUNT(*) FROM users WHERE username = ? AND role = 'STUDENT'", username)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Student not found.");
        }
        if (exists("SELECT COUNT(*) FROM users WHERE LOWER(email) = LOWER(?) AND username <> ?", email, username)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "That email is already used by another account.");
        }
        jdbc.update("UPDATE users SET full_name = ?, email = ?, department = ? WHERE username = ? AND role = 'STUDENT'",
                fullName, email, department, username);
        return findUser(username);
    }

    public List<Map<String, Object>> getStudentDirectory(Map<String, Object> actor, String query) {
        requireFaculty(actor);
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<Map<String, Object>> all = jdbc.queryForList("SELECT username, full_name, email, department FROM users WHERE role = 'STUDENT' ORDER BY username");
        for (Map<String, Object> student : all) {
            String username = String.valueOf(student.get("username"));
            Long assigned = jdbc.queryForObject("SELECT COUNT(*) FROM seat_assignments WHERE username = ?", Long.class, username);
            Long rosterCount = jdbc.queryForObject("SELECT COUNT(*) FROM session_students WHERE username = ?", Long.class, username);
            student.put("assignedPlans", assigned == null ? 0 : assigned);
            student.put("enrolledPlans", rosterCount == null ? 0 : rosterCount);
        }
        if (q.isBlank()) return all;
        return all.stream().filter(student ->
                String.valueOf(student.get("username")).toLowerCase(Locale.ROOT).contains(q)
                || String.valueOf(student.get("full_name")).toLowerCase(Locale.ROOT).contains(q)
                || String.valueOf(student.get("email")).toLowerCase(Locale.ROOT).contains(q)
                || String.valueOf(student.get("department")).toLowerCase(Locale.ROOT).contains(q)).toList();
    }

    private void validateStudent(String username, String fullName, String email, String department, String password) {
        if (username.isBlank() || !username.matches("^[A-Z0-9._-]{4,40}$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Registration number must be 4–40 letters, numbers, dots, underscores, or hyphens.");
        }
        if (fullName.length() < 2 || fullName.length() > 120 || email.length() > 160
                || !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a valid full name and college email address.");
        }
        if (department.isBlank() || department.length() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter your department or course.");
        }
        if (password.length() < 8 || password.length() > 128) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must be 8–128 characters long.");
        }
    }

    private boolean exists(String sql, Object... args) {
        Long count = jdbc.queryForObject(sql, Long.class, args);
        return count != null && count > 0;
    }

    private void insertUser(String username, String fullName, String email, String department, String role, String passwordHash) {
        jdbc.update("INSERT INTO users(username, full_name, email, department, role, password_hash) VALUES (?,?,?,?,?,?)",
                username, fullName, email, department, role, passwordHash);
    }

    private long insertSession(String title, String course, long classroomId, LocalDateTime startsAt,
                               boolean published, String createdBy, String notes) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO sessions(title, course, classroom_id, starts_at, published, created_by, notes) VALUES (?,?,?,?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, title);
            ps.setString(2, course);
            ps.setLong(3, classroomId);
            ps.setTimestamp(4, Timestamp.valueOf(startsAt));
            ps.setBoolean(5, published);
            ps.setString(6, createdBy);
            ps.setString(7, notes);
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) throw new IllegalStateException("Database did not return the new session ID.");
        return key.longValue();
    }

    public Map<String, Object> findUser(String username) {
        return jdbc.query("SELECT username, full_name, email, department, role FROM users WHERE username = ?",
                rs -> {
                    if (!rs.next()) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Demo user not found.");
                    Map<String, Object> user = new LinkedHashMap<>();
                    user.put("username", rs.getString("username"));
                    user.put("fullName", rs.getString("full_name"));
                    user.put("email", rs.getString("email"));
                    user.put("department", rs.getString("department"));
                    user.put("role", rs.getString("role"));
                    return user;
                }, username);
    }

    public List<Map<String, Object>> getClassrooms() {
        return jdbc.query("SELECT id, name, building, row_count, column_count, capacity FROM classrooms ORDER BY id",
                (rs, row) -> {
                    Map<String, Object> c = new LinkedHashMap<>();
                    c.put("id", rs.getLong("id")); c.put("name", rs.getString("name"));
                    c.put("building", rs.getString("building")); c.put("rowCount", rs.getInt("row_count"));
                    c.put("columnCount", rs.getInt("column_count")); c.put("capacity", rs.getInt("capacity"));
                    return c;
                });
    }

    public List<Map<String, Object>> getClassrooms(Map<String, Object> user) {
        if (user == null || user.get("username") == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Please sign in first.");
        return getClassrooms();
    }

    public Map<String, Object> createClassroom(Map<String, Object> body, Map<String, Object> user) {
        requireFaculty(user);
        String name = textValue(body.get("name"));
        String building = textValue(body.get("building"));
        int rows = integerValue(body.get("rows"), 0);
        int columns = integerValue(body.get("columns"), 0);
        if (name.isBlank() || name.length() > 40 || building.isBlank() || building.length() > 160
                || rows < 1 || columns < 1 || rows > 30 || columns > 30) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a room name, building, and valid row/column counts (1–30 each).");
        }
        if (exists("SELECT COUNT(*) FROM classrooms WHERE LOWER(name) = LOWER(?)", name)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A classroom with that name already exists.");
        }
        int capacity = rows * columns;
        jdbc.update("INSERT INTO classrooms(name, building, row_count, column_count, capacity) VALUES (?,?,?,?,?)",
                name, building, rows, columns, capacity);
        return jdbc.queryForMap("SELECT id, name, building, row_count AS rowCount, column_count AS columnCount, capacity FROM classrooms WHERE name = ?", name);
    }

    public List<Map<String, Object>> getSessions(Map<String, Object> user) {
        String role = String.valueOf(user.get("role"));
        String username = String.valueOf(user.get("username"));
        List<Map<String, Object>> rows;
        if ("STUDENT".equals(role)) {
            rows = jdbc.queryForList("SELECT s.* FROM sessions s JOIN session_students ss ON ss.session_id = s.id " +
                    "WHERE s.published = TRUE AND ss.username = ? ORDER BY s.starts_at", username);
        } else {
            rows = jdbc.queryForList("SELECT * FROM sessions WHERE created_by = ? ORDER BY starts_at", username);
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : rows) result.add(sessionView(row, username));
        return result;
    }

    private Map<String, Object> sessionView(Map<String, Object> session, String username) {
        long id = ((Number) session.get("id")).longValue();
        long classroomId = ((Number) session.get("classroom_id")).longValue();
        Map<String, Object> room = jdbc.queryForMap("SELECT * FROM classrooms WHERE id = ?", classroomId);
        List<Map<String, Object>> own = jdbc.queryForList(
                "SELECT seat_code FROM seat_assignments WHERE session_id = ? AND username = ?", id, username);
        long assigned = jdbc.queryForObject("SELECT COUNT(*) FROM seat_assignments WHERE session_id = ?", Long.class, id);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id); result.put("title", session.get("title")); result.put("course", session.get("course"));
        result.put("classroomId", classroomId); result.put("classroom", room.get("name")); result.put("building", room.get("building"));
        result.put("startsAt", timestampString(session.get("starts_at"))); result.put("published", session.get("published"));
        result.put("createdBy", session.get("created_by")); result.put("assignedCount", assigned);
        result.put("capacity", room.get("capacity")); result.put("mySeat", own.isEmpty() ? null : own.get(0).get("seat_code"));
        result.put("notes", session.get("notes") == null ? "" : session.get("notes"));
        return result;
    }

    public Map<String, Object> getFacultySummary(String username) {
        long totalStudents = jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE role = 'STUDENT'", Long.class);
        long totalSessions = jdbc.queryForObject("SELECT COUNT(*) FROM sessions WHERE created_by = ?", Long.class, username);
        long assignedSeats = jdbc.queryForObject("SELECT COUNT(*) FROM seat_assignments a JOIN sessions s ON s.id = a.session_id WHERE s.created_by = ?", Long.class, username);
        long publishedSessions = jdbc.queryForObject("SELECT COUNT(*) FROM sessions WHERE created_by = ? AND published = TRUE", Long.class, username);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalStudents", totalStudents); result.put("totalSessions", totalSessions);
        result.put("classrooms", count("classrooms")); result.put("assignedSeats", assignedSeats);
        result.put("publishedSessions", publishedSessions);
        return result;
    }

    public Map<String, Object> getSeatMap(long sessionId, Map<String, Object> user) {
        boolean faculty = "FACULTY".equals(user.get("role")) || "ADMIN".equals(user.get("role"));
        Map<String, Object> session = getOwnedOrAccessibleSession(sessionId, user, faculty);
        boolean isStudent = "STUDENT".equals(user.get("role"));
        if (isStudent && !Boolean.TRUE.equals(session.get("published"))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This seating plan has not been published yet.");
        }
        long classroomId = ((Number) session.get("classroom_id")).longValue();
        Map<String, Object> room = jdbc.queryForMap("SELECT * FROM classrooms WHERE id = ?", classroomId);
        List<Map<String, Object>> assignmentRows = jdbc.queryForList(
                "SELECT a.seat_code, a.username, u.full_name FROM seat_assignments a JOIN users u ON u.username = a.username WHERE a.session_id = ?",
                sessionId);
        Map<String, Map<String, Object>> bySeat = new HashMap<>();
        for (Map<String, Object> a : assignmentRows) bySeat.put(String.valueOf(a.get("seat_code")), a);
        String ownUsername = String.valueOf(user.get("username"));
        int rowCount = ((Number) room.get("row_count")).intValue();
        int columnCount = ((Number) room.get("column_count")).intValue();
        List<Map<String, Object>> seats = new ArrayList<>();
        for (int row = 1; row <= rowCount; row++) {
            for (int col = 1; col <= columnCount; col++) {
                String code = "R" + row + "-C" + col;
                Map<String, Object> allocation = bySeat.get(code);
                boolean occupied = allocation != null;
                boolean mine = occupied && ownUsername.equals(String.valueOf(allocation.get("username")));
                Map<String, Object> seat = new LinkedHashMap<>();
                seat.put("seatCode", code); seat.put("occupied", occupied); seat.put("mine", mine);
                seat.put("occupantName", occupied && !isStudent ? allocation.get("full_name") : null);
                seat.put("occupantUsername", occupied && !isStudent ? allocation.get("username") : null);
                seats.add(seat);
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sessionId", sessionId); result.put("title", session.get("title")); result.put("course", session.get("course"));
        result.put("classroom", room.get("name")); result.put("building", room.get("building"));
        result.put("startsAt", timestampString(session.get("starts_at"))); result.put("published", session.get("published"));
        result.put("rowCount", rowCount); result.put("columnCount", columnCount);
        result.put("assignedCount", assignmentRows.size()); result.put("capacity", room.get("capacity")); result.put("seats", seats);
        return result;
    }

    public Map<String, Object> createSession(Map<String, Object> body, Map<String, Object> user) {
        requireFaculty(user);
        String title = textValue(body.get("title"));
        String course = textValue(body.get("course"));
        if (title.isBlank() || course.isBlank() || body.get("classroomId") == null || body.get("startsAt") == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Please complete all required session details.");
        }
        long classroomId;
        try { classroomId = Long.parseLong(String.valueOf(body.get("classroomId"))); }
        catch (NumberFormatException ex) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a valid classroom."); }
        if (jdbc.queryForList("SELECT id FROM classrooms WHERE id = ?", classroomId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a valid classroom.");
        }
        LocalDateTime startsAt;
        try { startsAt = LocalDateTime.parse(String.valueOf(body.get("startsAt"))); }
        catch (RuntimeException ex) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a valid start date and time."); }
        if (!startsAt.isAfter(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a future date and time.");
        }
        String notes = textValue(body.get("notes"));
        long id = insertSession(title.trim(), course.trim(), classroomId, startsAt, false,
                String.valueOf(user.get("username")), notes);
        enrollAllStudents(id);
        return sessionView(jdbc.queryForMap("SELECT * FROM sessions WHERE id = ?", id), String.valueOf(user.get("username")));
    }

    public Map<String, Object> allocateSeats(long sessionId, Map<String, Object> user) {
        requireFaculty(user);
        Map<String, Object> session = getOwnedOrAccessibleSession(sessionId, user, true);
        int created = allocateSeats(sessionId, String.valueOf(user.get("username")));
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM seat_assignments WHERE session_id = ?", Long.class, sessionId);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("message", "Seat allocation complete. " + total + " student(s) now have a seat.");
        response.put("newAssignments", created);
        return response;
    }

    private int allocateSeats(long sessionId, String ignoredCreator) {
        Map<String, Object> session = jdbc.queryForMap("SELECT * FROM sessions WHERE id = ?", sessionId);
        Map<String, Object> room = jdbc.queryForMap("SELECT * FROM classrooms WHERE id = ?", session.get("classroom_id"));
        List<Map<String, Object>> students = jdbc.queryForList(
                "SELECT u.username, u.full_name FROM users u JOIN session_students ss ON ss.username = u.username " +
                "WHERE ss.session_id = ? AND u.role = 'STUDENT' ORDER BY u.username ASC", sessionId);
        List<String> usernames = students.stream().map(s -> String.valueOf(s.get("username"))).toList();
        List<Map<String, Object>> current = jdbc.queryForList("SELECT username, seat_code FROM seat_assignments WHERE session_id = ?", sessionId);
        Set<String> assignedUsers = new HashSet<>();
        Set<String> occupiedSeats = new LinkedHashSet<>();
        current.forEach(a -> { assignedUsers.add(String.valueOf(a.get("username"))); occupiedSeats.add(String.valueOf(a.get("seat_code"))); });
        List<SeatAllocationEngine.Assignment> plans = allocationEngine.allocate(usernames,
                ((Number) room.get("row_count")).intValue(), ((Number) room.get("column_count")).intValue(), assignedUsers, occupiedSeats);
        for (SeatAllocationEngine.Assignment plan : plans) {
            try {
                jdbc.update("INSERT INTO seat_assignments(session_id, username, seat_code) VALUES (?,?,?)",
                        sessionId, plan.username(), plan.seatCode());
            } catch (DataIntegrityViolationException ex) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "A seat changed during allocation. Please run allocation again.");
            }
        }
        return plans.size();
    }

    public Map<String, Object> getSessionRoster(long sessionId, Map<String, Object> user) {
        requireFaculty(user);
        getOwnedOrAccessibleSession(sessionId, user, true);
        List<Map<String, Object>> students = jdbc.queryForList(
                "SELECT u.username, u.full_name AS fullName, u.email, u.department, " +
                "CASE WHEN ss.username IS NULL THEN FALSE ELSE TRUE END AS enrolled, a.seat_code AS seatCode " +
                "FROM users u LEFT JOIN session_students ss ON ss.username = u.username AND ss.session_id = ? " +
                "LEFT JOIN seat_assignments a ON a.username = u.username AND a.session_id = ? " +
                "WHERE u.role = 'STUDENT' ORDER BY u.username", sessionId, sessionId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sessionId", sessionId);
        result.put("students", students);
        result.put("enrolledCount", students.stream().filter(x -> Boolean.TRUE.equals(x.get("enrolled"))).count());
        return result;
    }

    @Transactional
    public Map<String, Object> replaceSessionRoster(long sessionId, Map<String, Object> body, Map<String, Object> user) {
        requireFaculty(user);
        getOwnedOrAccessibleSession(sessionId, user, true);
        Object raw = body.get("usernames");
        if (!(raw instanceof List<?> incoming)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose one or more students for this session.");
        }
        Set<String> chosen = new LinkedHashSet<>();
        for (Object value : incoming) {
            String username = textValue(value).toUpperCase(Locale.ROOT);
            if (username.isBlank()) continue;
            if (!exists("SELECT COUNT(*) FROM users WHERE username = ? AND role = 'STUDENT'", username)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Student not found: " + username);
            }
            chosen.add(username);
        }
        if (chosen.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select at least one student.");
        jdbc.update("DELETE FROM seat_assignments WHERE session_id = ? AND username NOT IN (" + placeholders(chosen.size()) + ")",
                prepend(sessionId, chosen.toArray()));
        jdbc.update("DELETE FROM session_students WHERE session_id = ?", sessionId);
        for (String username : chosen) {
            jdbc.update("INSERT INTO session_students(session_id, username) VALUES (?,?)", sessionId, username);
        }
        jdbc.update("UPDATE sessions SET published = FALSE WHERE id = ?", sessionId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", "Roster saved. Review allocations and publish the plan again.");
        result.put("enrolledCount", chosen.size());
        result.put("sessionId", sessionId);
        return result;
    }

    @Transactional
    public Map<String, Object> assignSeat(long sessionId, Map<String, Object> body, Map<String, Object> actor) {
        requireFaculty(actor);
        getOwnedOrAccessibleSession(sessionId, actor, true);
        String username = textValue(body.get("username")).toUpperCase(Locale.ROOT);
        String seatCode = textValue(body.get("seatCode")).toUpperCase(Locale.ROOT);
        boolean replaceExisting = Boolean.TRUE.equals(body.get("replaceExisting"));
        if (!exists("SELECT COUNT(*) FROM users WHERE username = ? AND role = 'STUDENT'", username)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a registered student.");
        }
        Map<String, Object> sessionRow = jdbc.queryForMap("SELECT classroom_id FROM sessions WHERE id = ?", sessionId);
        Map<String, Object> room = jdbc.queryForMap("SELECT row_count, column_count FROM classrooms WHERE id = ?", sessionRow.get("classroom_id"));
        int rowCount = ((Number) room.get("row_count")).intValue();
        int columnCount = ((Number) room.get("column_count")).intValue();
        if (!seatCode.matches("R[1-9][0-9]*-C[1-9][0-9]*")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a valid seat on the room map.");
        String[] parts = seatCode.substring(1).split("-C");
        int row = Integer.parseInt(parts[0]);
        int column = Integer.parseInt(parts[1]);
        if (row > rowCount || column > columnCount) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "That seat is outside the classroom layout.");
        if (!exists("SELECT COUNT(*) FROM session_students WHERE session_id = ? AND username = ?", sessionId, username)) {
            jdbc.update("INSERT INTO session_students(session_id, username) VALUES (?,?)", sessionId, username);
        }
        List<Map<String, Object>> occupantRows = jdbc.queryForList(
                "SELECT username FROM seat_assignments WHERE session_id = ? AND seat_code = ?", sessionId, seatCode);
        if (!occupantRows.isEmpty()) {
            String occupant = String.valueOf(occupantRows.get(0).get("username"));
            if (!occupant.equals(username) && !replaceExisting) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "That seat is occupied. Confirm replacement or choose another seat.");
            }
            if (!occupant.equals(username)) jdbc.update("DELETE FROM seat_assignments WHERE session_id = ? AND seat_code = ?", sessionId, seatCode);
        }
        jdbc.update("DELETE FROM seat_assignments WHERE session_id = ? AND username = ?", sessionId, username);
        jdbc.update("INSERT INTO seat_assignments(session_id, username, seat_code) VALUES (?,?,?)", sessionId, username, seatCode);
        jdbc.update("UPDATE sessions SET published = FALSE WHERE id = ?", sessionId);
        return Map.of("message", username + " assigned to " + seatCode + ". Publish the plan again to make the change visible.",
                "username", username, "seatCode", seatCode);
    }

    @Transactional
    public Map<String, Object> removeAssignment(long sessionId, String username, Map<String, Object> actor) {
        requireFaculty(actor);
        getOwnedOrAccessibleSession(sessionId, actor, true);
        jdbc.update("DELETE FROM seat_assignments WHERE session_id = ? AND username = ?", sessionId, username);
        jdbc.update("UPDATE sessions SET published = FALSE WHERE id = ?", sessionId);
        return Map.of("message", "Seat assignment removed. Publish the revised plan after checking it.");
    }

    private int integerValue(Object value, int fallback) {
        try { return Integer.parseInt(String.valueOf(value)); } catch (RuntimeException ex) { return fallback; }
    }

    private String placeholders(int count) {
        return String.join(",", java.util.Collections.nCopies(count, "?"));
    }

    private Object[] prepend(Object first, Object[] rest) {
        Object[] values = new Object[rest.length + 1];
        values[0] = first;
        System.arraycopy(rest, 0, values, 1, rest.length);
        return values;
    }

    public Map<String, Object> publishSession(long sessionId, Map<String, Object> user) {
        requireFaculty(user);
        getOwnedOrAccessibleSession(sessionId, user, true);
        Long assigned = jdbc.queryForObject("SELECT COUNT(*) FROM seat_assignments WHERE session_id = ?", Long.class, sessionId);
        Long roster = jdbc.queryForObject("SELECT COUNT(*) FROM session_students WHERE session_id = ?", Long.class, sessionId);
        if (assigned == null || assigned == 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Allocate seats before publishing this session.");
        if (roster != null && assigned < roster) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only " + assigned + " of " + roster + " students have seats. Choose a larger classroom, reduce the roster, or assign the remaining seats before publishing.");
        }
        jdbc.update("UPDATE sessions SET published = TRUE WHERE id = ?", sessionId);
        return Map.of("message", "Published. Students can now view their seats before the session.");
    }

    public byte[] exportCsv(long sessionId, Map<String, Object> user) {
        requireFaculty(user);
        Map<String, Object> session = getOwnedOrAccessibleSession(sessionId, user, true);
        long classroomId = ((Number) session.get("classroom_id")).longValue();
        Map<String, Object> room = jdbc.queryForMap("SELECT * FROM classrooms WHERE id = ?", classroomId);
        List<Map<String, Object>> assignments = jdbc.queryForList(
                "SELECT a.seat_code, u.full_name, u.username FROM seat_assignments a JOIN users u ON u.username = a.username WHERE a.session_id = ? ORDER BY a.seat_code",
                sessionId);
        StringBuilder csv = new StringBuilder("Seat,Student Name,Registration Number,Session,Classroom,Start Time\r\n");
        for (Map<String, Object> a : assignments) {
            csv.append(csv(a.get("seat_code"))).append(',').append(csv(a.get("full_name"))).append(',')
                    .append(csv(a.get("username"))).append(',').append(csv(session.get("title"))).append(',')
                    .append(csv(room.get("name"))).append(',').append(csv(timestampString(session.get("starts_at")))).append("\r\n");
        }
        return csv.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private String csv(Object value) {
        String text = value == null ? "" : String.valueOf(value);
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }

    private Map<String, Object> getOwnedOrAccessibleSession(long sessionId, Map<String, Object> user, boolean mustOwn) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM sessions WHERE id = ?", sessionId);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found.");
        Map<String, Object> session = rows.get(0);
        if (mustOwn && !String.valueOf(user.get("username")).equals(String.valueOf(session.get("created_by")))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only manage sessions you created.");
        }
        if ("STUDENT".equals(user.get("role"))) {
            if (!Boolean.TRUE.equals(session.get("published"))) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This seating plan has not been published yet.");
            }
            if (!exists("SELECT COUNT(*) FROM session_students WHERE session_id = ? AND username = ?", sessionId, user.get("username"))) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not on the student roster for this session.");
            }
        }
        return session;
    }

    private void requireFaculty(Map<String, Object> user) {
        if (!"FACULTY".equals(user.get("role")) && !"ADMIN".equals(user.get("role"))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Faculty access is required for this action.");
        }
    }

    private String timestampString(Object value) {
        if (value instanceof Timestamp timestamp) return timestamp.toLocalDateTime().toString();
        if (value instanceof LocalDateTime dateTime) return dateTime.toString();
        return String.valueOf(value);
    }

    private String textValue(Object value) { return value == null ? "" : String.valueOf(value).trim(); }
}
