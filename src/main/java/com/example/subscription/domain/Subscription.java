package com.example.subscription.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Immutable subscription configuration.
 *
 * <p>The configuration (filter, fields, engine, topicPostfix) is never mutated after creation:
 * any change is modelled by the client as a brand new subscription with a new id. Only the
 * lifecycle {@link #status} (and the failure diagnostics) change over time.
 */
@Entity
@Table(name = "subscription")
public class Subscription {

    @Id
    @Column(name = "id", nullable = false, updatable = false, length = 64)
    private String id;

    @Column(name = "subscriber_name", nullable = false, updatable = false)
    private String subscriberName;

    @Column(name = "topic_postfix", nullable = false, updatable = false)
    private String topicPostfix;

    @Enumerated(EnumType.STRING)
    @Column(name = "engine", nullable = false, updatable = false, length = 64)
    private EngineType engine;

    @Column(name = "filter", updatable = false)
    private String filter;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "subscription_fields", joinColumns = @JoinColumn(name = "subscription_id"))
    @OrderColumn(name = "field_order")
    @Column(name = "field_name", nullable = false)
    private List<String> fields = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private SubscriptionStatus status;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "failure_message")
    private String failureMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Subscription() {
        // for JPA
    }

    public Subscription(String id,
                        String subscriberName,
                        String topicPostfix,
                        EngineType engine,
                        String filter,
                        List<String> fields,
                        SubscriptionStatus status) {
        this.id = id;
        this.subscriberName = subscriberName;
        this.topicPostfix = topicPostfix;
        this.engine = engine;
        this.filter = filter;
        this.fields = fields == null ? new ArrayList<>() : new ArrayList<>(fields);
        this.status = status;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public void markPaused() {
        this.status = SubscriptionStatus.PAUSED;
    }

    public void markActive() {
        this.status = SubscriptionStatus.ACTIVE;
    }

    public void markDeleted() {
        this.status = SubscriptionStatus.DELETED;
    }

    public void markFailed(String reason, String message) {
        this.status = SubscriptionStatus.FAILED;
        this.failureReason = reason;
        this.failureMessage = message;
    }

    public String getId() {
        return id;
    }

    public String getSubscriberName() {
        return subscriberName;
    }

    public String getTopicPostfix() {
        return topicPostfix;
    }

    public EngineType getEngine() {
        return engine;
    }

    public String getFilter() {
        return filter;
    }

    public List<String> getFields() {
        return fields;
    }

    public SubscriptionStatus getStatus() {
        return status;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public String getFailureMessage() {
        return failureMessage;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
