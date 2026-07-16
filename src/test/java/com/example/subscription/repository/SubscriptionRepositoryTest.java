package com.example.subscription.repository;

import com.example.subscription.domain.EngineType;
import com.example.subscription.domain.Subscription;
import com.example.subscription.domain.SubscriptionStatus;
import com.example.subscription.domain.SubscriptionTarget;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class SubscriptionRepositoryTest {

    @Autowired
    private SubscriptionRepository repository;

    private Subscription sub(String id, String subscriber, String postfix,
                             EngineType engine, SubscriptionStatus status) {
        return new Subscription(id, subscriber, postfix, engine, "f==1",
                List.of("a", "b"), List.of(new SubscriptionTarget("Trade", true)), status);
    }

    @Test
    void persistsFieldsAndTimestamps() {
        Subscription saved = repository.saveAndFlush(
                sub("sub-1", "risk", "prod", EngineType.OBJECT_STREAM, SubscriptionStatus.ACTIVE));

        Subscription found = repository.findById("sub-1").orElseThrow();
        assertThat(found.getFields()).containsExactly("a", "b");
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getUpdatedAt()).isNotNull();
        assertThat(saved.getEngine()).isEqualTo(EngineType.OBJECT_STREAM);
    }

    @Test
    void listingSpecificationFiltersAndHidesDeletedByDefault() {
        repository.saveAndFlush(sub("s1", "risk", "prod", EngineType.OBJECT_STREAM, SubscriptionStatus.ACTIVE));
        repository.saveAndFlush(sub("s2", "risk", "dev", EngineType.EVENT_WITH_REMOVE, SubscriptionStatus.PAUSED));
        repository.saveAndFlush(sub("s3", "risk", "prod", EngineType.OBJECT_STREAM, SubscriptionStatus.DELETED));
        repository.saveAndFlush(sub("s4", "other", "prod", EngineType.OBJECT_STREAM, SubscriptionStatus.ACTIVE));

        List<Subscription> all = repository.findAll(
                SubscriptionSpecifications.forListing("risk", null, null, null));
        assertThat(all).extracting(Subscription::getId).containsExactlyInAnyOrder("s1", "s2");

        List<Subscription> byStatus = repository.findAll(
                SubscriptionSpecifications.forListing("risk", SubscriptionStatus.DELETED, null, null));
        assertThat(byStatus).extracting(Subscription::getId).containsExactly("s3");

        List<Subscription> byTopicAndEngine = repository.findAll(
                SubscriptionSpecifications.forListing("risk", null, "prod", EngineType.OBJECT_STREAM));
        assertThat(byTopicAndEngine).extracting(Subscription::getId).containsExactly("s1");
    }

    @Test
    void countQueriesIgnoreDeleted() {
        repository.saveAndFlush(sub("s1", "risk", "prod", EngineType.OBJECT_STREAM, SubscriptionStatus.ACTIVE));
        repository.saveAndFlush(sub("s2", "risk", "dev", EngineType.OBJECT_STREAM, SubscriptionStatus.PAUSED));
        repository.saveAndFlush(sub("s3", "risk", "prod", EngineType.OBJECT_STREAM, SubscriptionStatus.DELETED));

        assertThat(repository.countBySubscriberNameAndStatusNot("risk", SubscriptionStatus.DELETED)).isEqualTo(2);
        assertThat(repository.countDistinctTopicsBySubscriber("risk", SubscriptionStatus.DELETED)).isEqualTo(2);
        assertThat(repository.existsBySubscriberNameAndTopicPostfixAndStatusNot(
                "risk", "prod", SubscriptionStatus.DELETED)).isTrue();
        assertThat(repository.existsBySubscriberNameAndTopicPostfixAndStatusNot(
                "risk", "nope", SubscriptionStatus.DELETED)).isFalse();
    }
}
