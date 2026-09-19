CREATE TABLE enrollment (
    enrollment_id BIGINT GENERATED ALWAYS AS IDENTITY (CACHE 1) PRIMARY KEY,
    student_number INTEGER NOT NULL,
    offering_id BIGINT NOT NULL,
    CONSTRAINT uq_enrollment_student_offering
        UNIQUE (student_number, offering_id),

    CONSTRAINT fk_enrollment_student
        FOREIGN KEY (student_number)
            REFERENCES student (student_number)
            ON DELETE RESTRICT,

    CONSTRAINT fk_enrollment_course_offering
        FOREIGN KEY (offering_id)
            REFERENCES course_offering (offering_id)
            ON DELETE RESTRICT
);

CREATE INDEX idx_enrollment_offering_id
    ON enrollment (offering_id);