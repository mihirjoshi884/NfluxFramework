
package org.nflux.nfluxframework.framework;


import org.nflux.nfluxframework.config.NfluxConfig;
import org.nflux.nfluxframework.pojo.API;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient; // Import WebClient

import java.util.List;

@Configuration
@EnableConfigurationProperties(NfluxConfig.class) // Crucial: Enables NfluxConfig to be processed as a @ConfigurationProperties bean
public class NfluxFrameworkAutoConfiguration {

    /**
     * Defines the NfluxFrameworkHolder bean, which provides a static way to access
     * the NfluxFramework instance.
     *
     * @param nfluxFramework The NfluxFramework instance, automatically injected by Spring.
     * @return An instance of NfluxFrameworkHolder.
     */
    @Bean
    @ConditionalOnMissingBean
    public NfluxFrameworkHolder nfluxFrameworkHolder(NfluxFramework nfluxFramework) {
        NfluxFrameworkHolder holder = new NfluxFrameworkHolder();
        holder.setFramework(nfluxFramework);
        return holder;
    }

    /**
     * Defines the core NfluxFramework bean, responsible for the framework's main logic.
     * It receives the runtime-configured NfluxConfig, a WebClient.Builder, and any user-defined APIs.
     *
     * @param nfluxConfig The runtime populated NfluxConfig bean. (Injected by Spring)
     * @param webClientBuilder The WebClient.Builder bean, which should be configured (e.g., with base URL for tests).
     * @param userDefinedApis A list of user-defined API POJOs, optionally provided by the consumer. (Injected by Spring)
     * @return An instance of NfluxFramework.
     */
    @Bean
    @ConditionalOnMissingBean
    public NfluxFramework nfluxFramework(
            NfluxConfig nfluxConfig,
            WebClient.Builder webClientBuilder, // Added WebClient.Builder as a dependency
            @Autowired(required = false) List<API> userDefinedApis
    ) {
        // Log the NFlux Framework initialization with its loaded configuration.
        System.out.println("NfluxFramework initialized with config: " + nfluxConfig.toString());
        // Pass the webClientBuilder to the NfluxFramework constructor
        return new NfluxFramework(nfluxConfig, webClientBuilder, userDefinedApis);
    }
}
