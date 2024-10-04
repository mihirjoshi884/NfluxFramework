package org.nFlux.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {

    @Bean
    public static NfluxConfig nfluxConfig(){
        NfluxConfig config = new NfluxConfig();
        return config;
    }
}
