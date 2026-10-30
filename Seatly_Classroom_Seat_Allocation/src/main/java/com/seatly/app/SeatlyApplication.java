package com.seatly.app;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class SeatlyApplication {
    public static void main(String[] args) {
        SpringApplication.run(SeatlyApplication.class, args);
    }

    @Bean
    CommandLineRunner seedDatabase(SeatlyService service) {
        return args -> { service.ensureSchemaCompatibility(); service.seedDemoData(); };
    }
}
