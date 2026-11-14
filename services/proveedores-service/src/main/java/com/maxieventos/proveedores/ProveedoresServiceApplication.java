package com.maxieventos.proveedores;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class ProveedoresServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(ProveedoresServiceApplication.class, args);
    }
}
