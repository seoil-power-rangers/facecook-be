package com.facecook.admin.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.PropertySourcesPlaceholdersResolver;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AdminStatsPropertiesTest {

    private static final String PREFIX = "app.admin.stats";

    @Test
    void applicationYamlDefaultsToLastActiveToday() throws IOException {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                .load("application", new ClassPathResource("application.yml"));
        Binder binder = new Binder(
                ConfigurationPropertySources.from(sources),
                new PropertySourcesPlaceholdersResolver(sources)
        );

        AdminStatsProperties properties = binder.bindOrCreate(PREFIX, AdminStatsProperties.class);

        assertThat(properties.activeUserCriterion()).isEqualTo(ActiveUserCriterion.LAST_ACTIVE_TODAY);
    }

    @Test
    void defaultValueIsLastActiveTodayWhenPropertyIsMissing() {
        Binder binder = new Binder(new MapConfigurationPropertySource(Map.of()));

        AdminStatsProperties properties = binder.bindOrCreate(PREFIX, AdminStatsProperties.class);

        assertThat(properties.activeUserCriterion()).isEqualTo(ActiveUserCriterion.LAST_ACTIVE_TODAY);
    }

    @Test
    void statusCanStillBeSelected() {
        Binder binder = new Binder(new MapConfigurationPropertySource(
                Map.of(PREFIX + ".active-user-criterion", "STATUS")
        ));

        AdminStatsProperties properties = binder.bindOrCreate(PREFIX, AdminStatsProperties.class);

        assertThat(properties.activeUserCriterion()).isEqualTo(ActiveUserCriterion.STATUS);
    }
}
