package com.catalog.common.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SkuGeneratorTest {

    @Test
    void shouldGenerateExpectedSkuFormat() {
        SkuGenerator skuGenerator = new SkuGenerator();

        String sku = skuGenerator.generate();

        assertThat(sku).startsWith("VAR-");
        assertThat(sku).hasSize(16);
        assertThat(sku).matches("VAR-[A-F0-9]{12}");
    }
}

