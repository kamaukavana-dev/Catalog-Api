package com.catalog.common.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lightweight liveness endpoint on the public web port.
 *
 * <p>Actuator lives on the private management port (9001) and is not routed
 * by Render, so this provides a public, dependency-free target for Render's
 * platform Health Check Path. It intentionally performs no downstream checks
 * — it only proves the web tier is accepting requests.
 */
@RestController
public class PingController {

    @GetMapping("/ping")
    public String ping() {
        return "ok";
    }
}
