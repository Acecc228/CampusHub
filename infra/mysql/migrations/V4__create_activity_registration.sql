ALTER TABLE activity
    ADD COLUMN registered_count INT NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_activity_registered_count CHECK (registered_count >= 0 AND registered_count <= capacity);

CREATE TABLE activity_registration (
    id BIGINT NOT NULL AUTO_INCREMENT,
    activity_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    version BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    updated_at DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    PRIMARY KEY (id),
    UNIQUE KEY uk_registration_activity_user (activity_id, user_id),
    KEY idx_registration_user_id (user_id, id),
    CONSTRAINT fk_registration_activity FOREIGN KEY (activity_id) REFERENCES activity(id),
    CONSTRAINT fk_registration_user FOREIGN KEY (user_id) REFERENCES app_user(id),
    CONSTRAINT ck_registration_status CHECK (status IN ('CONFIRMED', 'CANCELLED')),
    CONSTRAINT ck_registration_version CHECK (version > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
