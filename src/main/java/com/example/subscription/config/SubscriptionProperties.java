package com.example.subscription.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Externalized service configuration. Backed by ConfigMap / environment variables so that
 * limits and endpoints can be tuned without rebuilding the service.
 */
@ConfigurationProperties(prefix = "subscription")
public class SubscriptionProperties {

    private final Topic topic = new Topic();
    private final Redis redis = new Redis();
    private final Initialization initialization = new Initialization();
    private final RateLimits rateLimits = new RateLimits();
    private final Metamodel metamodel = new Metamodel();

    public Topic getTopic() {
        return topic;
    }

    public Redis getRedis() {
        return redis;
    }

    public Initialization getInitialization() {
        return initialization;
    }

    public RateLimits getRateLimits() {
        return rateLimits;
    }

    public Metamodel getMetamodel() {
        return metamodel;
    }

    public static class Topic {
        /** Fixed prefix of the Kafka topic name: {prefix}.{subscriberName}.{topicPostfix}. */
        private String prefix = "subscription";

        public String getPrefix() {
            return prefix;
        }

        public void setPrefix(String prefix) {
            this.prefix = prefix;
        }
    }

    public static class Redis {
        /** Single Pub/Sub channel used as a change signal. */
        private String channel = "subscriptions:changes";
        /** Set holding the ids of runtime (ACTIVE/PAUSED) subscriptions. */
        private String runtimeSetKey = "subs:runtime";
        /** Prefix for per-subscription runtime config keys: {prefix}{subscriptionId}. */
        private String configKeyPrefix = "sub:";

        public String getChannel() {
            return channel;
        }

        public void setChannel(String channel) {
            this.channel = channel;
        }

        public String getRuntimeSetKey() {
            return runtimeSetKey;
        }

        public void setRuntimeSetKey(String runtimeSetKey) {
            this.runtimeSetKey = runtimeSetKey;
        }

        public String getConfigKeyPrefix() {
            return configKeyPrefix;
        }

        public void setConfigKeyPrefix(String configKeyPrefix) {
            this.configKeyPrefix = configKeyPrefix;
        }
    }

    public static class Initialization {
        /** Base URL of the Initialization Service. */
        private String baseUrl = "http://initialization-service";

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }
    }

    /**
     * DataDictionary integration used by the field/filter validator. The metamodel is loaded once
     * at startup (fail-fast) from a single v3 endpoint that carries classes, declared scalar fields,
     * the hierarchy and relations (for reference-traversal paths like {@code baseCurrency.code}).
     */
    public static class Metamodel {
        /** Base URL of the DataDictionary service. */
        private String baseUrl = "http://data-dictionary:8080";
        /** Search-service v3 metadata export (classes, fields, hierarchy, relations). */
        private String metadataPath = "/api/search-service/metadata/v3";

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getMetadataPath() {
            return metadataPath;
        }

        public void setMetadataPath(String metadataPath) {
            this.metadataPath = metadataPath;
        }
    }

    public static class RateLimits {
        private int maxSubscriptionsPerSubscriber = 100;
        private int maxTopicsPerSubscriber = 20;
        private int maxSubscriptionCreationsPerHour = 60;
        private int maxInitializationsPerHour = 20;
        private int maxFields = 100;
        private int maxFilterLength = 4096;

        public int getMaxSubscriptionsPerSubscriber() {
            return maxSubscriptionsPerSubscriber;
        }

        public void setMaxSubscriptionsPerSubscriber(int v) {
            this.maxSubscriptionsPerSubscriber = v;
        }

        public int getMaxTopicsPerSubscriber() {
            return maxTopicsPerSubscriber;
        }

        public void setMaxTopicsPerSubscriber(int v) {
            this.maxTopicsPerSubscriber = v;
        }

        public int getMaxSubscriptionCreationsPerHour() {
            return maxSubscriptionCreationsPerHour;
        }

        public void setMaxSubscriptionCreationsPerHour(int v) {
            this.maxSubscriptionCreationsPerHour = v;
        }

        public int getMaxInitializationsPerHour() {
            return maxInitializationsPerHour;
        }

        public void setMaxInitializationsPerHour(int v) {
            this.maxInitializationsPerHour = v;
        }

        public int getMaxFields() {
            return maxFields;
        }

        public void setMaxFields(int v) {
            this.maxFields = v;
        }

        public int getMaxFilterLength() {
            return maxFilterLength;
        }

        public void setMaxFilterLength(int v) {
            this.maxFilterLength = v;
        }
    }
}
