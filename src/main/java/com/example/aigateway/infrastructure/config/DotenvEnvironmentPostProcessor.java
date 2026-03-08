package com.example.aigateway.infrastructure.config;

import io.github.cdimascio.dotenv.Dotenv;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.util.StringUtils;

public class DotenvEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Dotenv dotenv = Dotenv.configure()
                .ignoreIfMalformed()
                .ignoreIfMissing()
                .load();

        Map<String, Object> values = new LinkedHashMap<>();
        dotenv.entries().forEach(entry -> {
            if (!StringUtils.hasText(environment.getProperty(entry.getKey()))) {
                values.put(entry.getKey(), entry.getValue());
            }
        });

        if (!values.isEmpty()) {
            environment.getPropertySources().addFirst(new MapPropertySource("dotenvProperties", values));
        }
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
