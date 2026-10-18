package com.memoraai;

import com.memoraai.config.Neo4jSchemaInitializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.summary.ResultSummary;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class Neo4jSchemaInitializerTest {

    @Mock
    private Driver driver;

    @Mock
    private Session session;

    @Mock
    private Result result;

    @Mock
    private ResultSummary resultSummary;

    @BeforeEach
    void setUp() {
        lenient().when(driver.session()).thenReturn(session);
        lenient().when(session.run(anyString())).thenReturn(result);
        lenient().when(result.consume()).thenReturn(resultSummary);
    }

    @Test
    @DisplayName("1. Successfully executes all 5 uniqueness constraints and closes session")
    void testInitializeSchemaSuccess() {
        Neo4jSchemaInitializer initializer = new Neo4jSchemaInitializer(driver, true);

        initializer.initializeSchema();

        ArgumentCaptor<String> cypherCaptor = ArgumentCaptor.forClass(String.class);
        verify(session, times(5)).run(cypherCaptor.capture());
        verify(result, times(5)).consume();
        verify(session, times(1)).close();

        List<String> executedStatements = cypherCaptor.getAllValues();
        assertThat(executedStatements).containsExactlyInAnyOrder(
                "CREATE CONSTRAINT user_id_unique IF NOT EXISTS FOR (u:User) REQUIRE u.id IS UNIQUE",
                "CREATE CONSTRAINT memory_id_unique IF NOT EXISTS FOR (m:Memory) REQUIRE m.id IS UNIQUE",
                "CREATE CONSTRAINT person_user_name_unique IF NOT EXISTS FOR (p:Person) REQUIRE (p.userId, p.name) IS UNIQUE",
                "CREATE CONSTRAINT place_user_name_unique IF NOT EXISTS FOR (p:Place) REQUIRE (p.userId, p.name) IS UNIQUE",
                "CREATE CONSTRAINT tag_user_name_unique IF NOT EXISTS FOR (t:Tag) REQUIRE (t.userId, t.name) IS UNIQUE"
        );
    }

    @Test
    @DisplayName("2. Session is closed and IllegalStateException is thrown when constraint execution fails")
    void testInitializeSchemaFailureThrowsAndClosesSession() {
        when(session.run(anyString())).thenThrow(new RuntimeException("Database error"));

        Neo4jSchemaInitializer initializer = new Neo4jSchemaInitializer(driver, true);

        assertThatThrownBy(initializer::initializeSchema)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to initialize required Neo4j schema constraints")
                .hasRootCauseInstanceOf(RuntimeException.class);

        verify(session, times(1)).close();
    }

    @Test
    @DisplayName("3. When schemaInitEnabled is false, run() does not open a Neo4j session")
    void testRunDisabledByConfiguration() {
        Neo4jSchemaInitializer initializer = new Neo4jSchemaInitializer(driver, false);

        initializer.run(null);

        verify(driver, never()).session();
    }

    @Test
    @DisplayName("4. When schemaInitEnabled is true, run() triggers schema initialization")
    void testRunEnabledByConfiguration() {
        Neo4jSchemaInitializer initializer = new Neo4jSchemaInitializer(driver, true);

        initializer.run(null);

        verify(driver, times(1)).session();
        verify(session, times(5)).run(anyString());
        verify(session, times(1)).close();
    }
}
