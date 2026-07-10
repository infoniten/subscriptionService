package com.example.subscription.service.validation.metamodel;

import com.example.subscription.service.validation.metamodel.dto.MetadataResponse;
import com.example.subscription.service.validation.metamodel.dto.MetadataResponse.ClassEntry;
import com.example.subscription.service.validation.metamodel.dto.MetadataResponse.FieldEntry;
import com.example.subscription.service.validation.metamodel.dto.MetadataResponse.FieldsBlock;
import com.example.subscription.service.validation.metamodel.dto.MetadataResponse.Hierarchy;
import com.example.subscription.service.validation.metamodel.dto.RelationsResponse;
import com.example.subscription.service.validation.metamodel.dto.RelationsResponse.RelationEntry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MetamodelCatalogTest {

    private final MetamodelCatalog catalog = sampleCatalog();

    /**
     * Sample modelled after the real metamodel:
     * Entity(id) <- Trade(portfolioId, contractId) <- FxSpotForwardTrade(baseAmount)
     * Entity(id) <- Currency(code, name); relation FxSpotForwardTrade.baseCurrency -> Currency.
     */
    private static MetamodelCatalog sampleCatalog() {
        MetadataResponse metadata = new MetadataResponse(
                List.of(
                        new ClassEntry("ENTITY", "Entity"),
                        new ClassEntry("TRADE", "Trade"),
                        new ClassEntry("FX_SPOT_FORWARD_TRADE", "FxSpotForwardTrade"),
                        new ClassEntry("CURRENCY", "Currency")),
                Map.of(
                        "ENTITY", block("id"),
                        "TRADE", block("portfolioId", "contractId"),
                        "FX_SPOT_FORWARD_TRADE", block("baseAmount"),
                        "CURRENCY", block("code", "name")),
                new Hierarchy(Map.of(
                        "ENTITY", List.of("ENTITY"),
                        "TRADE", List.of("TRADE", "ENTITY"),
                        "FX_SPOT_FORWARD_TRADE", List.of("FX_SPOT_FORWARD_TRADE", "TRADE", "ENTITY"),
                        "CURRENCY", List.of("CURRENCY", "ENTITY"))));

        RelationsResponse relations = new RelationsResponse(List.of(
                new RelationEntry("baseCurrencyId", "baseCurrency",
                        "FxSpotForwardTrade", "Currency", "global_link")));

        return MetamodelCatalogFactory.build(metadata, relations);
    }

    private static FieldsBlock block(String... names) {
        return new FieldsBlock(java.util.Arrays.stream(names)
                .map(n -> new FieldEntry(n, "string")).toList());
    }

    @Test
    void acceptsDirectScalarField() {
        assertThat(catalog.validateFieldPath("Trade.portfolioId")).isEmpty();
        assertThat(catalog.validateFieldPath("FxSpotForwardTrade.baseAmount")).isEmpty();
        assertThat(catalog.validateFieldPath("Entity.id")).isEmpty();
    }

    @Test
    void acceptsInheritedField() {
        // portfolioId from Trade, id from Entity — both visible on FxSpotForwardTrade
        assertThat(catalog.validateFieldPath("FxSpotForwardTrade.portfolioId")).isEmpty();
        assertThat(catalog.validateFieldPath("FxSpotForwardTrade.id")).isEmpty();
    }

    @Test
    void acceptsRelationTraversal() {
        assertThat(catalog.validateFieldPath("FxSpotForwardTrade.baseCurrency.code")).isEmpty();
        assertThat(catalog.validateFieldPath("FxSpotForwardTrade.baseCurrency.name")).isEmpty();
    }

    @Test
    void rejectsUnknownClass() {
        assertThat(catalog.validateFieldPath("Nope.foo")).get().asString().contains("unknown class");
    }

    @Test
    void rejectsUnknownField() {
        assertThat(catalog.validateFieldPath("Trade.missing")).get().asString().contains("unknown field");
    }

    @Test
    void rejectsUnknownFieldOnRelationTarget() {
        assertThat(catalog.validateFieldPath("FxSpotForwardTrade.baseCurrency.missing"))
                .get().asString().contains("unknown field");
    }

    @Test
    void rejectsRelationUsedAsLeaf() {
        // baseCurrency is a relation, not a scalar field — cannot be selected directly
        assertThat(catalog.validateFieldPath("FxSpotForwardTrade.baseCurrency"))
                .get().asString().contains("unknown field");
    }

    @Test
    void rejectsScalarUsedAsIntermediate() {
        assertThat(catalog.validateFieldPath("FxSpotForwardTrade.baseAmount.code"))
                .get().asString().contains("unknown relation");
    }

    @Test
    void rejectsUnqualifiedField() {
        assertThat(catalog.validateFieldPath("Trade")).get().asString().contains("Class.field");
    }
}
