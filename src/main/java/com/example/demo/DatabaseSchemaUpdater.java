package com.example.demo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Applies the small database changes that Hibernate's ddl-auto=update does NOT do
 * (widening existing columns and adding the unique-email index), so nobody has to
 * run a SQL script by hand. Safe to run on every start: each step is skipped when
 * it is already in place, and a failure in one step is logged, never fatal.
 */
@Component
@Order(1)
public class DatabaseSchemaUpdater implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DatabaseSchemaUpdater.class);

    private final JdbcTemplate jdbc;

    public DatabaseSchemaUpdater(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        widen("appointments", "visit_reason", 300);
        widen("appointments", "diagnosis", 200);
        widen("appointments", "consultation_notes", 2000);
        widen("check_ins", "presenting_complaint", 500);
        widen("notifications", "message", 600);
        uniqueEmailIndex();
    }

    // Widens a varchar column only if it is currently shorter than the length we need.
    private void widen(String table, String column, int length) {
        try {
            Integer current = jdbc.query(
                    "SELECT character_maximum_length FROM information_schema.columns "
                            + "WHERE table_schema = current_schema() AND table_name = ? AND column_name = ?",
                    rs -> rs.next() && rs.getObject(1) != null ? Integer.valueOf(((Number) rs.getObject(1)).intValue()) : null, table, column);
            if (current != null && current < length) {
                jdbc.execute("ALTER TABLE " + table + " ALTER COLUMN " + column + " TYPE varchar(" + length + ")");
                log.info("Database updated: {}.{} widened from {} to {}", table, column, current, length);
            }
        } catch (Exception e) {
            log.warn("Could not widen {}.{}: {}", table, column, e.getMessage());
        }
    }

    // One email address can belong to only one account (enforced here as well as in the server code).
    private void uniqueEmailIndex() {
        try {
            jdbc.execute("CREATE UNIQUE INDEX IF NOT EXISTS users_email_unique "
                    + "ON users (lower(email)) WHERE email IS NOT NULL");
        } catch (Exception e) {
            log.warn("Could not create the unique-email index (two accounts probably share an email address - "
                    + "give each its own email, then restart): {}", e.getMessage());
        }
    }
}
