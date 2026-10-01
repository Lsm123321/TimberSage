package com.arch3d;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class Arch3DApplication {
    public static void main(String[] args) {
        SpringApplication.run(Arch3DApplication.class, args);
    }
}
