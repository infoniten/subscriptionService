package com.example.subscription.service.validation.metamodel;

import java.util.LinkedHashSet;
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
    private final Map<String, List<String>> subtypesOrSelf;
    private final Map<String, Map<String, String>> relationsByCanonical;

    MetamodelCatalog(Map<String, String> sourceValueToCanonical,
                     Map<String, String> canonicalToSourceValue,
                     Map<String, Set<String>> scalarFieldsByCanonical,
                     Map<String, List<String>> parentsOrSelf,
                     Map<String, List<String>> subtypesOrSelf,
                     Map<String, Map<String, String>> relationsByCanonical) {
        this.sourceValueToCanonical = sourceValueToCanonical;
        this.canonicalToSourceValue = canonicalToSourceValue;
        this.scalarFieldsByCanonical = scalarFieldsByCanonical;
        this.parentsOrSelf = parentsOrSelf;
        this.subtypesOrSelf = subtypesOrSelf;
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
        // Once a segment traverses a relation, the value it points at is polymorphic: an
        // EMBEDDED_SET/GLOBAL_LINK declared as type T can hold any concrete subtype of T at runtime.
        // From that point on, resolve fields/relations against T's subtree (ancestors + subtypes),
        // not only its inheritance chain. The root class is not expanded — the caller qualified it
        // explicitly (e.g. FxSpotForwardTrade vs Trade).
        boolean polymorphic = false;
        for (int i = 1; i < parts.length; i++) {
            String segment = parts[i];
            boolean last = i == parts.length - 1;
            if (last) {
                if (!hasScalarField(current, segment, polymorphic)) {
                    return Optional.of("unknown field '" + segment + "' on class '"
                            + sourceValueOf(current) + "' in '" + rawPath + "'");
                }
            } else {
                String target = resolveRelationTarget(current, segment, polymorphic);
                if (target == null) {
                    return Optional.of("unknown relation '" + segment + "' on class '"
                            + sourceValueOf(current) + "' in '" + rawPath + "'");
                }
                current = target;
                polymorphic = true;
            }
        }
        return Optional.empty();
    }

    /** Resolves a class token (sourceValue or canonical) to its canonical name, if known. */
    public Optional<String> canonicalOf(String token) {
        return token == null ? Optional.empty() : Optional.ofNullable(resolveClass(token));
    }

    /** True if {@code ancestorCanonical} is {@code canonical} or one of its ancestors. */
    public boolean isAncestorOrSelf(String ancestorCanonical, String canonical) {
        return chain(canonical).contains(ancestorCanonical);
    }

    /** True if {@code subtypeCanonical} is {@code canonical} or one of its subtypes. */
    public boolean isSubtypeOrSelf(String subtypeCanonical, String canonical) {
        return subtypes(canonical).contains(subtypeCanonical);
    }

    /** Resolves a class token (by sourceValue, or a canonical name) to its canonical name. */
    private String resolveClass(String token) {
        String canonical = sourceValueToCanonical.get(token);
        if (canonical != null) {
            return canonical;
        }
        return scalarFieldsByCanonical.containsKey(token) ? token : null;
    }

    private boolean hasScalarField(String canonical, String field, boolean includeSubtypes) {
        for (String related : resolutionClasses(canonical, includeSubtypes)) {
            Set<String> fields = scalarFieldsByCanonical.get(related);
            if (fields != null && fields.contains(field)) {
                return true;
            }
        }
        return false;
    }

    private String resolveRelationTarget(String canonical, String alias, boolean includeSubtypes) {
        for (String related : resolutionClasses(canonical, includeSubtypes)) {
            Map<String, String> relations = relationsByCanonical.get(related);
            if (relations != null && relations.containsKey(alias)) {
                return relations.get(alias);
            }
        }
        return null;
    }

    /**
     * Classes whose declared members are visible on {@code canonical}: always its inheritance chain
     * (self + ancestors); plus, when {@code includeSubtypes} is set (i.e. we arrived here via a
     * relation and the runtime object may be a concrete subtype), all of its subtypes.
     */
    private Set<String> resolutionClasses(String canonical, boolean includeSubtypes) {
        Set<String> classes = new LinkedHashSet<>(chain(canonical));
        if (includeSubtypes) {
            classes.addAll(subtypes(canonical));
        }
        return classes;
    }

    private List<String> chain(String canonical) {
        List<String> chain = parentsOrSelf.get(canonical);
        return chain != null && !chain.isEmpty() ? chain : List.of(canonical);
    }

    private List<String> subtypes(String canonical) {
        List<String> subtypes = subtypesOrSelf.get(canonical);
        return subtypes != null && !subtypes.isEmpty() ? subtypes : List.of(canonical);
    }

    private String sourceValueOf(String canonical) {
        return canonicalToSourceValue.getOrDefault(canonical, canonical);
    }
}
