package com.example.subscription.service.validation.metamodel;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts class-qualified field selectors (e.g. {@code Trade.portfolioId},
 * {@code FxSpotForwardTrade.baseCurrency.code}) from an RSQL filter string.
 *
 * <p>This is a lightweight lexical extraction — the Subscription Service never compiles RSQL
 * (that is the Engine's responsibility). It matches tokens that start with an upper-case class
 * name followed by one or more dotted segments, which is exactly the field format. Selector
 * values (numbers, quoted strings) do not match this shape.
 */
public final class FilterFieldExtractor {

    private static final Pattern SELECTOR =
            Pattern.compile("[A-Z][A-Za-z0-9]*(?:\\.[A-Za-z0-9_]+)+");

    private FilterFieldExtractor() {
    }

    public static Set<String> extract(String filter) {
        Set<String> selectors = new LinkedHashSet<>();
        if (filter == null || filter.isBlank()) {
            return selectors;
        }
        Matcher m = SELECTOR.matcher(filter);
        while (m.find()) {
            selectors.add(m.group());
        }
        return selectors;
    }
}
