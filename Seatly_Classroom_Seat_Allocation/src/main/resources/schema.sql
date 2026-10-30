CREATE TABLE IF NOT EXISTS users (
    username VARCHAR(40) PRIMARY KEY,
    full_name VARCHAR(120) NOT NULL,
    email VARCHAR(160) NOT NULL,
    department VARCHAR(100),
    role VARCHAR(20) NOT NULL,
    password_hash VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS classrooms (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(40) NOT NULL,
    building VARCHAR(160) NOT NULL,
    row_count INT NOT NULL,
    column_count INT NOT NULL,
    capacity INT NOT NULL
);

CREATE TABLE IF NOT EXISTS sessions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(140) NOT NULL,
    course VARCHAR(100) NOT NULL,
    classroom_id BIGINT NOT NULL,
    starts_at TIMESTAMP NOT NULL,
    published BOOLEAN NOT NULL DEFAULT FALSE,
    created_by VARCHAR(40) NOT NULL,
    notes VARCHAR(700),
    CONSTRAINT fk_session_classroom FOREIGN KEY (classroom_id) REFERENCES classrooms(id),
    CONSTRAINT fk_session_creator FOREIGN KEY (created_by) REFERENCES users(username)
);

CREATE TABLE IF NOT EXISTS seat_assignments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id BIGINT NOT NULL,
    username VARCHAR(40) NOT NULL,
    seat_code VARCHAR(20) NOT NULL,
    CONSTRAINT uq_session_student UNIQUE (session_id, username),
    CONSTRAINT uq_session_seat UNIQUE (session_id, seat_code),
    CONSTRAINT fk_assignment_session FOREIGN KEY (session_id) REFERENCES sessions(id),
    CONSTRAINT fk_assignment_user FOREIGN KEY (username) REFERENCES users(username)
);


CREATE TABLE IF NOT EXISTS session_students (
    session_id BIGINT NOT NULL,
    username VARCHAR(40) NOT NULL,
    PRIMARY KEY (session_id, username),
    CONSTRAINT fk_roster_session FOREIGN KEY (session_id) REFERENCES sessions(id),
    CONSTRAINT fk_roster_user FOREIGN KEY (username) REFERENCES users(username)
);
