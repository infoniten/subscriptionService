package com.example.subscription.service.validation.metamodel;

/**
 * Fetches the metamodel from DataDictionary and assembles a {@link MetamodelCatalog}.
 */
public interface MetamodelClient {

    /**
     * Loads the current metamodel. Throws {@link MetamodelUnavailableException} if DataDictionary
     * cannot be reached or returns an unusable payload.
     */
    MetamodelCatalog fetchCatalog();
}
