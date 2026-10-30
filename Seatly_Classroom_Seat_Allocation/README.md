# Seatly — Classroom Seat Allocation Website

A Seatly-inspired classroom and examination seating application based on the **Classroom Seat Allocation System** idea in `Mini Project (1).docx`. The browser interface adapts the supplied `Seatly_Preview.html`; the full application uses Java, JDBC and a persistent database.

## Choose the version to run

### A. Offline HTML preview

Open `Seatly_Standalone_Preview.html` in a browser. It has no Java or Maven requirement and is useful for demonstrating the UI and workflow. It stores demo users, passwords and changes in **that browser's local storage only**. It is not a shared multi-student account system and must not be used for real student credentials.

### B. Full Java website (shared server/database)

Requirements: **JDK 17+** and **Maven 3.8+**.

1. Extract `Seatly_Classroom_Seat_Allocation_Java_Website.zip`.
2. Open a terminal in the extracted `Seatly_Classroom_Seat_Allocation` folder.
3. Run `RUN_SEATLY.bat` on Windows, or run:

   ```bash
   mvn spring-boot:run
   ```

4. Open `http://localhost:8080`.
5. Students sign in with their registration number or email. They can create their own account using **Create student account**. Faculty use a faculty account.

The Java app saves accounts, session rosters, seat assignments and classrooms to the H2 file database in `data/`. Keep the server running while students use the site; all users must access the same deployed server to share data.

## Demo accounts

These accounts are seeded when the Java database first starts. The project also fills in demo passwords for bundled demo users in an older local database if their password field is blank.

| Role | Login ID | Demo password |
|---|---|---|
| Student | `99250040078` | `Student@123` |
| Student | Any other seeded registration number | `Student@123` |
| Faculty | `faculty` | `Faculty@123` |

There are **15 seeded students** for demonstrations, not a four-student limit. More students can register themselves or be added from the Faculty **Student directory**. Each registration number and email must be unique. Demo credentials are for local evaluation only; change them and secure access before deployment.

## Student features

- Individual login by registration number or college email plus password.
- Self-registration as a student with name, registration number, email, department and password.
- Personal overview and timetable.
- Only published sessions on the student's selected roster appear in their account.
- The student can see their own seat highlighted on the room map; other students' names are hidden.

## Faculty features

- Dashboard showing total student accounts, sessions, classrooms and assigned seats.
- Searchable **Student directory** to inspect registrations and edit name, email or department.
- Add a student account with an initial password so the student can sign in.
- Create classrooms using row and column counts; capacity is calculated as rows × columns.
- Create a session with course, classroom, start date/time and instructions.
- **Manage roster**: choose which students are expected in each session. The roster is separate for every session.
- **Allocate**: fill available seats in registration-number order without assigning a student or seat twice.
- **Manual allocation**: open the map, click a seat, select a student, and confirm before replacing a student already in that seat.
- **Publish**: students see the plan only after publishing. Changing the roster or a seat returns the plan to Draft so it must be reviewed and published again.
- Download the allocation as CSV and print the seating map.

A classroom cannot publish a complete roster if it has fewer seats than selected students. Choose a larger room, remove students from that session's roster, or adjust the classroom layout before publishing.

## Technical implementation

| Layer | Implementation |
|---|---|
| Frontend | Responsive HTML, CSS and JavaScript based on the provided Seatly style |
| API | Spring Boot REST controller and session-based login |
| Java/OOP | `SeatlyController`, `SeatlyService`, `SeatAllocationEngine`, `PasswordUtil` |
| Collections | `List`, `Set` and `Map` for student rosters, seat grids and API responses |
| Algorithm | Deterministic row-major seat scan, skipping assigned students and occupied seats |
| Persistence | Spring `JdbcTemplate` / JDBC with H2 by default and MySQL configuration |
| Passwords | Salted PBKDF2-HMAC-SHA256 hashes in the Java app; raw passwords are not stored there |

The uploaded course document proposes JavaFX as its frontend option. Since this task specifically asks for a website, the interface is HTML/CSS/JavaScript while the backend follows the document's Core Java, OOP, Collections, DSA and JDBC structure. The source flowchart shows an AI node but does not define an AI feature; this project does not claim to implement an AI model.

## MySQL configuration (optional)

Create an empty database called `seatly`, then configure environment variables before running:

```text
DB_URL=jdbc:mysql://localhost:3306/seatly
DB_USERNAME=your_mysql_username
DB_PASSWORD=your_mysql_password
DB_DRIVER=com.mysql.cj.jdbc.Driver
```

Use a dedicated database user and keep credentials out of Git. The application initializes the tables in `schema.sql`; `SeatlyService.ensureSchemaCompatibility()` adds the password column when upgrading an earlier H2 project database.

## API summary

See [`API_GUIDE.md`](API_GUIDE.md) for endpoint details, request shapes and the seat-allocation workflow. `FLOWCHART_MAPPING.md` maps the document's flowchart to the code files.

## Important production caveat

This is a college mini-project foundation, not a production-ready university identity system. Before real deployment, add institution-controlled faculty provisioning, email/identity verification, password reset, login rate limiting, HTTPS, CSRF protection, secure cookie settings, audit logs, backups and a reviewed privacy policy. The demo faculty account is intentionally provided for evaluation only.
