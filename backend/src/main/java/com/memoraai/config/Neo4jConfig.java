package com.memoraai.config;

import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class Neo4jConfig {

    private final String uri;
    private final String username;
    private final String password;

    public Neo4jConfig(
            @Value("${neo4j.uri:bolt://localhost:7687}") String uri,
            @Value("${neo4j.username:neo4j}") String username,
            @Value("${neo4j.password:memora_neo4j_dev}") String password) {
        this.uri = uri;
        this.username = username;
        this.password = password;
    }

    @Bean(destroyMethod = "close")
    public Driver neo4jDriver() {
        return GraphDatabase.driver(uri, AuthTokens.basic(username, password));
    }
}
