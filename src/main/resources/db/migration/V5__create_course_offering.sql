CREATE TABLE course_offering (
    offering_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    subject_code VARCHAR(30) NOT NULL,
    academic_year INTEGER NOT NULL,
    term SMALLINT NOT NULL,
    offering_code VARCHAR(30) NOT NULL,
    name VARCHAR(200) NOT NULL,
    credits SMALLINT NOT NULL,
    capacity INTEGER NOT NULL,
    department_id BIGINT NOT NULL,

    CONSTRAINT fk_course_offering_department
        FOREIGN KEY (department_id)
            REFERENCES department (department_id)
            ON DELETE RESTRICT,

    CONSTRAINT fk_course_offering_subject_code
        FOREIGN KEY (subject_code)
            REFERENCES subject (subject_code)
            ON DELETE RESTRICT,

    CONSTRAINT ck_course_offering_academic_year
        CHECK (academic_year BETWEEN 1000 AND 9999),

    CONSTRAINT ck_course_offering_term
        CHECK (term IN (1, 2)),

    CONSTRAINT ck_course_offering_code_digits
        CHECK (offering_code ~ '^[0-9]{1,30}$'),

    CONSTRAINT ck_course_offering_name_not_blank
        CHECK (name ~ '[^[:space:]]'),

    CONSTRAINT ck_course_offering_credits
        CHECK (credits BETWEEN 1 AND 6),

    CONSTRAINT ck_course_offering_capacity
        CHECK (capacity > 0),

    CONSTRAINT uq_course_offering_term_code
        UNIQUE (academic_year, term, offering_code)
);