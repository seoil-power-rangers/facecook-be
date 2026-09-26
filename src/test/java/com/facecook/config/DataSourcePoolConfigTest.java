package com.facecook.config;

import com.zaxxer.hikari.HikariConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.PropertySourcesPlaceholdersResolver;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * application.yml의 DB 연결 풀 크기(#129, #131)를 스프링이 읽는 방식 그대로 {@link HikariConfig}에 바인딩해 확인한다.
 */
class DataSourcePoolConfigTest {

    @Test
    void defaultsToTenConnectionsAllKeptIdle() throws IOException {
        HikariConfig config = bind(Map.of());

        assertThat(config.getMaximumPoolSize()).isEqualTo(10);
        assertThat(config.getMinimumIdle()).isEqualTo(10);
    }

    @Test
    void environmentVariablesOverrideThePoolSize() throws IOException {
        HikariConfig config = bind(Map.of("DB_POOL_MAX_SIZE", "15", "DB_POOL_MIN_IDLE", "5"));

        assertThat(config.getMaximumPoolSize()).isEqualTo(15);
        assertThat(config.getMinimumIdle()).isEqualTo(5);
    }

    private static HikariConfig bind(Map<String, Object> environment) throws IOException {
        List<PropertySource<?>> sources = new ArrayList<>();
        sources.add(new MapPropertySource("environment", environment));
        sources.addAll(new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yml")));
        Binder binder = new Binder(
                ConfigurationPropertySources.from(sources),
                new PropertySourcesPlaceholdersResolver(sources)
        );
        return binder.bind("spring.datasource.hikari", Bindable.of(HikariConfig.class)).orElseGet(HikariConfig::new);
    }
}
