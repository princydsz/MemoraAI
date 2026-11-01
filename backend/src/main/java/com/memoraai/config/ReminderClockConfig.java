package com.memoraai.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class ReminderClockConfig {
    @Bean
    Clock reminderClock() {
        return Clock.systemUTC();
    }
}
