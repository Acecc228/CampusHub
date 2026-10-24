-- Run AFTER approved Flyway migrations create activity_category in both schemas.
-- No schema-wide app grants: flyway_schema_history must remain protected.
GRANT SELECT ON campushub.activity_category TO 'campushub_app'@'127.0.0.1';
GRANT SELECT, INSERT, UPDATE, DELETE ON campushub_test.activity_category
TO 'campushub_test_app'@'127.0.0.1';
