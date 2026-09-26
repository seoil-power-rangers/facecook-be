package com.facecook.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.PropertySourcesPlaceholdersResolver;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.web.server.Compression;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * application.yml이 JSON 응답 압축을 켜는지 확인한다(#125). 압축은 Tomcat이 하므로 MockMvc로는 보이지 않아서,
 * 설정값을 스프링이 읽는 방식 그대로 {@link Compression}에 바인딩해 본다.
 */
class ResponseCompressionConfigTest {

    @Test
    void applicationYamlEnablesGzipForJsonResponses() throws IOException {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                .load("application", new ClassPathResource("application.yml"));
        Binder binder = new Binder(
                ConfigurationPropertySources.from(sources),
                new PropertySourcesPlaceholdersResolver(sources)
        );

        Compression compression = binder.bind("server.compression", Bindable.of(Compression.class))
                .orElseGet(Compression::new);

        assertThat(compression.getEnabled()).isTrue();
        assertThat(compression.getMimeTypes()).contains("application/json");
    }
}
