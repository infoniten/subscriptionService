package com.example.subscription.domain;

/**
 * Engine processing mode. The Subscription Service only stores the selected engine;
 * all processing logic lives in the Engine Service.
 */
public enum EngineType {
    OBJECT_STREAM,
    OBJECT_WITH_PREVIOUS,
    EVENT_WITH_REMOVE,
    /** Batch delivery handled by the Delivery Engine Batch service. */
    OBJECT_BATCH
}
