package com.dealstracker.config;

import com.dealstracker.deals.DealDetector;
import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class AppConfig {

    @Bean
    public Clock clock(TrackerProperties props) {
        return Clock.system(ZoneId.of(props.timezone()));
    }

    @Bean
    public DealDetector dealDetector(TrackerProperties props) {
        return new DealDetector(props.deals().baselineDays(), props.deals().longBaselineDays());
    }

    @Bean
    public WebMvcConfigurer corsConfigurer(TrackerProperties props) {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/**")
                        .allowedOrigins(props.corsAllowedOrigins().toArray(String[]::new))
                        .allowedMethods("GET", "POST", "DELETE");
            }
        };
    }
}
