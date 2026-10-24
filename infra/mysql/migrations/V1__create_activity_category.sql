CREATE TABLE activity_category (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(32) NOT NULL,
    name VARCHAR(64) NOT NULL,
    sort_order INT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_activity_category_code (code),
    CONSTRAINT ck_activity_category_sort CHECK (sort_order >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO activity_category (code, name, sort_order) VALUES
    ('ACADEMIC', '学术科技', 10),
    ('CULTURE_SPORTS', '文体活动', 20),
    ('VOLUNTEER', '志愿公益', 30),
    ('CAREER', '就业实践', 40);
