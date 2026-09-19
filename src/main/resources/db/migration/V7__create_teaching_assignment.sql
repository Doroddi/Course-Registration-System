CREATE TABLE teaching_assignment(
    professor_id BIGINT NOT NULL,
    offering_id BIGINT NOT NULL,

    CONSTRAINT fk_teaching_assignment_offering
        FOREIGN KEY (offering_id)
            REFERENCES course_offering (offering_id)
            ON DELETE RESTRICT,

    CONSTRAINT fk_teaching_assignment_professor
        FOREIGN KEY (professor_id)
            REFERENCES professor (professor_id)
            ON DELETE RESTRICT,

    CONSTRAINT pk_teaching_assignment
        PRIMARY KEY (professor_id, offering_id)
);