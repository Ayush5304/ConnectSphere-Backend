package com.connectsphere.admin;

import de.codecentric.boot.admin.server.config.AdminServerProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;

/**
 * AdminSecurityConfig — Secures the Admin Server UI with login page.
 * Username: admin  Password: admin123
 */
@Configuration
public class AdminSecurityConfig {

    private final AdminServerProperties adminServer;

    public AdminSecurityConfig(AdminServerProperties adminServer) {
        this.adminServer = adminServer;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        SavedRequestAwareAuthenticationSuccessHandler successHandler =
            new SavedRequestAwareAuthenticationSuccessHandler();
        successHandler.setTargetUrlParameter("redirectTo");
        successHandler.setDefaultTargetUrl(adminServer.path("/"));

        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(adminServer.path("/assets/**")).permitAll()
                .requestMatchers(adminServer.path("/login")).permitAll()
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage(adminServer.path("/login"))
                .successHandler(successHandler)
            )
            .logout(logout -> logout
                .logoutUrl(adminServer.path("/logout"))
            )
            .csrf(csrf -> csrf.disable());

        return http.build();
    }
}
