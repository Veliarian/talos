package com.talos.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@ComponentScan(basePackages = "com.talos")
@EnableJpaRepositories(basePackages = "com.talos.gis.repository")
@EntityScan(basePackages = {"com.talos.gis.entity", "com.talos.model.entity"})
public class TalosServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(TalosServerApplication.class, args);
    }
}