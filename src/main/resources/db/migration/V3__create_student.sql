CREATE TABLE student (
    student_number INTEGER PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    grade SMALLINT NOT NULL,
    department_id BIGINT NOT NULL,
    password_hash VARCHAR(255) NOT NULL,

    CONSTRAINT ck_student_number_nine_digits
        CHECK (student_number BETWEEN 100000000 AND 999999999),

    CONSTRAINT ck_student_name_not_blank
        CHECK (name ~ '[^[:space:]]'),

    CONSTRAINT ck_student_grade_range
        CHECK (grade BETWEEN 1 AND 4),

    CONSTRAINT fk_student_department
    FOREIGN KEY (department_id)
    REFERENCES department (department_id)
    ON DELETE RESTRICT,

    CONSTRAINT ck_student_password_hash_not_blank
        CHECK (password_hash ~ '[^[:space:]]')
);