package com.example.subscription.repository;

import com.example.subscription.domain.Subscription;
import com.example.subscription.domain.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface SubscriptionRepository
        extends JpaRepository<Subscription, String>, JpaSpecificationExecutor<Subscription> {

    /** Runtime subscriptions (ACTIVE / PAUSED) — the set mirrored into Redis. */
    List<Subscription> findByStatusIn(Collection<SubscriptionStatus> statuses);

    long countBySubscriberNameAndStatusNot(String subscriberName, SubscriptionStatus status);

    @Query("select count(distinct s.topicPostfix) from Subscription s "
            + "where s.subscriberName = :subscriberName and s.status <> :excluded")
    long countDistinctTopicsBySubscriber(@Param("subscriberName") String subscriberName,
                                         @Param("excluded") SubscriptionStatus excluded);

    boolean existsBySubscriberNameAndTopicPostfixAndStatusNot(
            String subscriberName, String topicPostfix, SubscriptionStatus status);
}
