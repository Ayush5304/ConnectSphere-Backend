package com.connectsphere.admin;

import de.codecentric.boot.admin.server.config.EnableAdminServer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * AdminServerApplication — Spring Boot Admin Server
 *
 * Provides a web UI dashboard to monitor all microservices.
 * Access at: http://localhost:9090
 *
 * Features visible in dashboard:
 *   - Health status of each service (UP/DOWN)
 *   - Memory and CPU usage
 *   - Log levels (can change at runtime)
 *   - Live log file viewer
 *   - Environment properties
 *   - HTTP request metrics
 */
@SpringBootApplication
@EnableAdminServer
public class AdminServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(AdminServerApplication.class, args);
    }
}
