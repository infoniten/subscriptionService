package com.example.subscription.service.validation;

import com.example.subscription.api.error.ErrorCode;
import com.example.subscription.domain.EngineType;
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
 * Validates the returned {@code fields} list and the filter's field selectors against the
 * metamodel loaded from DataDictionary.
 *
 * <p>Field paths are {@code Class.field} or, for reference traversal, {@code Class.relation.field}
 * (e.g. {@code FxSpotForwardTrade.baseCurrency.code}). Fields errors are reported as
 * {@code INVALID_FIELDS}; filter errors as {@code INVALID_FILTER}. RSQL itself is not compiled —
 * that remains the Engine's responsibility.
 */
@Component
public class MetamodelSubscriptionValidator implements SubscriptionValidator {

    private final MetamodelCatalogHolder catalogHolder;

    public MetamodelSubscriptionValidator(MetamodelCatalogHolder catalogHolder) {
        this.catalogHolder = catalogHolder;
    }

    @Override
    public void validate(String subscriberName, EngineType engine, String filter, List<String> fields) {
        MetamodelCatalog catalog = catalogHolder.get();

        validateReturnedFields(catalog, fields);
        validateFilterFields(catalog, filter);
    }

    private void validateReturnedFields(MetamodelCatalog catalog, List<String> fields) {
        if (fields == null) {
            return; // structural checks (non-empty) happen earlier in SubscriptionInputParser
        }
        List<String> errors = new ArrayList<>();
        for (String field : fields) {
            catalog.validateFieldPath(field).ifPresent(errors::add);
        }
        if (!errors.isEmpty()) {
            throw new ValidationException(ErrorCode.INVALID_FIELDS,
                    "Invalid fields: " + String.join("; ", errors),
                    Map.of("fields", errors));
        }
    }

    private void validateFilterFields(MetamodelCatalog catalog, String filter) {
        Set<String> selectors = FilterFieldExtractor.extract(filter);
        List<String> errors = new ArrayList<>();
        for (String selector : selectors) {
            Optional<String> error = catalog.validateFieldPath(selector);
            error.ifPresent(errors::add);
        }
        if (!errors.isEmpty()) {
            throw new ValidationException(ErrorCode.INVALID_FILTER,
                    "Invalid filter: " + String.join("; ", errors),
                    Map.of("filter", errors));
        }
    }
}
