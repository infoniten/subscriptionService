package com.example.subscription.service.validation.metamodel.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Subset of the DataDictionary {@code /api/metamodel/export} response — only the relations are
 * used, to resolve reference-traversal field paths (e.g. {@code baseCurrency.code}).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RelationsResponse(List<RelationEntry> relations) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RelationEntry(
            String relationName,
            String relationAlias,
            String sourceClassName,
            String targetClassName,
            String relationType
    ) {
        /** Name used in field paths: the alias when present, otherwise the relation name. */
        public String pathName() {
            return relationAlias != null && !relationAlias.isBlank() ? relationAlias : relationName;
        }
    }
}
