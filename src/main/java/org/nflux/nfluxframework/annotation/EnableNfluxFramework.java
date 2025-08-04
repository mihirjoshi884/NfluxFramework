package org.nflux.nfluxframework.annotation;


import org.nflux.nfluxframework.framework.NfluxFrameworkAutoConfiguration;
import org.springframework.context.annotation.Import;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;

import java.lang.annotation.Target;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Enables the Nflux Framework's auto-configuration.
 * When applied to a Spring Boot application's main class, it imports
 * NfluxFrameworkAutoConfiguration, which in turn enables @ConfigurationProperties
 * for NfluxConfig.
 */
@Retention(RUNTIME)
@Target(ElementType.TYPE)
@Import(NfluxFrameworkAutoConfiguration.class)
public @interface EnableNfluxFramework {

}
