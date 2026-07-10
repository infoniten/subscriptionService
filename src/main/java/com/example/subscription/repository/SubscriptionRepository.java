package com.example.subscription.repository;

import com.example.subscription.domain.Subscription;
import com.example.subscription.domain.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SubscriptionRepository
        extends JpaRepository<Subscription, String>, JpaSpecificationExecutor<Subscription> {

    long countBySubscriberNameAndStatusNot(String subscriberName, SubscriptionStatus status);

    @Query("select count(distinct s.topicPostfix) from Subscription s "
            + "where s.subscriberName = :subscriberName and s.status <> :excluded")
    long countDistinctTopicsBySubscriber(@Param("subscriberName") String subscriberName,
                                         @Param("excluded") SubscriptionStatus excluded);

    boolean existsBySubscriberNameAndTopicPostfixAndStatusNot(
            String subscriberName, String topicPostfix, SubscriptionStatus status);
}
