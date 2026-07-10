package com.example.subscription.service.runtime;

/**
 * Runtime configuration store (Redis). Every method participates in the mandatory write-path:
 * on failure it throws {@link com.example.subscription.exception.RedisUnavailableException},
 * which rolls back the surrounding transaction and results in HTTP 503.
 */
public interface RuntimeConfigStore {

    /**
     * Upserts the runtime config for an ACTIVE/PAUSED subscription and ensures it is present in
     * the runtime set. Used on create, pause and resume.
     */
    void put(RuntimeConfig config);

    /**
     * Removes a subscription from the runtime store (config key + runtime set).
     * Used on delete and fail (DELETED/FAILED are not part of the runtime).
     */
    void remove(String subscriptionId);
}
