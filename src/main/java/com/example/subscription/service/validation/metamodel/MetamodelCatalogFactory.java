package com.example.subscription.service.validation.metamodel;

import com.example.subscription.service.validation.metamodel.dto.MetadataResponse;
import com.example.subscription.service.validation.metamodel.dto.RelationsResponse;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Assembles an immutable {@link MetamodelCatalog} from the two DataDictionary responses:
 * scalar fields + hierarchy from the search-service metadata, and relations from the metamodel
 * export. The two are bridged by the class {@code sourceValue} (framework class name).
 */
public final class MetamodelCatalogFactory {

    private MetamodelCatalogFactory() {
    }

    public static MetamodelCatalog build(MetadataResponse metadata, RelationsResponse relations) {
        Map<String, String> sourceValueToCanonical = new HashMap<>();
        Map<String, String> canonicalToSourceValue = new HashMap<>();
        if (metadata.classes() != null) {
            for (MetadataResponse.ClassEntry c : metadata.classes()) {
                if (c.name() == null) {
                    continue;
                }
                if (c.sourceValue() != null) {
                    sourceValueToCanonical.put(c.sourceValue(), c.name());
                    canonicalToSourceValue.put(c.name(), c.sourceValue());
                }
            }
        }

        Map<String, Set<String>> scalarFieldsByCanonical = new HashMap<>();
        if (metadata.fields() != null) {
            for (Map.Entry<String, MetadataResponse.FieldsBlock> e : metadata.fields().entrySet()) {
                Set<String> names = new LinkedHashSet<>();
                MetadataResponse.FieldsBlock block = e.getValue();
                if (block != null && block.jsonFields() != null) {
                    for (MetadataResponse.FieldEntry f : block.jsonFields()) {
                        if (f.name() != null) {
                            names.add(f.name());
                        }
                    }
                }
                scalarFieldsByCanonical.put(e.getKey(), names);
            }
        }

        Map<String, List<String>> parentsOrSelf = new HashMap<>();
        if (metadata.hierarchy() != null && metadata.hierarchy().parentsOrSelf() != null) {
            parentsOrSelf.putAll(metadata.hierarchy().parentsOrSelf());
        }

        Map<String, Map<String, String>> relationsByCanonical = new HashMap<>();
        if (relations != null && relations.relations() != null) {
            for (RelationsResponse.RelationEntry r : relations.relations()) {
                String sourceCanonical = sourceValueToCanonical.get(r.sourceClassName());
                String targetCanonical = sourceValueToCanonical.get(r.targetClassName());
                String alias = r.pathName();
                if (sourceCanonical == null || targetCanonical == null || alias == null) {
                    continue;
                }
                relationsByCanonical
                        .computeIfAbsent(sourceCanonical, k -> new HashMap<>())
                        .put(alias, targetCanonical);
            }
        }

        return new MetamodelCatalog(
                Map.copyOf(sourceValueToCanonical),
                Map.copyOf(canonicalToSourceValue),
                Map.copyOf(scalarFieldsByCanonical),
                Map.copyOf(parentsOrSelf),
                Map.copyOf(relationsByCanonical));
    }
}
