package com.talos.engine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@ComponentScan(basePackages = {"com.talos"})
public class TalosServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(TalosServerApplication.class, args);
    }
}
