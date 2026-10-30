# Seatly API Guide

All APIs are served from the same Spring Boot origin at `http://localhost:8080`. Login/register endpoints create an HTTP session cookie. The full app expects JSON request bodies where noted.

## Authentication and accounts

| Method | Endpoint | Purpose |
|---|---|---|
| `POST` | `/api/auth/login` | Login with `{ "username": "registration number or email", "password": "..." }` |
| `POST` | `/api/auth/register` | Create a student account with `{ "username", "fullName", "email", "department", "password" }` |
| `POST` | `/api/demo/login?role=STUDENT` | Optional demo-only login; still requires a `password` query parameter |
| `POST` | `/api/demo/logout` | End the current session |
| `GET` | `/api/me` | Return the signed-in user's public profile |

Public registration always creates the `STUDENT` role. The website does not allow public self-registration as faculty.

## Common student endpoints

| Method | Endpoint | Purpose |
|---|---|---|
| `GET` | `/api/sessions` | Return sessions published to the signed-in student, or sessions owned by signed-in faculty |
| `GET` | `/api/classrooms` | List configured classroom layouts |
| `GET` | `/api/sessions/{sessionId}/seats` | Return the seating map; a student sees their own occupant details only |

## Faculty endpoints

| Method | Endpoint | Purpose |
|---|---|---|
| `GET` | `/api/faculty/summary` | Dashboard metrics |
| `GET` | `/api/faculty/students?q=keyword` | Search the student directory (the query is optional) |
| `POST` | `/api/faculty/students` | Create a student account using the same fields as registration |
| `PUT` | `/api/faculty/students/{username}` | Update name, email and department |
| `POST` | `/api/faculty/classrooms` | Create a classroom with `{ "name", "building", "rows", "columns" }` |
| `POST` | `/api/faculty/sessions` | Create a session with `{ "title", "course", "classroomId", "startsAt", "notes" }` |
| `GET` | `/api/faculty/sessions/{sessionId}/roster` | Get all students and their roster status / current seat |
| `PUT` | `/api/faculty/sessions/{sessionId}/roster` | Replace the session roster using `{ "usernames": ["REG001", "REG002"] }` |
| `POST` | `/api/faculty/sessions/{sessionId}/allocate` | Allocate available seats automatically |
| `POST` | `/api/faculty/sessions/{sessionId}/assign` | Assign or replace a seat using `{ "username", "seatCode", "replaceExisting" }` |
| `DELETE` | `/api/faculty/sessions/{sessionId}/assignments/{username}` | Remove a student's seat assignment |
| `POST` | `/api/faculty/sessions/{sessionId}/publish` | Publish a complete seating plan to its roster |
| `GET` | `/api/faculty/sessions/{sessionId}/export` | Download the seating plan as CSV |

## Seat-allocation workflow

1. A student creates an account or faculty adds the student's account.
2. Faculty creates a classroom and a session.
3. Faculty opens **Roster** and selects the students expected in that session.
4. Faculty runs automatic allocation or opens the room map and assigns a student to a clicked seat.
5. Faculty reviews the map. If changing the roster or manually moving a student, the plan returns to Draft.
6. Faculty publishes only after every student selected in that roster has a seat. Students then see the published session under their own account.

## Allocation algorithm

`SeatAllocationEngine.allocate(...)` generates the seat sequence `R1-C1`, `R1-C2`, and so on, scanning each row left to right. It skips users already assigned and seats already occupied. The database also enforces unique `(session_id, username)` and `(session_id, seat_code)` constraints as a second protection against duplicate allocations.
