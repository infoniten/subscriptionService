package com.example.subscription.service.validation;

import com.example.subscription.api.error.ErrorCode;
import com.example.subscription.domain.EngineType;
import com.example.subscription.domain.SubscriptionTarget;
import com.example.subscription.exception.ValidationException;
import com.example.subscription.service.validation.metamodel.FilterFieldExtractor;
import com.example.subscription.service.validation.metamodel.MetamodelCatalog;
import com.example.subscription.service.validation.metamodel.MetamodelCatalogHolder;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Validates a subscription's {@code targets}, returned {@code fields} and the filter's field
 * selectors against the metamodel loaded from DataDictionary.
 *
 * <p>Targets: each {@code objectClass} must resolve to a known class ({@code INVALID_TARGETS}).
 * Fields/filter paths must resolve ({@code INVALID_FIELDS}/{@code INVALID_FILTER}) and, additionally,
 * each field's leading class must be <em>applicable to at least one target</em>: for a polymorphic
 * (SUBTREE) target the field's class may be an ancestor, the class itself, or a subtype; for an
 * exact (EXACT) target only an ancestor-or-self is allowed. RSQL itself is not compiled here.
 */
@Component
public class MetamodelSubscriptionValidator implements SubscriptionValidator {

    private final MetamodelCatalogHolder catalogHolder;

    public MetamodelSubscriptionValidator(MetamodelCatalogHolder catalogHolder) {
        this.catalogHolder = catalogHolder;
    }

    /** A target resolved to its canonical class name plus its match mode. */
    private record CanonTarget(String canonical, boolean includeSubclasses) {
    }

    @Override
    public void validate(String subscriberName, EngineType engine,
                         List<SubscriptionTarget> targets, String filter, List<String> fields) {
        MetamodelCatalog catalog = catalogHolder.get();

        List<CanonTarget> canonTargets = validateTargets(catalog, targets);
        validateReturnedFields(catalog, fields, canonTargets);
        validateFilterFields(catalog, filter, canonTargets);
    }

    private List<CanonTarget> validateTargets(MetamodelCatalog catalog, List<SubscriptionTarget> targets) {
        if (targets == null || targets.isEmpty()) {
            throw new ValidationException(ErrorCode.INVALID_TARGETS, "targets must not be empty");
        }
        List<CanonTarget> resolved = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        for (SubscriptionTarget t : targets) {
            Optional<String> canonical = catalog.canonicalOf(t.getObjectClass());
            if (canonical.isEmpty()) {
                errors.add("unknown objectClass '" + t.getObjectClass() + "'");
            } else {
                resolved.add(new CanonTarget(canonical.get(), t.isIncludeSubclasses()));
            }
        }
        if (!errors.isEmpty()) {
            throw new ValidationException(ErrorCode.INVALID_TARGETS,
                    "Invalid targets: " + String.join("; ", errors), Map.of("targets", errors));
        }
        return resolved;
    }

    private void validateReturnedFields(MetamodelCatalog catalog, List<String> fields, List<CanonTarget> targets) {
        if (fields == null) {
            return; // structural checks (non-empty) happen earlier in SubscriptionInputParser
        }
        List<String> errors = new ArrayList<>();
        for (String field : fields) {
            checkFieldPath(catalog, field, targets).ifPresent(errors::add);
        }
        if (!errors.isEmpty()) {
            throw new ValidationException(ErrorCode.INVALID_FIELDS,
                    "Invalid fields: " + String.join("; ", errors), Map.of("fields", errors));
        }
    }

    private void validateFilterFields(MetamodelCatalog catalog, String filter, List<CanonTarget> targets) {
        Set<String> selectors = FilterFieldExtractor.extract(filter);
        List<String> errors = new ArrayList<>();
        for (String selector : selectors) {
            checkFieldPath(catalog, selector, targets).ifPresent(errors::add);
        }
        if (!errors.isEmpty()) {
            throw new ValidationException(ErrorCode.INVALID_FILTER,
                    "Invalid filter: " + String.join("; ", errors), Map.of("filter", errors));
        }
    }

    /**
     * Validates one field path: first that it resolves in the metamodel, then that its leading class
     * is applicable to at least one target. Returns a reason if invalid, empty otherwise.
     */
    private Optional<String> checkFieldPath(MetamodelCatalog catalog, String path, List<CanonTarget> targets) {
        Optional<String> pathError = catalog.validateFieldPath(path);
        if (pathError.isPresent()) {
            return pathError;
        }
        int dot = path == null ? -1 : path.indexOf('.');
        if (dot <= 0) {
            return Optional.empty(); // validateFieldPath already rejects unqualified paths
        }
        String fieldClass = catalog.canonicalOf(path.substring(0, dot)).orElse(null);
        if (fieldClass == null) {
            return Optional.empty(); // unresolved prefix already caught by validateFieldPath
        }
        for (CanonTarget t : targets) {
            if (applicable(catalog, fieldClass, t)) {
                return Optional.empty();
            }
        }
        return Optional.of("field '" + path + "' is not applicable to any subscription target");
    }

    /**
     * A field whose leading class is {@code fieldClass} is applicable to a target as follows:
     * SUBTREE — fieldClass is an ancestor, the class itself, or a subtype of the target; EXACT —
     * fieldClass is an ancestor-or-self of the target (subtype-only fields are rejected).
     */
    private boolean applicable(MetamodelCatalog catalog, String fieldClass, CanonTarget target) {
        if (target.includeSubclasses()) {
            return catalog.isAncestorOrSelf(fieldClass, target.canonical())
                    || catalog.isSubtypeOrSelf(fieldClass, target.canonical());
        }
        return catalog.isAncestorOrSelf(fieldClass, target.canonical());
    }
}
