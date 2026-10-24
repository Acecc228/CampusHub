-- No global privileges, GRANT OPTION, DROP or cross-schema grants.
-- Flyway clean stays disabled. A future destructive migration needs review.
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES
ON campushub.* TO 'campushub_migrator'@'127.0.0.1';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES
ON `campushub\_test`.* TO 'campushub_test_migrator'@'127.0.0.1';
