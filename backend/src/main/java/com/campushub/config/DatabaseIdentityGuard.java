package com.campushub.config;

import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class DatabaseIdentityGuard implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(DatabaseIdentityGuard.class);
    private final DataSource dataSource;
    private final String purpose;

    public DatabaseIdentityGuard(DataSource dataSource, @Value("${campushub.database-purpose:application}") String purpose) {
        this.dataSource = dataSource;
        this.purpose = purpose;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        String database = switch (purpose) {
            case "test" -> "campushub_test";
            case "application" -> "campushub";
            default -> throw new IllegalStateException("Unsupported database purpose");
        };
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement();
             var row = statement.executeQuery("SELECT VERSION(),@@port,@@datadir,DATABASE(),CURRENT_USER()")) {
            if (!row.next() || !"8.4.11".equals(row.getString(1)) || row.getInt(2) != 3307
                    || !"E:/CampusHubRuntime/mysql84/data/".equalsIgnoreCase(row.getString(3).replace('\\','/'))
                    || !database.equals(row.getString(4)) || !(database + "_app@127.0.0.1").equals(row.getString(5))) {
                throw new IllegalStateException("Database identity guard rejected the connection");
            }
        }
        log.info("Database identity verified: purpose={} database={} port=3307", purpose, database);
    }
}
