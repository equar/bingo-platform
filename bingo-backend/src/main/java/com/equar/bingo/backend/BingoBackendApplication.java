package com.equar.bingo.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class BingoBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(BingoBackendApplication.class, args);
    }
}