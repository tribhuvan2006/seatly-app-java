package com.seatly.app;

import jakarta.servlet.http.HttpSession;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
public class SeatlyController {
    private final SeatlyService service;

    public SeatlyController(SeatlyService service) { this.service = service; }

    /** Each student creates an account with their own registration number and password. */
    @PostMapping("/api/auth/register")
    public Map<String, Object> register(@RequestBody Map<String, Object> body, HttpSession session) {
        Map<String, Object> user = service.registerStudent(body);
        session.setAttribute("seatlyUsername", String.valueOf(user.get("username")));
        return user;
    }

    @PostMapping("/api/auth/login")
    public Map<String, Object> login(@RequestBody Map<String, Object> body, HttpSession session) {
        Map<String, Object> user = service.authenticate(text(body.get("username")), text(body.get("password")));
        session.setAttribute("seatlyUsername", String.valueOf(user.get("username")));
        return user;
    }

    /** Convenience access for the two sample demo accounts only. */
    @PostMapping("/api/demo/login")
    public Map<String, Object> demoLogin(@RequestParam(defaultValue = "STUDENT") String role,
                                         @RequestParam String password, HttpSession session) {
        String username;
        if ("FACULTY".equalsIgnoreCase(role)) username = "faculty";
        else if ("STUDENT".equalsIgnoreCase(role)) username = "99250040078";
        else throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose Student or Faculty demo role.");
        Map<String, Object> user = service.authenticate(username, password);
        session.setAttribute("seatlyUsername", username);
        return user;
    }

    @PostMapping("/api/demo/logout")
    public Map<String, String> logout(HttpSession session) {
        session.invalidate();
        return Map.of("message", "Signed out.");
    }

    @GetMapping("/api/me")
    public Map<String, Object> me(HttpSession session) { return service.findUser(currentUsername(session)); }

    @GetMapping("/api/sessions")
    public List<Map<String, Object>> sessions(HttpSession session) {
        return service.getSessions(service.findUser(currentUsername(session)));
    }

    @GetMapping("/api/classrooms")
    public List<Map<String, Object>> classrooms(HttpSession session) {
        return service.getClassrooms(service.findUser(currentUsername(session)));
    }

    @GetMapping("/api/faculty/summary")
    public Map<String, Object> facultySummary(HttpSession session) {
        Map<String, Object> user = service.findUser(currentUsername(session));
        return service.getFacultySummary(String.valueOf(user.get("username")));
    }

    @GetMapping("/api/faculty/students")
    public List<Map<String, Object>> students(@RequestParam(required = false) String q, HttpSession session) {
        return service.getStudentDirectory(service.findUser(currentUsername(session)), q);
    }

    @PostMapping("/api/faculty/students")
    public Map<String, Object> addStudent(@RequestBody Map<String, Object> body, HttpSession session) {
        return service.createStudent(body, service.findUser(currentUsername(session)));
    }

    @PutMapping("/api/faculty/students/{username}")
    public Map<String, Object> updateStudent(@PathVariable String username, @RequestBody Map<String, Object> body, HttpSession session) {
        return service.updateStudent(username, body, service.findUser(currentUsername(session)));
    }

    @GetMapping("/api/sessions/{id}/seats")
    public Map<String, Object> seatMap(@PathVariable long id, HttpSession session) {
        return service.getSeatMap(id, service.findUser(currentUsername(session)));
    }

    @GetMapping("/api/faculty/sessions/{id}/roster")
    public Map<String, Object> roster(@PathVariable long id, HttpSession session) {
        return service.getSessionRoster(id, service.findUser(currentUsername(session)));
    }

    @PutMapping("/api/faculty/sessions/{id}/roster")
    public Map<String, Object> updateRoster(@PathVariable long id, @RequestBody Map<String, Object> body, HttpSession session) {
        return service.replaceSessionRoster(id, body, service.findUser(currentUsername(session)));
    }

    @PostMapping("/api/faculty/sessions")
    public Map<String, Object> createSession(@RequestBody Map<String, Object> body, HttpSession session) {
        return service.createSession(body, service.findUser(currentUsername(session)));
    }

    @PostMapping("/api/faculty/classrooms")
    public Map<String, Object> createClassroom(@RequestBody Map<String, Object> body, HttpSession session) {
        return service.createClassroom(body, service.findUser(currentUsername(session)));
    }

    @PostMapping("/api/faculty/sessions/{id}/allocate")
    public Map<String, Object> allocate(@PathVariable long id, HttpSession session) {
        return service.allocateSeats(id, service.findUser(currentUsername(session)));
    }

    @PostMapping("/api/faculty/sessions/{id}/assign")
    public Map<String, Object> assign(@PathVariable long id, @RequestBody Map<String, Object> body, HttpSession session) {
        return service.assignSeat(id, body, service.findUser(currentUsername(session)));
    }

    @DeleteMapping("/api/faculty/sessions/{id}/assignments/{username}")
    public Map<String, Object> unassign(@PathVariable long id, @PathVariable String username, HttpSession session) {
        return service.removeAssignment(id, username, service.findUser(currentUsername(session)));
    }

    @PostMapping("/api/faculty/sessions/{id}/publish")
    public Map<String, Object> publish(@PathVariable long id, HttpSession session) {
        return service.publishSession(id, service.findUser(currentUsername(session)));
    }

    @GetMapping("/api/faculty/sessions/{id}/export")
    public ResponseEntity<byte[]> export(@PathVariable long id, HttpSession session) {
        byte[] csv = service.exportCsv(id, service.findUser(currentUsername(session)));
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("seating-plan-" + id + ".csv").build().toString())
                .body(csv);
    }

    private String currentUsername(HttpSession session) {
        Object value = session.getAttribute("seatlyUsername");
        if (value == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Please sign in first.");
        return String.valueOf(value);
    }

    private String text(Object value) { return value == null ? "" : String.valueOf(value).trim(); }
}
