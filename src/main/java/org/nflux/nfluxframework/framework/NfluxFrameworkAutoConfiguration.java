
package org.nflux.nfluxframework.framework;

import org.nflux.nfluxframework.config.NfluxConfig;
import org.nflux.nfluxframework.pojo.API;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;

@Configuration
@EnableConfigurationProperties(NfluxConfig.class)
public class NfluxFrameworkAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public NfluxFrameworkHolder nfluxFrameworkHolder(NfluxFramework nfluxFramework) {
        NfluxFrameworkHolder holder = new NfluxFrameworkHolder();
        holder.setFramework(nfluxFramework);
        return holder;
    }


    @Bean
    @ConditionalOnMissingBean
    public NfluxFramework nfluxFramework(
            NfluxConfig nfluxConfig,
            WebClient.Builder webClientBuilder,
            @Autowired(required = false) List<API> userDefinedApis
    ) {
        System.out.println("NfluxFramework initialized with config: " + nfluxConfig.toString());
        // Pass the nfluxConfig and webClientBuilder to the NfluxFramework constructor
        return new NfluxFramework(nfluxConfig, webClientBuilder, userDefinedApis);
    }
}