ALTER TABLE class_bookings
    ADD COLUMN created_by BIGINT,
    ADD COLUMN created_date TIMESTAMP,
    ADD COLUMN last_modified_by BIGINT,
    ADD COLUMN last_modified_date TIMESTAMP;

ALTER TABLE pt_sessions
    ADD COLUMN created_by BIGINT,
    ADD COLUMN created_date TIMESTAMP,
    ADD COLUMN last_modified_by BIGINT,
    ADD COLUMN last_modified_date TIMESTAMP;
