package org.nflux.nfluxframework.config;


import lombok.Getter;
import lombok.Setter;

import org.nflux.nfluxframework.enums.config.ErrorMode;
import org.nflux.nfluxframework.enums.config.LogOutput;
import org.nflux.nfluxframework.enums.config.OutputFormat;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * NfluxConfig bean that binds properties from application.properties
 * with the prefix "nflux".
 * These values are loaded at runtime by Spring Boot.
 */
@ConfigurationProperties(prefix = "nflux") // Specify the prefix for properties
@Getter
@Setter
public class NfluxConfig {
    // Provide default values which will be overridden by application.properties
    private OutputFormat outputFormat = OutputFormat.JSON_OBJECT;
    private ErrorMode errorMode = ErrorMode.FAIL_FAST;
    private LogOutput logOutput = LogOutput.CONSOLE;
    private boolean loggingEnabled = true;
    private boolean errorLoggingEnabled = true;
    private long timeout = 30000L; // milliseconds

    @Override
    public String toString() {
        return "NfluxConfig{" +
                "outputFormat=" + outputFormat +
                ", errorMode=" + errorMode +
                ", logOutput=" + logOutput +
                ", loggingEnabled=" + loggingEnabled +
                ", errorLoggingEnabled=" + errorLoggingEnabled +
                ", timeout=" + timeout +
                '}';
    }
}
