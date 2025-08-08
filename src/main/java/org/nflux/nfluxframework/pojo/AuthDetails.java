// File: src/main/java/org/nflux/nfluxframework/pojo/AuthDetails.java
package org.nflux.nfluxframework.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.nflux.nfluxframework.enums.AUTH_WAYS;

/**
 * Pojo representing authentication details for an API.
 * It supports various authentication methods like Bearer Token, Basic Auth, API Key, and OAuth2.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthDetails {
    private AUTH_WAYS authWays;
    private String token; // For Bearer Token and OAuth2 tokens
    private String username; // For Basic Auth
    private String password; // For Basic Auth
    private String apiKeyName; // For API Key (e.g., "X-API-KEY")
    private String apiKeyValue; // For API Key value
    private String clientId; // Potentially for OAuth2 client credentials flow
    private String clientSecret; // Potentially for OAuth2 client credentials flow


    public static AuthDetails bearerToken(String token) {
        // Corrected to pass all 8 arguments to the AllArgsConstructor
        return new AuthDetails(AUTH_WAYS.BEARER_TOKEN, token, null, null, null, null, null, null);
    }

    public static AuthDetails basicAuth(String username, String password) {
        // Corrected to pass all 8 arguments to the AllArgsConstructor
        return new AuthDetails(AUTH_WAYS.BASIC_AUTH, null, username, password, null, null, null, null);
    }

    public static AuthDetails apiKey(String apiKeyName, String apiKeyValue) {
        // Corrected to pass all 8 arguments to the AllArgsConstructor
        return new AuthDetails(AUTH_WAYS.API_KEY, null, null, null, apiKeyName, apiKeyValue, null, null);
    }

    public static AuthDetails oauth2(String token) {

        return new AuthDetails(AUTH_WAYS.OAUTH2, token, null, null, null, null, null, null);
    }

    // Optional: If you want a factory method for OAuth2 with client credentials
    public static AuthDetails oauth2ClientCredentials(String clientId, String clientSecret) {
        // This would be used if you define an API call to *get* the token using these credentials.
        // The token itself would then be used in a subsequent API call via the 'oauth2(String token)' method.
        return new AuthDetails(AUTH_WAYS.OAUTH2, null, null, null, null, null, clientId, clientSecret);
    }
}
