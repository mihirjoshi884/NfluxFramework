package org.nFlux.pojo;

import lombok.*;
import org.nFlux.enums.API_Type;
import org.nFlux.enums.AUTHWAYS;
import org.nFlux.enums.HTTP_METHOD;
import org.nFlux.enums.RESPONSE;

import java.util.HashMap;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@Builder
public class API {

    private String url;
    private API_Type api_type;
    private Integer dependencyIndex;
    private HTTP_METHOD method;
    private HashMap<AUTHWAYS,AuthDetails> auth;
    private RESPONSE responseWay;
    private String responseField;


}
