package com.example.subscription.service.validation.metamodel;

import com.example.subscription.config.SubscriptionProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class HttpMetamodelClientTest {

    private static final String BASE = "http://data-dictionary:8080";
    private static final String PATH = "/api/search-service/metadata/v3";

    // Shape mirrors the real v3 response: declaredFields + relations keyed by canonical class.
    private static final String V3_JSON = """
            {
              "classes": [
                {"name": "TRADE", "sourceValue": "Trade"},
                {"name": "FX_SPOT_FORWARD_TRADE", "sourceValue": "FxSpotForwardTrade"},
                {"name": "CURRENCY", "sourceValue": "Currency"}
              ],
              "fields": {
                "TRADE": {"mainFields": [], "columnsFields": [], "declaredFields": [{"name": "portfolioId", "type": "LONG"}]},
                "FX_SPOT_FORWARD_TRADE": {"declaredFields": [{"name": "baseAmount", "type": "DECIMAL"}, {"name": "baseCurrencyId", "type": "LONG"}]},
                "CURRENCY": {"declaredFields": [{"name": "code", "type": "STRING"}]}
              },
              "hierarchy": {
                "parentsOrSelf": {
                  "TRADE": ["TRADE"],
                  "FX_SPOT_FORWARD_TRADE": ["FX_SPOT_FORWARD_TRADE", "TRADE"],
                  "CURRENCY": ["CURRENCY"]
                }
              },
              "relations": {
                "FX_SPOT_FORWARD_TRADE": [
                  {"name": "baseCurrencyId", "alias": "baseCurrency", "type": "GLOBAL_LINK", "targetClass": "CURRENCY"}
                ]
              },
              "enumTypes": {}
            }""";

    private SubscriptionProperties props() {
        SubscriptionProperties p = new SubscriptionProperties();
        p.getMetamodel().setBaseUrl(BASE);
        return p;
    }

    @Test
    void fetchesSingleEndpointAndBuildsCatalog() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(BASE + PATH))
                .andRespond(withSuccess(V3_JSON, MediaType.APPLICATION_JSON));

        HttpMetamodelClient client = new HttpMetamodelClient(builder, props());
        MetamodelCatalog catalog = client.fetchCatalog();

        server.verify();
        assertThat(catalog.classCount()).isEqualTo(3);
        assertThat(catalog.validateFieldPath("FxSpotForwardTrade.baseCurrency.code")).isEmpty();
        assertThat(catalog.validateFieldPath("FxSpotForwardTrade.portfolioId")).isEmpty();
        assertThat(catalog.validateFieldPath("Trade.unknown")).isPresent();
    }

    @Test
    void throwsMetamodelUnavailableOnServerError() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(BASE + PATH)).andRespond(withServerError());

        HttpMetamodelClient client = new HttpMetamodelClient(builder, props());
        assertThatThrownBy(client::fetchCatalog)
                .isInstanceOf(MetamodelUnavailableException.class);
    }

    @Test
    void throwsWhenMetadataEmpty() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(BASE + PATH))
                .andRespond(withSuccess("{\"classes\": []}", MediaType.APPLICATION_JSON));

        HttpMetamodelClient client = new HttpMetamodelClient(builder, props());
        assertThatThrownBy(client::fetchCatalog)
                .isInstanceOf(MetamodelUnavailableException.class);
    }
}
