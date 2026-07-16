package com.example.subscription.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.util.Objects;

/**
 * One class target of a subscription: the object class it selects, and whether the selection is
 * polymorphic (the class and all its subclasses) or exact (only that class). A subscription may have
 * several targets ("multi-class").
 */
@Embeddable
public class SubscriptionTarget {

    @Column(name = "object_class", nullable = false)
    private String objectClass;

    @Column(name = "include_subclasses", nullable = false)
    private boolean includeSubclasses;

    protected SubscriptionTarget() {
        // for JPA
    }

    public SubscriptionTarget(String objectClass, boolean includeSubclasses) {
        this.objectClass = objectClass;
        this.includeSubclasses = includeSubclasses;
    }

    public String getObjectClass() {
        return objectClass;
    }

    public boolean isIncludeSubclasses() {
        return includeSubclasses;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SubscriptionTarget that)) {
            return false;
        }
        return includeSubclasses == that.includeSubclasses && Objects.equals(objectClass, that.objectClass);
    }

    @Override
    public int hashCode() {
        return Objects.hash(objectClass, includeSubclasses);
    }
}
