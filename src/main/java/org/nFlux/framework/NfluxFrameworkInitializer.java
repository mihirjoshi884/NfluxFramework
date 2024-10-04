package org.nFlux.framework;

import org.nFlux.annotations.EnableNFluxFramework;
import org.nFlux.config.NfluxConfig;
import org.nFlux.enums.NfluxProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

@Component
public class NfluxFrameworkInitializer {

    @Autowired
    static NfluxConfig config;

    public static void initialize(Class<?> mainClass) {
        if (mainClass.isAnnotationPresent(EnableNFluxFramework.class)) {
            EnableNFluxFramework annotation = mainClass.getAnnotation(EnableNFluxFramework.class);
            NfluxProperty outputPreference = annotation.OUTPUT_PREFERENCE();
            String configFilePath = annotation.configFilePath();

            // Set output preference directly from the annotation if provided
            if (outputPreference != NfluxProperty.NONE) {
                config.setOutputPreference(outputPreference);
            }
            // If a config file path is provided, load the property from that file
            else if (!configFilePath.isEmpty()) {
                config.setOutputPreference(loadFromPropertiesFile(configFilePath));
            }
            // Handle the case where no output preference is set
            else {
                config.setOutputPreference(NfluxProperty.NONE); // Default value
            }
            NfluxFramework framework = new NfluxFramework(config);
            NfluxFrameworkHolder.setNfluxFramework(framework);
        }
    }

    // Load properties from the given config file
    private static NfluxProperty loadFromPropertiesFile(String configFilePath) {
        Properties properties = new Properties();
        try (InputStream inputStream = new FileInputStream(configFilePath)) {
            properties.load(inputStream);
            String propertyValue = properties.getProperty("nflux.OUTPUT_PREFERENCE");
            return propertyValue != null ? NfluxProperty.valueOf(propertyValue) : NfluxProperty.NONE;
        } catch (IOException | IllegalArgumentException e) {
            throw new RuntimeException("Failed to load configuration from " + configFilePath, e);
        }
    }
}
