package com.connectsphere.auth.config;

import com.connectsphere.auth.entity.User;
import com.connectsphere.auth.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class AdminAccountInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminAccountInitializer.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${connectsphere.admin.email:}")
    private String adminEmail;

    @Value("${connectsphere.admin.password:}")
    private String adminPassword;

    @Value("${connectsphere.admin.username:admin}")
    private String adminUsername;

    @Value("${connectsphere.admin.full-name:ConnectSphere Admin}")
    private String adminFullName;

    public AdminAccountInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        if (adminEmail == null || adminEmail.isBlank() || adminPassword == null || adminPassword.isBlank()) {
            log.info("Admin bootstrap skipped. Configure connectsphere.admin.email and connectsphere.admin.password to enable it.");
            return;
        }

        User admin = userRepository.findByEmail(adminEmail.trim().toLowerCase())
            .orElseGet(User::new);

        boolean created = admin.getUserId() == null;
        if (created) {
            admin.setEmail(adminEmail.trim().toLowerCase());
            admin.setUsername(adminUsername == null || adminUsername.isBlank() ? "admin" : adminUsername.trim());
        }

        admin.setPasswordHash(passwordEncoder.encode(adminPassword));
        admin.setRole(User.Role.ADMIN);
        admin.setActive(true);
        admin.setFullName(adminFullName);
        admin.setBio("Monitoring ConnectSphere communities, reports, content, trends, and platform health.");
        userRepository.save(admin);

        log.info("{} admin account for {}", created ? "Created" : "Updated", admin.getEmail());
    }

}
