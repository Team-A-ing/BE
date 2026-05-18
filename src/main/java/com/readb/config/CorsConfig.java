package com.readb.config;

import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.List;

@Configuration
public class CorsConfig {

    private final Environment environment;

    public CorsConfig(Environment environment) {
        this.environment = environment;
    }

    @Bean
    public CorsFilter corsFilter() {
        List<String> allowedOrigins = Binder.get(environment)
                .bind("cors.allowed-origins", Bindable.listOf(String.class))
                .orElse(List.of("http://localhost:5173"));

        CorsConfiguration config = new CorsConfiguration();
        boolean isWildcard = allowedOrigins.size() == 1 && "*".equals(allowedOrigins.get(0));
        config.setAllowCredentials(!isWildcard);
        config.setAllowedOriginPatterns(allowedOrigins);
        config.setAllowedHeaders(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        config.setExposedHeaders(List.of("Authorization"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }
}
