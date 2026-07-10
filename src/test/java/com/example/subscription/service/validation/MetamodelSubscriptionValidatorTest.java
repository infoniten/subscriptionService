package com.example.subscription.service.validation;

import com.example.subscription.api.error.ErrorCode;
import com.example.subscription.domain.EngineType;
import com.example.subscription.exception.ValidationException;
import com.example.subscription.service.validation.metamodel.MetamodelCatalog;
import com.example.subscription.service.validation.metamodel.MetamodelCatalogFactory;
import com.example.subscription.service.validation.metamodel.MetamodelCatalogHolder;
import com.example.subscription.service.validation.metamodel.dto.MetadataResponse;
import com.example.subscription.service.validation.metamodel.dto.MetadataResponse.ClassEntry;
import com.example.subscription.service.validation.metamodel.dto.MetadataResponse.FieldEntry;
import com.example.subscription.service.validation.metamodel.dto.MetadataResponse.FieldsBlock;
import com.example.subscription.service.validation.metamodel.dto.MetadataResponse.Hierarchy;
import com.example.subscription.service.validation.metamodel.dto.RelationsResponse;
import com.example.subscription.service.validation.metamodel.dto.RelationsResponse.RelationEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MetamodelSubscriptionValidatorTest {

    private MetamodelSubscriptionValidator validator;

    @BeforeEach
    void setUp() {
        MetamodelCatalogHolder holder = mock(MetamodelCatalogHolder.class);
        when(holder.get()).thenReturn(catalog());
        validator = new MetamodelSubscriptionValidator(holder);
    }

    private static MetamodelCatalog catalog() {
        MetadataResponse metadata = new MetadataResponse(
                List.of(
                        new ClassEntry("TRADE", "Trade"),
                        new ClassEntry("FX_SPOT_FORWARD_TRADE", "FxSpotForwardTrade"),
                        new ClassEntry("CURRENCY", "Currency")),
                Map.of(
                        "TRADE", new FieldsBlock(List.of(new FieldEntry("portfolioId", "long"))),
                        "FX_SPOT_FORWARD_TRADE", new FieldsBlock(List.of(new FieldEntry("baseAmount", "BigDecimal"))),
                        "CURRENCY", new FieldsBlock(List.of(new FieldEntry("code", "string")))),
                new Hierarchy(Map.of(
                        "TRADE", List.of("TRADE"),
                        "FX_SPOT_FORWARD_TRADE", List.of("FX_SPOT_FORWARD_TRADE", "TRADE"),
                        "CURRENCY", List.of("CURRENCY"))));
        RelationsResponse relations = new RelationsResponse(List.of(
                new RelationEntry("baseCurrencyId", "baseCurrency", "FxSpotForwardTrade", "Currency", "global_link")));
        return MetamodelCatalogFactory.build(metadata, relations);
    }

    @Test
    void acceptsValidFieldsAndFilter() {
        assertThatCode(() -> validator.validate("risk", EngineType.EVENT_WITH_REMOVE,
                "Trade.portfolioId==6052",
                List.of("FxSpotForwardTrade.baseAmount", "FxSpotForwardTrade.baseCurrency.code", "Trade.portfolioId")))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsUnknownFieldWithInvalidFields() {
        assertThatThrownBy(() -> validator.validate("risk", EngineType.OBJECT_STREAM, null,
                List.of("Trade.portfolioId", "Trade.wrongField")))
                .isInstanceOf(ValidationException.class)
                .extracting(e -> ((ValidationException) e).getCode())
                .isEqualTo(ErrorCode.INVALID_FIELDS);
    }

    @Test
    void rejectsUnknownFilterSelectorWithInvalidFilter() {
        assertThatThrownBy(() -> validator.validate("risk", EngineType.OBJECT_STREAM,
                "Trade.doesNotExist==1", List.of("Trade.portfolioId")))
                .isInstanceOf(ValidationException.class)
                .extracting(e -> ((ValidationException) e).getCode())
                .isEqualTo(ErrorCode.INVALID_FILTER);
    }

    @Test
    void acceptsNullFilter() {
        assertThatCode(() -> validator.validate("risk", EngineType.OBJECT_STREAM, null,
                List.of("Trade.portfolioId")))
                .doesNotThrowAnyException();
    }
}
