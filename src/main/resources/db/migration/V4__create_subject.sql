CREATE TABLE subject (
    subject_code VARCHAR(30) NOT NULL PRIMARY KEY ,

    CONSTRAINT ck_subject_code_not_blank
                     CHECK ( subject_code ~ '^[0-9]{1,30}$' )
);