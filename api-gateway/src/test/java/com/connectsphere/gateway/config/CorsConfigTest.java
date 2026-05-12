package com.connectsphere.gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.web.cors.reactive.CorsWebFilter;

import static org.junit.jupiter.api.Assertions.*;

class CorsConfigTest {

    @Test
    void corsFilterBeanIsCreated() {
        assertNotNull(new CorsConfig().corsFilter());
        assertInstanceOf(CorsWebFilter.class, new CorsConfig().corsFilter());
    }
}
