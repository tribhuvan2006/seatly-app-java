# Flowchart mapping

```mermaid
flowchart TD
    A[Student / Faculty Sign-in] --> B[Seatly Website UI<br/>HTML / CSS / JavaScript]
    B --> C[Java REST Controller]
    C --> D[Core Java Service + OOP]
    D --> E[Student Accounts + Session Roster]
    D --> F[Collections<br/>List / Set / Map]
    D --> G[Seat Allocation Algorithm]
    E --> H[Application Rules]
    F --> H
    G --> H
    H --> I[JDBC / JdbcTemplate]
    I --> J[(H2 default / MySQL configurable)]
    J --> K[Publish + Student Seat View]
```

The source DOCX describes a Java mini-project architecture using JavaFX, Core Java, OOP, Collections, data structures and algorithms, JDBC, and a SQL database. Because the requested output is a website resembling the supplied Seatly HTML, this implementation uses a browser frontend and a Java REST backend. `SeatlyService` handles accounts, rosters, rooms and sessions; `SeatAllocationEngine` creates row-major seat assignments; JDBC stores persistent state. Passwords are hashed in the Java version.

The source diagram includes an AI node but does not specify a model or AI task. This project does not claim to implement an AI model; seating allocation is deterministic and rule-based.
