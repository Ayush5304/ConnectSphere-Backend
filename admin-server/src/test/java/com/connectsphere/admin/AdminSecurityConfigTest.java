package com.connectsphere.admin;

import de.codecentric.boot.admin.server.config.AdminServerProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(
    classes = AdminServerApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "spring.security.user.name=admin",
        "spring.security.user.password=admin123"
    }
)
class AdminSecurityConfigTest {

    @LocalServerPort
    int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private SecurityFilterChain securityFilterChain;

    @Test
    void constructorStoresAdminServerProperties() {
        AdminServerProperties properties = new AdminServerProperties();
        properties.setContextPath("/admin");

        AdminSecurityConfig config = new AdminSecurityConfig(properties);

        assertSame(properties, ReflectionTestUtils.getField(config, "adminServer"));
        assertEquals("/admin/login", properties.path("/login"));
        assertEquals("/admin/logout", properties.path("/logout"));
    }

    @Test
    void securityFilterChainIsCreatedBySpringContext() {
        assertNotNull(securityFilterChain);
        assertFalse(securityFilterChain.getFilters().isEmpty());
    }

    @Test
    void loginPageIsPublicAndRootRequiresAuthentication() {
        ResponseEntity<String> login = restTemplate.getForEntity(url("/login"), String.class);
        ResponseEntity<String> root = restTemplate.getForEntity(url("/"), String.class);
        ResponseEntity<String> asset = restTemplate.getForEntity(url("/assets/app.css"), String.class);

        assertNotEquals(HttpStatus.UNAUTHORIZED, login.getStatusCode());
        assertTrue(root.getStatusCode().is3xxRedirection() || root.getStatusCode() == HttpStatus.UNAUTHORIZED);
        assertNotEquals(HttpStatus.UNAUTHORIZED, asset.getStatusCode());
    }

    @Test
    void authenticatedUserCanReachAdminRoot() {
        ResponseEntity<String> response = restTemplate
            .withBasicAuth("admin", "admin123")
            .getForEntity(url("/"), String.class);

        assertNotEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }
}
