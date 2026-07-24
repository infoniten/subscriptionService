package com.example.subscription.config;

import io.lettuce.core.cluster.ClusterClientOptions;
import io.lettuce.core.cluster.ClusterTopologyRefreshOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisClusterConfiguration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

/**
 * Redis connection factory that works against both a standalone node and a Redis Cluster.
 *
 * <p>When {@code spring.data.redis.cluster.nodes} (env {@code REDIS_CLUSTER_NODES}) is non-empty a
 * cluster-aware Lettuce client is built — it follows {@code MOVED}/{@code ASK} redirects and refreshes
 * the cluster topology, so keys spread across slots/nodes ({@code sub:{id}}, {@code subs:runtime}) are
 * all reachable. Otherwise it falls back to a single-node client from {@code host}/{@code port}.
 *
 * <p>Defining this bean makes Spring Boot back off its own auto-configured factory, so the mode is
 * chosen explicitly here rather than by Boot's property detection.
 */
@Configuration
public class RedisConfig {

    @Bean
    public RedisConnectionFactory redisConnectionFactory(
            @Value("${spring.data.redis.host:localhost}") String host,
            @Value("${spring.data.redis.port:6379}") int port,
            @Value("${spring.data.redis.password:}") String password,
            @Value("${spring.data.redis.timeout:2s}") Duration timeout,
            @Value("${spring.data.redis.cluster.nodes:}") String clusterNodes,
            @Value("${spring.data.redis.cluster.max-redirects:3}") int maxRedirects) {

        List<String> nodes = Arrays.stream(clusterNodes.split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).toList();

        if (!nodes.isEmpty()) {
            RedisClusterConfiguration cluster = new RedisClusterConfiguration(nodes);
            cluster.setMaxRedirects(maxRedirects);
            if (!password.isEmpty()) {
                cluster.setPassword(password);
            }
            ClusterTopologyRefreshOptions refresh = ClusterTopologyRefreshOptions.builder()
                    .enablePeriodicRefresh(Duration.ofSeconds(30))
                    .enableAllAdaptiveRefreshTriggers()
                    .build();
            LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
                    .commandTimeout(timeout)
                    .clientOptions(ClusterClientOptions.builder().topologyRefreshOptions(refresh).build())
                    .build();
            return new LettuceConnectionFactory(cluster, clientConfig);
        }

        RedisStandaloneConfiguration standalone = new RedisStandaloneConfiguration(host, port);
        if (!password.isEmpty()) {
            standalone.setPassword(password);
        }
        LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
                .commandTimeout(timeout)
                .build();
        return new LettuceConnectionFactory(standalone, clientConfig);
    }
}
