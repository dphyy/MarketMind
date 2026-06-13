package com.marketmind;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MarketMindApplication {
    public static void main(String[] args) {
        SpringApplication.run(MarketMindApplication.class, args);
    }
}
