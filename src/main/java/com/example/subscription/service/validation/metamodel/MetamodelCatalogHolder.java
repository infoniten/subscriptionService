package com.example.subscription.service.validation.metamodel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Holds the current {@link MetamodelCatalog}, loaded once from DataDictionary at startup.
 *
 * <p>Fail-fast: if the metamodel cannot be loaded during startup, {@link #afterPropertiesSet()}
 * throws and the application context fails to start. {@link #reload()} allows refreshing the
 * catalog later without a restart.
 */
@Component
public class MetamodelCatalogHolder implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(MetamodelCatalogHolder.class);

    private final MetamodelClient client;
    private final AtomicReference<MetamodelCatalog> ref = new AtomicReference<>();

    public MetamodelCatalogHolder(MetamodelClient client) {
        this.client = client;
    }

    @Override
    public void afterPropertiesSet() {
        log.info("Loading metamodel from DataDictionary at startup...");
        ref.set(client.fetchCatalog());
    }

    /** Reloads the catalog from DataDictionary. Throws {@link MetamodelUnavailableException} on failure. */
    public void reload() {
        ref.set(client.fetchCatalog());
        log.info("Metamodel reloaded");
    }

    public MetamodelCatalog get() {
        MetamodelCatalog catalog = ref.get();
        if (catalog == null) {
            throw new MetamodelUnavailableException("metamodel not loaded", null);
        }
        return catalog;
    }
}
