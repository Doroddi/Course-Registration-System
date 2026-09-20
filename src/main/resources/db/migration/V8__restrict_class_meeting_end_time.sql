-- LocalTime으로 표현할 수 없는 PostgreSQL의 24:00:00 저장을 막는다.
ALTER TABLE class_meeting
    ADD CONSTRAINT ck_class_meeting_end_before_midnight
    CHECK (ends_at < TIME '24:00:00');
