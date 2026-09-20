CREATE TABLE class_meeting (
    meeting_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    offering_id BIGINT NOT NULL,
    day_of_week SMALLINT NOT NULL,
    starts_at TIME WITHOUT TIME ZONE NOT NULL,
    ends_at TIME WITHOUT TIME ZONE NOT NULL,

    CONSTRAINT fk_class_meeting_offering
        FOREIGN KEY (offering_id)
            REFERENCES course_offering (offering_id)
            ON DELETE RESTRICT,

    CONSTRAINT ck_class_meeting_day_of_week
        CHECK (day_of_week BETWEEN 1 AND 7),

    CHECK (starts_at < ends_at),
    CHECK (EXTRACT(SECOND FROM starts_at) = 0),
    CHECK (EXTRACT(SECOND FROM ends_at) = 0)
);

CREATE INDEX idx_class_meeting_offering_id
    ON class_meeting (offering_id);