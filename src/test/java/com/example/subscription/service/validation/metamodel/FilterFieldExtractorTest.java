package com.example.subscription.service.validation.metamodel;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FilterFieldExtractorTest {

    @Test
    void extractsSingleSelector() {
        assertThat(FilterFieldExtractor.extract("Trade.portfolioId==6052"))
                .containsExactly("Trade.portfolioId");
    }

    @Test
    void extractsNestedAndMultipleSelectors() {
        assertThat(FilterFieldExtractor.extract(
                "FxSpotForwardTrade.baseCurrency.code==USD;Trade.portfolioId=gt=100"))
                .containsExactlyInAnyOrder("FxSpotForwardTrade.baseCurrency.code", "Trade.portfolioId");
    }

    @Test
    void ignoresValuesAndUnqualifiedTokens() {
        // numbers and bare identifiers are not class-qualified selectors
        assertThat(FilterFieldExtractor.extract("Trade.portfolioId==6052")).hasSize(1);
        assertThat(FilterFieldExtractor.extract("")).isEmpty();
        assertThat(FilterFieldExtractor.extract(null)).isEmpty();
    }
}
