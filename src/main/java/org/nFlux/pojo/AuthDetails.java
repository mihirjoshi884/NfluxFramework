package org.nFlux.pojo;



import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthDetails {
    private String bearerToken;
    private String basicUsername;
    private String basicPassword;
    private String clientId;
    private String clientSecret;
    private String apiKey;
    private String customHeaderName;
    private String customHeaderValue;
    private String queryParameterName;
    private String queryParameterValue;
}
