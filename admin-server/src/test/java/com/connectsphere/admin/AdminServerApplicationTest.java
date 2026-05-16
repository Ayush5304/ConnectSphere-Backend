package com.connectsphere.admin;

import de.codecentric.boot.admin.server.config.EnableAdminServer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import static org.junit.jupiter.api.Assertions.*;

class AdminServerApplicationTest {

    @Test
    void applicationClassHasExpectedBootAnnotationsAndCanBeInstantiated() {
        assertNotNull(new AdminServerApplication());
        assertTrue(AdminServerApplication.class.isAnnotationPresent(SpringBootApplication.class));
        assertTrue(AdminServerApplication.class.isAnnotationPresent(EnableAdminServer.class));
    }
}
