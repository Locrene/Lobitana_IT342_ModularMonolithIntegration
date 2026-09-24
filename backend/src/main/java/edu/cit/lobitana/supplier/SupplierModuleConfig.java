package edu.cit.lobitana.supplier;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(LegacySupplyProperties.class)
class SupplierModuleConfig {

    /**
     * Private to this module so no other module can borrow a RestTemplate that is
     * pre-pointed at LegacySupply. Both timeouts are capped so a slow supplier
     * cannot hold a thread (or a DB connection) longer than the configured budget.
     */
    @Bean
    RestTemplate legacySupplyRestTemplate(LegacySupplyProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(properties.getTimeoutMs()));
        factory.setReadTimeout(Duration.ofMillis(properties.getTimeoutMs()));
        return new RestTemplate(factory);
    }
}
