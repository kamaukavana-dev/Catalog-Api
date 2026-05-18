package com.catalog.common.util;

import java.text.Normalizer;
import java.util.regex.Pattern;

public final class SlugUtils {

    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9\\s-]");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern MULTIPLE_HYPHENS = Pattern.compile("-+");

    private SlugUtils() {}

    public static String toSlug(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("Cannot generate slug from blank input");
        }

        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD);
        String lower = normalized.toLowerCase();
        String noSpecial = NON_ALPHANUMERIC.matcher(lower).replaceAll("");
        String hyphenated = WHITESPACE.matcher(noSpecial).replaceAll("-");
        String clean = MULTIPLE_HYPHENS.matcher(hyphenated).replaceAll("-");

        return clean.strip().replaceAll("^-|-$", "");
    }
}

