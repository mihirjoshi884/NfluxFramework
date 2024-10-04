package org.nFlux.config;


import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.nFlux.enums.NfluxProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;


@Component
@Getter @Setter @AllArgsConstructor @NoArgsConstructor
public class NfluxConfig {
    NfluxProperty outputPreference ;

}
