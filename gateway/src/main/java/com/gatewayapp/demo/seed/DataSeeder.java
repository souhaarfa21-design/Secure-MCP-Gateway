package com.gatewayapp.demo.seed;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import net.datafaker.Faker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fills the database with fake customers and tickets on startup.
 * Flyway has already created the tables by the time this runs.
 * It only seeds an empty database, so restarting the gateway doesn't add duplicates.
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private static final String[] PLANS = {"FREE", "BASIC", "PRO", "ENTERPRISE"};
    private static final String[] MANAGERS = {"alice", "bob", "carol"};
    private static final String[] STATUSES = {"OPEN", "PENDING", "CLOSED"};

    private final JdbcTemplate jdbc;
    private final boolean enabled;
    private final int customerCount;

    public DataSeeder(JdbcTemplate jdbc,
                      @Value("${app.seed.enabled:true}") boolean enabled,
                      @Value("${app.seed.customers:200}") int customerCount) {
        this.jdbc = jdbc;
        this.enabled = enabled;
        this.customerCount = customerCount;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (!enabled) {
            return;
        }
        Integer existing = jdbc.queryForObject("SELECT count(*) FROM customers", Integer.class);
        if (existing != null && existing > 0) {
            log.info("Seed skipped: {} customers already in the database", existing);
            return;
        }

        // Fixed seed: both laptops get exactly the same fake data.
        Random random = new Random(42);
        Faker faker = new Faker(Locale.ENGLISH, random);

        List<Object[]> customers = new ArrayList<>(customerCount);
        for (int i = 0; i < customerCount; i++) {
            String name = faker.name().fullName();
            // index in the address keeps every email unique
            String email = faker.internet().username() + "." + i + "@" + faker.internet().domainName();
            customers.add(new Object[]{
                    name,
                    email.toLowerCase(Locale.ROOT),
                    faker.phoneNumber().cellPhone(),
                    faker.address().fullAddress(),
                    PLANS[random.nextInt(PLANS.length)],
                    MANAGERS[random.nextInt(MANAGERS.length)]
            });
        }
        jdbc.batchUpdate("""
                INSERT INTO customers (full_name, email, phone, address, plan, account_manager)
                VALUES (?, ?, ?, ?, ?, ?)
                """, customers);

        List<Long> ids = jdbc.queryForList("SELECT id FROM customers ORDER BY id", Long.class);
        List<Object[]> tickets = new ArrayList<>();
        for (Long customerId : ids) {
            int n = random.nextInt(3); // 0–2 tickets per customer
            for (int t = 0; t < n; t++) {
                tickets.add(new Object[]{
                        customerId,
                        faker.lorem().sentence(6),
                        faker.lorem().paragraph(3),
                        STATUSES[random.nextInt(STATUSES.length)]
                });
            }
        }
        jdbc.batchUpdate("""
                INSERT INTO tickets (customer_id, subject, body, status)
                VALUES (?, ?, ?, ?)
                """, tickets);

        log.info("Seeded {} customers and {} tickets", customers.size(), tickets.size());
    }
}
