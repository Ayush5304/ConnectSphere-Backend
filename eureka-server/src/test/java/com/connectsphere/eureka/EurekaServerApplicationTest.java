package com.connectsphere.eureka;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

import static org.junit.jupiter.api.Assertions.*;

class EurekaServerApplicationTest {

    @Test
    void applicationClassHasExpectedBootAnnotationsAndCanBeInstantiated() {
        assertNotNull(new EurekaServerApplication());
        assertTrue(EurekaServerApplication.class.isAnnotationPresent(SpringBootApplication.class));
        assertTrue(EurekaServerApplication.class.isAnnotationPresent(EnableEurekaServer.class));
    }

    @Test
    void mainStartsWithNonWebMode() {
        assertDoesNotThrow(() -> EurekaServerApplication.main(new String[] {
            "--server.port=0",
            "--eureka.client.register-with-eureka=false",
            "--eureka.client.fetch-registry=false",
            "--eureka.server.enable-self-preservation=false"
        }));
    }
}
