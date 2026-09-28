package com.talos.server;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@ComponentScan(basePackages = "com.talos")
@EnableJpaRepositories(basePackages = "com.talos")
@EntityScan(basePackages = {"com.talos"})
public class TalosServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(TalosServerApplication.class, args);
    }

    // Automatically enables PostGIS extension in a clean database on startup
    @Bean
    public CommandLineRunner initPostgis(JdbcTemplate jdbcTemplate) {
        return args -> {
            try {
                jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS postgis;");
                jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS hstore;");
            } catch (Exception ignored) {}
        };
    }
}