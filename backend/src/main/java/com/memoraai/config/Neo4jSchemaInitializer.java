package com.memoraai.config;

import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class Neo4jSchemaInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(Neo4jSchemaInitializer.class);

    public static final List<String> CONSTRAINT_CYPHER_STATEMENTS = List.of(
            "CREATE CONSTRAINT user_id_unique IF NOT EXISTS FOR (u:User) REQUIRE u.id IS UNIQUE",
            "CREATE CONSTRAINT memory_id_unique IF NOT EXISTS FOR (m:Memory) REQUIRE m.id IS UNIQUE",
            "CREATE CONSTRAINT person_user_name_unique IF NOT EXISTS FOR (p:Person) REQUIRE (p.userId, p.name) IS UNIQUE",
            "CREATE CONSTRAINT place_user_name_unique IF NOT EXISTS FOR (p:Place) REQUIRE (p.userId, p.name) IS UNIQUE",
            "CREATE CONSTRAINT tag_user_name_unique IF NOT EXISTS FOR (t:Tag) REQUIRE (t.userId, t.name) IS UNIQUE"
    );

    private final Driver driver;
    private final boolean schemaInitEnabled;

    public Neo4jSchemaInitializer(
            Driver driver,
            @Value("${neo4j.schema-init-enabled:true}") boolean schemaInitEnabled) {
        this.driver = driver;
        this.schemaInitEnabled = schemaInitEnabled;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!schemaInitEnabled) {
            log.info("Neo4j schema initialization is disabled by configuration (schemaInitEnabled=false).");
            return;
        }

        initializeSchema();
    }

    public void initializeSchema() {
        log.info("Beginning Neo4j schema uniqueness constraint initialization...");
        try (Session session = driver.session()) {
            for (String statement : CONSTRAINT_CYPHER_STATEMENTS) {
                session.run(statement).consume();
            }
            log.info("Successfully initialized {} Neo4j schema uniqueness constraints.", CONSTRAINT_CYPHER_STATEMENTS.size());
        } catch (Exception e) {
            log.error("Failed to initialize Neo4j schema constraints: {}", e.getMessage());
            throw new IllegalStateException("Failed to initialize required Neo4j schema constraints. Ensure Neo4j is running and reachable.", e);
        }
    }
}
