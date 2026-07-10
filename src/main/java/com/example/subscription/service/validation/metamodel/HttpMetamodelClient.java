package com.example.subscription.service.validation.metamodel;

import com.example.subscription.config.SubscriptionProperties;
import com.example.subscription.service.validation.metamodel.dto.MetadataResponse;
import com.example.subscription.service.validation.metamodel.dto.RelationsResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class HttpMetamodelClient implements MetamodelClient {

    private static final Logger log = LoggerFactory.getLogger(HttpMetamodelClient.class);

    private final RestClient restClient;
    private final SubscriptionProperties.Metamodel props;

    public HttpMetamodelClient(RestClient.Builder builder, SubscriptionProperties props) {
        this.props = props.getMetamodel();
        this.restClient = builder.baseUrl(this.props.getBaseUrl()).build();
    }

    @Override
    public MetamodelCatalog fetchCatalog() {
        MetadataResponse metadata = fetch(props.getMetadataPath(), MetadataResponse.class);
        RelationsResponse relations = fetch(props.getRelationsPath(), RelationsResponse.class);

        if (metadata == null || metadata.classes() == null || metadata.classes().isEmpty()) {
            throw new MetamodelUnavailableException(
                    "metadata response from " + props.getMetadataPath() + " is empty", null);
        }

        MetamodelCatalog catalog = MetamodelCatalogFactory.build(metadata, relations);
        log.info("Loaded metamodel from DataDictionary ({}): {} classes",
                props.getBaseUrl(), catalog.classCount());
        return catalog;
    }

    private <T> T fetch(String path, Class<T> type) {
        try {
            return restClient.get().uri(path).retrieve().body(type);
        } catch (RestClientException e) {
            throw new MetamodelUnavailableException(
                    "failed to fetch " + props.getBaseUrl() + path, e);
        }
    }
}
