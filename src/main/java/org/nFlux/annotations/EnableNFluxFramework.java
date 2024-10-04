package org.nFlux.annotations;

import org.nFlux.enums.NfluxProperty;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface EnableNFluxFramework {

    NfluxProperty OUTPUT_PREFERENCE() default NfluxProperty.NONE ;
    String configFilePath() default "";

}

