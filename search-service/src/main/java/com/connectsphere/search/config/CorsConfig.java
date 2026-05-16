package com.connectsphere.search.config;

import org.springframework.context.annotation.Configuration;

/**
 * CorsConfig — DISABLED in individual microservices.
 *
 * CORS is handled centrally by the API Gateway (CorsWebFilter in api-gateway).
 * Having CORS configured in BOTH the gateway AND each service caused the
 * "Access-Control-Allow-Origin contains multiple values" browser error,
 * which blocked all API calls from the frontend.
 *
 * Each service runs behind the gateway, so the browser never talks to them
 * directly — no CORS headers are needed here.
 */
@Configuration
public class CorsConfig {
    // intentionally empty — CORS handled by API Gateway only
}
