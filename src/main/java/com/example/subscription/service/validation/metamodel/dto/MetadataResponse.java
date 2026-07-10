package com.example.subscription.service.validation.metamodel.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;

/**
 * Subset of the DataDictionary {@code /api/search-service/metadata} response needed for validation:
 * the class list (sourceValue &lt;-&gt; canonical name), scalar fields per class, and the hierarchy.
 * Unknown properties are ignored so the contract can evolve without breaking us.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MetadataResponse(
        List<ClassEntry> classes,
        Map<String, FieldsBlock> fields,
        Hierarchy hierarchy
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ClassEntry(String name, String sourceValue) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FieldsBlock(List<FieldEntry> jsonFields) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FieldEntry(String name, String type) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Hierarchy(Map<String, List<String>> parentsOrSelf) {
    }
}
