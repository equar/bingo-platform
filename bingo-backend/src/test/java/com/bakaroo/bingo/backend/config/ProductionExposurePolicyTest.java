package com.bakaroo.bingo.backend.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

class ProductionExposurePolicyTest {
    @Test
    void productionDisablesApiDocsAndExposesOnlyRedactedHealth() throws Exception {
        var environment = new StandardEnvironment();
        new YamlPropertySourceLoader().load("application-prod", new ClassPathResource("application-prod.yml"))
                .forEach(environment.getPropertySources()::addFirst);

        assertThat(environment.getProperty("springdoc.api-docs.enabled", Boolean.class)).isFalse();
        assertThat(environment.getProperty("springdoc.swagger-ui.enabled", Boolean.class)).isFalse();
        assertThat(environment.getProperty("management.endpoints.web.exposure.include")).isEqualTo("health");
        assertThat(environment.getProperty("management.endpoint.health.show-components")).isEqualTo("never");
        assertThat(environment.getProperty("management.endpoint.health.show-details")).isEqualTo("never");
    }

    @Test
    void defaultDeveloperConfigurationRetainsApiDocsAndSwaggerUi() throws Exception {
        var environment = new StandardEnvironment();
        new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yml"))
                .forEach(environment.getPropertySources()::addFirst);

        assertThat(environment.getProperty("springdoc.api-docs.enabled", Boolean.class)).isTrue();
        assertThat(environment.getProperty("springdoc.swagger-ui.enabled", Boolean.class)).isTrue();
    }
}
