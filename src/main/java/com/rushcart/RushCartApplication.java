package com.rushcart;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class RushCartApplication {

    public static void main(String[] args) {
        SpringApplication.run(RushCartApplication.class, args);
    }
}
