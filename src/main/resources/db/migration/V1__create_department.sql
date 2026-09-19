CREATE TABLE department (
    department_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,

    CONSTRAINT ck_department_name_not_blank
        CHECK (name ~ '[^[:space:]]')
);