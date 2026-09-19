ALTER TABLE department
    ADD COLUMN department_code SMALLINT NOT NULL,

    ADD CONSTRAINT uq_department_code
        UNIQUE (department_code),

    ADD CONSTRAINT ck_department_code_range
        CHECK (department_code BETWEEN 10 AND 99);