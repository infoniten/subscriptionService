package com.example.subscription.service.validation.metamodel;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Immutable, resolved view of the metamodel used to validate user field paths.
 *
 * <p>A field path is {@code <ClassSource>.<segment>[.<segment>...]}. The first token is a class
 * (matched by its {@code sourceValue}, e.g. {@code Trade}); intermediate segments are relation
 * traversals (e.g. {@code baseCurrency} -&gt; {@code Currency}); the final segment must be a scalar
 * field. Inheritance is honoured: fields and relations declared on any ancestor are visible.
 *
 * <p>All maps are keyed by the canonical class name (UPPER_SNAKE). Field/relation lookups walk the
 * {@code parentsOrSelf} chain, so inherited members resolve correctly.
 */
public final class MetamodelCatalog {

    private final Map<String, String> sourceValueToCanonical;
    private final Map<String, String> canonicalToSourceValue;
    private final Map<String, Set<String>> scalarFieldsByCanonical;
    private final Map<String, List<String>> parentsOrSelf;
    private final Map<String, Map<String, String>> relationsByCanonical;

    MetamodelCatalog(Map<String, String> sourceValueToCanonical,
                     Map<String, String> canonicalToSourceValue,
                     Map<String, Set<String>> scalarFieldsByCanonical,
                     Map<String, List<String>> parentsOrSelf,
                     Map<String, Map<String, String>> relationsByCanonical) {
        this.sourceValueToCanonical = sourceValueToCanonical;
        this.canonicalToSourceValue = canonicalToSourceValue;
        this.scalarFieldsByCanonical = scalarFieldsByCanonical;
        this.parentsOrSelf = parentsOrSelf;
        this.relationsByCanonical = relationsByCanonical;
    }

    public int classCount() {
        return sourceValueToCanonical.size();
    }

    /**
     * Validates a single field path.
     *
     * @return empty if valid, otherwise a human-readable reason.
     */
    public Optional<String> validateFieldPath(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            return Optional.of("empty field path");
        }
        String[] parts = rawPath.split("\\.");
        if (parts.length < 2) {
            return Optional.of("field '" + rawPath + "' must be qualified as Class.field");
        }

        String canonical = resolveClass(parts[0]);
        if (canonical == null) {
            return Optional.of("unknown class '" + parts[0] + "' in '" + rawPath + "'");
        }

        String current = canonical;
        for (int i = 1; i < parts.length; i++) {
            String segment = parts[i];
            boolean last = i == parts.length - 1;
            if (last) {
                if (!hasScalarField(current, segment)) {
                    return Optional.of("unknown field '" + segment + "' on class '"
                            + sourceValueOf(current) + "' in '" + rawPath + "'");
                }
            } else {
                String target = resolveRelationTarget(current, segment);
                if (target == null) {
                    return Optional.of("unknown relation '" + segment + "' on class '"
                            + sourceValueOf(current) + "' in '" + rawPath + "'");
                }
                current = target;
            }
        }
        return Optional.empty();
    }

    /** Resolves a class token (by sourceValue, or a canonical name) to its canonical name. */
    private String resolveClass(String token) {
        String canonical = sourceValueToCanonical.get(token);
        if (canonical != null) {
            return canonical;
        }
        return scalarFieldsByCanonical.containsKey(token) ? token : null;
    }

    private boolean hasScalarField(String canonical, String field) {
        for (String ancestor : chain(canonical)) {
            Set<String> fields = scalarFieldsByCanonical.get(ancestor);
            if (fields != null && fields.contains(field)) {
                return true;
            }
        }
        return false;
    }

    private String resolveRelationTarget(String canonical, String alias) {
        for (String ancestor : chain(canonical)) {
            Map<String, String> relations = relationsByCanonical.get(ancestor);
            if (relations != null && relations.containsKey(alias)) {
                return relations.get(alias);
            }
        }
        return null;
    }

    private List<String> chain(String canonical) {
        List<String> chain = parentsOrSelf.get(canonical);
        return chain != null && !chain.isEmpty() ? chain : List.of(canonical);
    }

    private String sourceValueOf(String canonical) {
        return canonicalToSourceValue.getOrDefault(canonical, canonical);
    }
}
