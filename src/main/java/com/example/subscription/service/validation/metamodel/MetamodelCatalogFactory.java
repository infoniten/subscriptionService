package com.example.subscription.service.validation.metamodel;

import com.example.subscription.service.validation.metamodel.dto.MetadataResponse;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Assembles an immutable {@link MetamodelCatalog} from the single DataDictionary v3 metadata
 * response, which already carries classes, declared scalar fields, the hierarchy and relations
 * (with canonical target classes).
 */
public final class MetamodelCatalogFactory {

    private MetamodelCatalogFactory() {
    }

    public static MetamodelCatalog build(MetadataResponse metadata) {
        Map<String, String> sourceValueToCanonical = new HashMap<>();
        Map<String, String> canonicalToSourceValue = new HashMap<>();
        if (metadata.classes() != null) {
            for (MetadataResponse.ClassEntry c : metadata.classes()) {
                if (c.name() == null || c.sourceValue() == null) {
                    continue;
                }
                sourceValueToCanonical.put(c.sourceValue(), c.name());
                canonicalToSourceValue.put(c.name(), c.sourceValue());
            }
        }

        Map<String, Set<String>> scalarFieldsByCanonical = new HashMap<>();
        if (metadata.fields() != null) {
            for (Map.Entry<String, MetadataResponse.FieldsBlock> e : metadata.fields().entrySet()) {
                Set<String> names = new LinkedHashSet<>();
                MetadataResponse.FieldsBlock block = e.getValue();
                if (block != null && block.declaredFields() != null) {
                    for (MetadataResponse.FieldEntry f : block.declaredFields()) {
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

        // relations are keyed by canonical source class; targetClass is already canonical.
        Map<String, Map<String, String>> relationsByCanonical = new HashMap<>();
        if (metadata.relations() != null) {
            for (Map.Entry<String, List<MetadataResponse.RelationEntry>> e : metadata.relations().entrySet()) {
                if (e.getValue() == null) {
                    continue;
                }
                Map<String, String> byAlias = new HashMap<>();
                for (MetadataResponse.RelationEntry r : e.getValue()) {
                    String alias = r.pathName();
                    if (alias != null && r.targetClass() != null) {
                        byAlias.put(alias, r.targetClass());
                    }
                }
                if (!byAlias.isEmpty()) {
                    relationsByCanonical.put(e.getKey(), byAlias);
                }
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
