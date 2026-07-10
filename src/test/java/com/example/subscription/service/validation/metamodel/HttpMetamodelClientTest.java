package com.example.subscription.service.validation.metamodel;

import com.example.subscription.config.SubscriptionProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
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

    private static final String METADATA_JSON = """
            {
              "classes": [
                {"name": "TRADE", "sourceValue": "Trade"},
                {"name": "FX_SPOT_FORWARD_TRADE", "sourceValue": "FxSpotForwardTrade"},
                {"name": "CURRENCY", "sourceValue": "Currency"}
              ],
              "fields": {
                "TRADE": {"mainFields": [], "indexFields": [], "jsonFields": [{"name": "portfolioId", "type": "LONG"}]},
                "FX_SPOT_FORWARD_TRADE": {"jsonFields": [{"name": "baseAmount", "type": "DECIMAL"}]},
                "CURRENCY": {"jsonFields": [{"name": "code", "type": "STRING"}]}
              },
              "hierarchy": {
                "parentsOrSelf": {
                  "TRADE": ["TRADE"],
                  "FX_SPOT_FORWARD_TRADE": ["FX_SPOT_FORWARD_TRADE", "TRADE"],
                  "CURRENCY": ["CURRENCY"]
                }
              }
            }""";

    private static final String RELATIONS_JSON = """
            {
              "relations": [
                {"relationName": "baseCurrencyId", "relationAlias": "baseCurrency",
                 "sourceClassName": "FxSpotForwardTrade", "targetClassName": "Currency",
                 "relationType": "global_link"}
              ]
            }""";

    private SubscriptionProperties props() {
        SubscriptionProperties p = new SubscriptionProperties();
        p.getMetamodel().setBaseUrl(BASE);
        return p;
    }

    @Test
    void fetchesBothEndpointsAndBuildsCatalog() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(BASE + "/api/search-service/metadata"))
                .andRespond(withSuccess(METADATA_JSON, MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "/api/metamodel/export"))
                .andRespond(withSuccess(RELATIONS_JSON, MediaType.APPLICATION_JSON));

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
        server.expect(requestTo(BASE + "/api/search-service/metadata"))
                .andRespond(withServerError());

        HttpMetamodelClient client = new HttpMetamodelClient(builder, props());
        assertThatThrownBy(client::fetchCatalog)
                .isInstanceOf(MetamodelUnavailableException.class);
    }

    @Test
    void throwsWhenMetadataEmpty() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(BASE + "/api/search-service/metadata"))
                .andRespond(withSuccess("{\"classes\": []}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "/api/metamodel/export"))
                .andRespond(withSuccess("{\"relations\": []}", MediaType.APPLICATION_JSON));

        HttpMetamodelClient client = new HttpMetamodelClient(builder, props());
        assertThatThrownBy(client::fetchCatalog)
                .isInstanceOf(MetamodelUnavailableException.class);
    }
}
