-- V5-V7 belong to the separate M2 workstream; production M1 deliberately jumps to V8.
CREATE TABLE app_wechat_identity (
    appid VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    openid VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    user_id BIGINT NULL,
    PRIMARY KEY (appid, openid),
    UNIQUE KEY uk_app_wechat_identity_user (user_id),
    CONSTRAINT fk_app_wechat_identity_user FOREIGN KEY (user_id) REFERENCES app_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
