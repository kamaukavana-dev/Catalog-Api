package com.catalog.common.util;

import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class SkuGenerator {

    private static final String PREFIX = "VAR-";
    private static final int CODE_LENGTH = 12;

    public String generate() {
        String uuid = UUID.randomUUID().toString().replace("-", "").toUpperCase();
        return PREFIX + uuid.substring(0, CODE_LENGTH);
    }
}

