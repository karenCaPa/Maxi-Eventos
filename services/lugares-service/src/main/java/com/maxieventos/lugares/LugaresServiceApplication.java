package com.maxieventos.lugares;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class LugaresServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(LugaresServiceApplication.class, args);
    }
}
