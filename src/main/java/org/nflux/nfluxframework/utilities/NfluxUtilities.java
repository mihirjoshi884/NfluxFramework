// File: src/main/java/org/nflux/nfluxframework/utilities/NfluxUtilities.java
package org.nflux.nfluxframework.utilities;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.nflux.nfluxframework.enums.API_TYPE; // Import API_TYPE
import org.nflux.nfluxframework.enums.AUTH_WAYS;
import org.nflux.nfluxframework.pojo.API;
import org.nflux.nfluxframework.pojo.AuthDetails;
import org.nflux.nfluxframework.pojo.InputMappingDetail; // Import InputMappingDetail
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod; // IMPORTED
import org.springframework.http.HttpStatus; // IMPORTED
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List; // For setValueInMap helper
import java.util.function.Predicate; // For onStatus lambda

/**
 * Utility class for executing API calls using Spring WebClient.
 * It handles different HTTP methods, authentication types, and now,
 * dynamic input mapping from previous API results.
 */
public class NfluxUtilities {

    private static final Logger LOGGER = Logger.getLogger(NfluxUtilities.class.getName());
    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;

    public NfluxUtilities(WebClient.Builder webClientBuilder) {
        this.webClientBuilder = webClientBuilder;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Executes an API call based on the provided API definition.
     * It dynamically applies input mappings from previous API results
     * to construct the current API's request.
     *
     * @param api The API definition to execute.
     * @param previousResults A map of previously executed API IDs to their JsonNode responses.
     * This is used to resolve input mappings for dependent APIs.
     * @return A Mono emitting the JsonNode response of the executed API.
     */
    public Mono<JsonNode> executeApi(API api, Map<String, JsonNode> previousResults) {
        // Create mutable copies of request details to apply dynamic mappings
        String currentUrl = api.getUrl();
        Map<String, String> currentHeaders = api.getHeaders() != null ? new HashMap<>(api.getHeaders()) : new HashMap<>();
        Map<String, Object> currentQueryParams = api.getQueryParams() != null ? new HashMap<>(api.getQueryParams()) : new HashMap<>();
        Map<String, Object> currentRequestBody = api.getRequestBody() != null ? new HashMap<>(api.getRequestBody()) : new HashMap<>();

        // 1. Apply Input Mappings (only for DEPENDENT APIs that have mappings)
        if (api.getType() == API_TYPE.DEPENDENT && api.getInputMappings() != null && !api.getInputMappings().isEmpty()) {
            for (Map.Entry<String, InputMappingDetail> entry : api.getInputMappings().entrySet()) {
                InputMappingDetail mapping = entry.getValue();
                try {
                    // Get the source API's result
                    JsonNode sourceResult = previousResults.get(mapping.getSourceApiId());
                    if (sourceResult == null) {
                        throw new IllegalStateException("Source API result not found for mapping: " + mapping.getSourceApiId() + " for API: " + api.getId());
                    }

                    // Extract the value using JSONPath-like traversal
                    Object extractedValue = extractValueFromJsonNode(sourceResult, mapping.getSourceJsonPath());
                    if (extractedValue == null) {
                        LOGGER.log(Level.WARNING, "Extracted null value for mapping '{0}' from API '{1}' using path '{2}'. Skipping.",
                                new Object[]{entry.getKey(), mapping.getSourceApiId(), mapping.getSourceJsonPath()});
                        continue; // Skip this mapping if value is null
                    }

                    // Apply the extracted value to the target
                    switch (mapping.getTargetType()) {
                        case PATH_VARIABLE:
                            // Replace {targetField} in URL with extractedValue
                            currentUrl = currentUrl.replace("{" + mapping.getTargetField() + "}",
                                    URLEncoder.encode(String.valueOf(extractedValue), StandardCharsets.UTF_8));
                            break;
                        case QUERY_PARAM:
                            currentQueryParams.put(mapping.getTargetField(), extractedValue);
                            break;
                        case REQUEST_BODY_FIELD:
                            // Set the value in the request body map at the specified JSONPath
                            setValueInMap(currentRequestBody, mapping.getTargetField(), extractedValue);
                            break;
                        case HEADER:
                            currentHeaders.put(mapping.getTargetField(), String.valueOf(extractedValue));
                            break;
                        default:
                            LOGGER.log(Level.WARNING, "Unsupported InputTargetType: {0}", mapping.getTargetType());
                            break;
                    }
                } catch (Exception e) {
                    LOGGER.log(Level.SEVERE, "Failed to apply input mapping '{0}' for API '{1}': {2}",
                            new Object[]{entry.getKey(), api.getId(), e.getMessage()});
                    return Mono.error(new RuntimeException("Failed to apply input mapping for " + api.getId(), e));
                }
            }
        }

        // 2. Build WebClient Request
        WebClient webClient = webClientBuilder.build(); // Build WebClient once

        // Use a final variable for currentUrl in lambda
        String finalCurrentUrl = currentUrl;

        // Start building the request.
        WebClient.RequestHeadersSpec<?> requestSpec;

        // Corrected WebClient builder chain:
        if (api.getMethod() == HttpMethod.POST || api.getMethod() == HttpMethod.PUT || api.getMethod() == HttpMethod.PATCH) {
            requestSpec = webClient.method(api.getMethod())
                    .uri(uriBuilder -> {
                        uriBuilder.path(finalCurrentUrl);
                        currentQueryParams.forEach((key, value) -> uriBuilder.queryParam(key, value));
                        return uriBuilder.build();
                    })
                    .headers(httpHeaders -> currentHeaders.forEach(httpHeaders::add))
                    .body(BodyInserters.fromValue(currentRequestBody)); // Apply body here
        } else {
            requestSpec = webClient.method(api.getMethod())
                    .uri(uriBuilder -> {
                        uriBuilder.path(finalCurrentUrl);
                        currentQueryParams.forEach((key, value) -> uriBuilder.queryParam(key, value));
                        return uriBuilder.build();
                    })
                    .headers(httpHeaders -> currentHeaders.forEach(httpHeaders::add)); // Apply headers here
        }

        // Apply authentication (this needs to be on RequestHeadersSpec, which requestSpec is)
        applyAuthentication(requestSpec, api.getAuthDetails());

        LOGGER.log(Level.INFO, "Preparing to execute API: {0} - {1} ({2})",
                new Object[]{api.getId(), currentUrl, api.getMethod()});

        // 3. Execute Request and Handle Response
        return requestSpec.retrieve()
                .onStatus(status -> status.isError(), response -> {
                    // Log the error response details
                    return response.bodyToMono(String.class)
                            .flatMap(errorBody -> {
                                LOGGER.log(Level.SEVERE, "Error executing API {0}: Status {1} {2}. Body: {3}",
                                        new Object[]{api.getId(), response.statusCode().value(), response.statusCode().toString(), errorBody});
                                return Mono.error(new WebClientResponseException(
                                        response.statusCode().value(),
                                        response.statusCode().toString(),
                                        response.headers().asHttpHeaders(),
                                        errorBody.getBytes(),
                                        null));
                            });
                })
                .bodyToMono(JsonNode.class)
                .doOnSuccess(responseJson -> LOGGER.log(Level.INFO, "Successfully executed API: {0}. Response: {1}",
                        new Object[]{api.getId(), responseJson.toPrettyString()}))
                .onErrorResume(WebClientResponseException.class, ex -> {
                    LOGGER.log(Level.SEVERE, "Reactive error handling for API {0}: {1}",
                            new Object[]{api.getId(), ex.getMessage()});
                    return Mono.error(new RuntimeException("API execution failed for " + api.getId() + ": " + ex.getMessage(), ex));
                })
                .onErrorResume(Exception.class, ex -> {
                    LOGGER.log(Level.SEVERE, "Generic error during API execution for {0}: {1}",
                            new Object[]{api.getId(), ex.getMessage()});
                    return Mono.error(new RuntimeException("API execution failed for " + api.getId() + ": " + ex.getMessage(), ex));
                });
    }

    /**
     * Applies authentication details to the WebClient request.
     *
     * @param requestHeadersSpec The WebClient.RequestHeadersSpec to apply authentication to.
     * @param authDetails The authentication details.
     */
    private void applyAuthentication(WebClient.RequestHeadersSpec<?> requestHeadersSpec, AuthDetails authDetails) {
        if (authDetails == null) {
            return;
        }

        // The parameter 'requestHeadersSpec' is already of the correct type, no cast needed.
        if (authDetails.getAuthWays() == AUTH_WAYS.BEARER_TOKEN && authDetails.getToken() != null) {
            requestHeadersSpec.header(HttpHeaders.AUTHORIZATION, "Bearer " + authDetails.getToken());
            LOGGER.log(Level.INFO, "Applied Bearer Token authentication.");
        } else if (authDetails.getAuthWays() == AUTH_WAYS.BASIC_AUTH && authDetails.getUsername() != null && authDetails.getPassword() != null) {
            String auth = authDetails.getUsername() + ":" + authDetails.getPassword();
            String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
            requestHeadersSpec.header(HttpHeaders.AUTHORIZATION, "Basic " + encodedAuth);
            LOGGER.log(Level.INFO, "Applied Basic Authentication.");
        } else if (authDetails.getAuthWays() == AUTH_WAYS.API_KEY && authDetails.getApiKeyName() != null && authDetails.getApiKeyValue() != null) {
            // Apply API Key as a header
            requestHeadersSpec.header(authDetails.getApiKeyName(), authDetails.getApiKeyValue());
            LOGGER.log(Level.INFO, "Applied API Key authentication using header '{0}'.", authDetails.getApiKeyName());
        } else if (authDetails.getAuthWays() == AUTH_WAYS.OAUTH2 && authDetails.getToken() != null) {
            // For simplicity, assuming OAUTH2 here means applying a Bearer token
            // from the existing 'token' field in AuthDetails.
            // A more complex OAuth flow (e.g., client credentials) would involve
            // an earlier API call to get this token.
            requestHeadersSpec.header(HttpHeaders.AUTHORIZATION, "Bearer " + authDetails.getToken());
            LOGGER.log(Level.INFO, "Applied OAUTH2 authentication (as Bearer Token).");
        }
        // No need to handle clientId/clientSecret directly here, as they are used to *obtain* the token,
        // which is then stored in 'token' and used in the OAUTH2 case above.
    }

    /**
     * Extracts a value from a JsonNode using a simple JSONPath-like string.
     * Supports dot notation for object fields (e.g., "$.field", "$.nested.field")
     * and array indexing (e.g., "$.array[0]", "$.array[1].field").
     *
     * @param jsonNode The JsonNode to extract from.
     * @param jsonPath The JSONPath-like string (e.g., "$.orderId", "$.data[0].id").
     * @return The extracted value as an Object (String, Number, Boolean, or JsonNode for objects/arrays), or null if not found.
     */
    private Object extractValueFromJsonNode(JsonNode jsonNode, String jsonPath) {
        if (jsonNode == null || jsonPath == null || jsonPath.isEmpty()) {
            return null;
        }

        // Remove leading "$.", if present
        String path = jsonPath.startsWith("$.") ? jsonPath.substring(2) : jsonPath;
        String[] parts = path.split("\\.");

        JsonNode currentNode = jsonNode;
        for (String part : parts) {
            if (currentNode == null) {
                return null;
            }

            if (part.contains("[")) { // Handle array indexing (e.g., "array[0]")
                String arrayName = part.substring(0, part.indexOf("["));
                int index = Integer.parseInt(part.substring(part.indexOf("[") + 1, part.indexOf("]")));

                if (currentNode.has(arrayName) && currentNode.get(arrayName).isArray()) {
                    ArrayNode arrayNode = (ArrayNode) currentNode.get(arrayName);
                    if (index >= 0 && index < arrayNode.size()) {
                        currentNode = arrayNode.get(index);
                    } else {
                        return null; // Index out of bounds
                    }
                } else {
                    return null; // Not an array or array field not found
                }
            } else { // Handle object fields
                if (currentNode.isObject() && currentNode.has(part)) {
                    currentNode = currentNode.get(part);
                } else {
                    return null; // Field not found or not an object
                }
            }
        }

        // Return the value based on its type
        if (currentNode == null || currentNode.isNull()) {
            return null;
        } else if (currentNode.isTextual()) {
            return currentNode.asText();
        } else if (currentNode.isNumber()) {
            return currentNode.numberValue();
        } else if (currentNode.isBoolean()) {
            return currentNode.booleanValue();
        } else {
            return currentNode; // Return the JsonNode itself for objects or arrays
        }
    }

    /**
     * Sets a value in a nested Map<String, Object> (representing a JSON object) at a given JSONPath-like string.
     * Creates nested maps/lists as needed.
     * Supports dot notation for object fields (e.g., "field", "nested.field")
     * and array indexing (e.g., "array[0]", "array[1].field").
     *
     * @param map The map to modify.
     * @param jsonPath The JSONPath-like string (e.g., "$.newField", "$.items[0].productId").
     * @param value The value to set.
     */
    private void setValueInMap(Map<String, Object> map, String jsonPath, Object value) {
        if (map == null || jsonPath == null || jsonPath.isEmpty()) {
            return;
        }

        // Remove leading "$.", if present
        String path = jsonPath.startsWith("$.") ? jsonPath.substring(2) : jsonPath;
        String[] parts = path.split("\\.");

        Map<String, Object> currentMap = map;
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];

            if (part.contains("[")) { // Handle array indexing
                String arrayName = part.substring(0, part.indexOf("["));
                int index = Integer.parseInt(part.substring(part.indexOf("[") + 1, part.indexOf("]")));

                Object arrayObj = currentMap.get(arrayName);
                if (!(arrayObj instanceof List)) {
                    arrayObj = new java.util.ArrayList<>(); // Create new list if not exists
                    currentMap.put(arrayName, arrayObj);
                }
                List<Object> currentList = (List<Object>) arrayObj;

                // Ensure list has enough elements
                while (currentList.size() <= index) {
                    currentList.add(new HashMap<>()); // Add empty objects for nested maps
                }

                if (i == parts.length - 1) { // Last part of the path, set the value directly
                    currentList.set(index, value);
                } else { // Not the last part, continue into nested object/map within the array element
                    Object nextLevel = currentList.get(index);
                    if (!(nextLevel instanceof Map)) {
                        nextLevel = new HashMap<>(); // Convert to map if not already
                        currentList.set(index, nextLevel);
                    }
                    currentMap = (Map<String, Object>) nextLevel;
                }
            } else { // Handle object fields
                if (i == parts.length - 1) { // Last part of the path, set the value directly
                    currentMap.put(part, value);
                } else { // Not the last part, navigate to nested map
                    Object nextLevel = currentMap.get(part);
                    if (!(nextLevel instanceof Map)) {
                        nextLevel = new HashMap<>(); // Create new map if not exists
                        currentMap.put(part, nextLevel);
                    }
                    currentMap = (Map<String, Object>) nextLevel;
                }
            }
        }
    }
}
