package com.example.subscription.service.validation.metamodel.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;

/**
 * Subset of the DataDictionary {@code /api/search-service/metadata/v3} response needed for
 * validation: the class list (sourceValue &lt;-&gt; canonical name), declared scalar fields per
 * class, the hierarchy, and the relations (reference targets) per class. Unknown properties are
 * ignored so the contract can evolve without breaking us.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MetadataResponse(
        List<ClassEntry> classes,
        Map<String, FieldsBlock> fields,
        Hierarchy hierarchy,
        Map<String, List<RelationEntry>> relations
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ClassEntry(String name, String sourceValue) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FieldsBlock(List<FieldEntry> declaredFields) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FieldEntry(String name, String type) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Hierarchy(Map<String, List<String>> parentsOrSelf) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RelationEntry(String name, String alias, String type, String targetClass) {

        /** Name used in field paths: the alias (GLOBAL_LINK) when present, otherwise the name (*_SET/*_ITEM). */
        public String pathName() {
            return alias != null && !alias.isBlank() ? alias : name;
        }
    }
}
