package com.clickclak.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

/** Excluye la autoconfiguración de usuario en memoria: la autenticación es JWT propio, sin {@code UserDetailsService}. */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class ClickClakBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(ClickClakBackendApplication.class, args);
    }
}
