package com.gpi.gpitracker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;


@SpringBootApplication
@EnableScheduling
public class GpiTrackerApplication {
    public static void main(String[] args) {
        SpringApplication.run(GpiTrackerApplication.class, args);
    }

}
