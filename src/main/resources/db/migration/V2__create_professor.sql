CREATE TABLE professor (
    professor_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    department_id BIGINT NOT NULL,

    CONSTRAINT ck_professor_name_not_blank
        CHECK (name ~ '[^[:space:]]'),

    CONSTRAINT fk_professor_department
        FOREIGN KEY (department_id)
        REFERENCES department (department_id)
        ON DELETE RESTRICT
);