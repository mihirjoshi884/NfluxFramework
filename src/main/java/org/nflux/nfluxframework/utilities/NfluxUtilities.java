
package org.nflux.nfluxframework.utilities;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.nflux.nfluxframework.config.NfluxConfig;
import org.nflux.nfluxframework.enums.API_TYPE;
import org.nflux.nfluxframework.enums.AUTH_WAYS;
import org.nflux.nfluxframework.enums.config.OutputFormat;
import org.nflux.nfluxframework.pojo.API;
import org.nflux.nfluxframework.pojo.AuthDetails;
import org.nflux.nfluxframework.pojo.InputMappingDetail;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Utility class for executing API calls using Spring WebClient.
 * It now uses NfluxConfig for runtime configuration.
 */
public class NfluxUtilities {

    private static final Logger LOGGER = Logger.getLogger(NfluxUtilities.class.getName());
    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;
    private final NfluxConfig nfluxConfig; // Add NfluxConfig field

    /**
     * Constructor for NfluxUtilities.
     *
     * @param webClientBuilder The WebClient.Builder to use.
     * @param nfluxConfig The NfluxConfig object for runtime settings.
     */
    public NfluxUtilities(WebClient.Builder webClientBuilder, NfluxConfig nfluxConfig) {
        this.webClientBuilder = webClientBuilder;
        this.objectMapper = new ObjectMapper();
        this.nfluxConfig = nfluxConfig;
    }

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
                        if (nfluxConfig.isLoggingEnabled()) { // Use nfluxConfig for logging
                            LOGGER.log(Level.WARNING, "Extracted null value for mapping '{0}' from API '{1}' using path '{2}'. Skipping.",
                                    new Object[]{entry.getKey(), mapping.getSourceApiId(), mapping.getSourceJsonPath()});
                        }
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
                        case AUTH_DETAIL_FIELD: // NEW: Handle mapping to AuthDetails fields
                            if (api.getAuthDetails() == null) {
                                // If AuthDetails is null, create a new one.
                                // We assume it's a bearer token if 'token' is targeted.
                                if ("token".equals(mapping.getTargetField())) {
                                    api.setAuthDetails(new AuthDetails(AUTH_WAYS.BEARER_TOKEN, String.valueOf(extractedValue), null, null, null, null, null, null));
                                } else {
                                    if (nfluxConfig.isErrorLoggingEnabled()) { // Use nfluxConfig for error logging
                                        LOGGER.log(Level.WARNING, "AuthDetails is null for API '{0}'. Cannot set field '{1}'.",
                                                new Object[]{api.getId(), mapping.getTargetField()});
                                    }
                                }
                            } else {
                                // Set the specific field in the existing AuthDetails object
                                if ("token".equals(mapping.getTargetField())) {
                                    api.getAuthDetails().setToken(String.valueOf(extractedValue));
                                }
                                // Add more else if for other AuthDetails fields if needed (e.g., username, password)
                                else {
                                    if (nfluxConfig.isErrorLoggingEnabled()) { // Use nfluxConfig for error logging
                                        LOGGER.log(Level.WARNING, "Unsupported AuthDetails field for mapping: '{0}' for API '{1}'.",
                                                new Object[]{mapping.getTargetField(), api.getId()});
                                    }
                                }
                            }
                            break;
                        default:
                            if (nfluxConfig.isErrorLoggingEnabled()) { // Use nfluxConfig for error logging
                                LOGGER.log(Level.WARNING, "Unsupported InputTargetType: {0}", mapping.getTargetType());
                            }
                            break;
                    }
                } catch (Exception e) {
                    if (nfluxConfig.isErrorLoggingEnabled()) { // Use nfluxConfig for error logging
                        LOGGER.log(Level.SEVERE, "Failed to apply input mapping '{0}' for API '{1}': {2}",
                                new Object[]{entry.getKey(), api.getId(), e.getMessage()});
                    }
                    return Mono.error(new RuntimeException("Failed to apply input mapping for " + api.getId(), e));
                }
            }
        }

        WebClient webClient;
        WebClient.RequestHeadersSpec<?> requestSpec = null;

        try {
            URI fullUri = new URI(currentUrl);

            // Check if the URI is absolute (contains a scheme like http/https).
            if (fullUri.isAbsolute()) {
                // Case 1: Absolute URI (e.g., http://localhost:8080/path)
                // Rebuild the base URL from the scheme and authority (host:port).
                String baseUrl = fullUri.getScheme() + "://" + fullUri.getAuthority();

                // Create a new WebClient instance with the resolved base URL.
                webClient = webClientBuilder.baseUrl(baseUrl).build();

                // The path is now a relative segment.
                String finalPath = fullUri.getPath();

                // Build the URI string with path and query parameters
                UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromUriString(finalPath);
                for (Map.Entry<String, Object> entry : currentQueryParams.entrySet()) {
                    uriBuilder.queryParam(entry.getKey(), entry.getValue());
                }
                String finalUri = uriBuilder.build().toUriString();

                if (api.getMethod() == HttpMethod.POST || api.getMethod() == HttpMethod.PUT || api.getMethod() == HttpMethod.PATCH) {
                    requestSpec = webClient.method(api.getMethod())
                            .uri(finalUri)
                            .headers(httpHeaders -> currentHeaders.forEach(httpHeaders::add))
                            .body(BodyInserters.fromValue(currentRequestBody));
                } else {
                    requestSpec = webClient.method(api.getMethod())
                            .uri(finalUri)
                            .headers(httpHeaders -> currentHeaders.forEach(httpHeaders::add));
                }
            } else {
                // Case 2: Relative URI (e.g., /path)
                // Use the original builder and provide the full path to the uri() method.
                webClient = webClientBuilder.build();

                // Build the URI string with path and query parameters
                UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromUriString(currentUrl);
                for (Map.Entry<String, Object> entry : currentQueryParams.entrySet()) {
                    uriBuilder.queryParam(entry.getKey(), entry.getValue());
                }
                String finalUri = uriBuilder.build().toUriString();

                if (api.getMethod() == HttpMethod.POST || api.getMethod() == HttpMethod.PUT || api.getMethod() == HttpMethod.PATCH) {
                    requestSpec = webClient.method(api.getMethod())
                            .uri(finalUri)
                            .headers(httpHeaders -> currentHeaders.forEach(httpHeaders::add))
                            .body(BodyInserters.fromValue(currentRequestBody));
                } else {
                    requestSpec = webClient.method(api.getMethod())
                            .uri(finalUri)
                            .headers(httpHeaders -> currentHeaders.forEach(httpHeaders::add));
                }
            }
        } catch (URISyntaxException e) {
            // Handle the case where the URL is malformed.
            // Log the error and return a Mono.error to fail the stream gracefully.
            if (nfluxConfig.isErrorLoggingEnabled()) {
                LOGGER.log(Level.SEVERE, "Invalid URL syntax for API '{0}': {1}",
                        new Object[]{api.getId(), e.getMessage()});
            }
            return Mono.error(new RuntimeException("Invalid URL syntax for " + api.getId(), e));
        }
        applyAuthentication(requestSpec, api.getAuthDetails());

        if (nfluxConfig.isLoggingEnabled()) {
            LOGGER.log(Level.INFO, "Preparing to execute API: {0} - {1} ({2})",
                    new Object[]{api.getId(), currentUrl, api.getMethod()});
        }

        // Inside the executeApi method of NfluxUtilities.java
        return requestSpec.retrieve()
                .onStatus(status -> status.isError(), response ->
                        response.bodyToMono(String.class)
                                .defaultIfEmpty("") // Handle cases where error body is empty
                                .flatMap(errorBody -> {
                                    if (nfluxConfig.isErrorLoggingEnabled()) {
                                        LOGGER.log(Level.SEVERE, "Error executing API {0}: Status {1}. Body: {2}",
                                                new Object[]{api.getId(), response.statusCode(), errorBody});
                                    }
                                    // Always return a Mono.error() to propagate the failure signal
                                    return Mono.error(new WebClientResponseException(
                                            response.statusCode().value(),
                                            response.statusCode().toString(),
                                            response.headers().asHttpHeaders(),
                                            errorBody.getBytes(),
                                            StandardCharsets.UTF_8
                                    ));
                                }))
                .bodyToMono(String.class)
                .map(rawResponse -> {
                    // ... [rest of the success path logic] ...
                    return processResponse(rawResponse, api.getId());
                })
                // The onErrorResume is now simplified as the onStatus block does the heavy lifting
                .onErrorResume(WebClientResponseException.class, ex -> {
                    if (nfluxConfig.isErrorLoggingEnabled()) {
                        LOGGER.log(Level.SEVERE, "Reactive error handling for API {0}: {1}",
                                new Object[]{api.getId(), ex.getMessage()});
                    }
                    return Mono.error(new RuntimeException("API execution failed for " + api.getId(), ex));
                })
                .onErrorResume(Exception.class, ex -> {

                    return Mono.error(new RuntimeException("API execution failed for " + api.getId(), ex));
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

        if (authDetails.getAuthWays() == AUTH_WAYS.BEARER_TOKEN && authDetails.getToken() != null) {
            requestHeadersSpec.header(HttpHeaders.AUTHORIZATION, "Bearer " + authDetails.getToken());
            if (nfluxConfig.isLoggingEnabled()) {
                LOGGER.log(Level.INFO, "Applied Bearer Token authentication.");
            }
        } else if (authDetails.getAuthWays() == AUTH_WAYS.BASIC_AUTH && authDetails.getUsername() != null && authDetails.getPassword() != null) {
            String auth = authDetails.getUsername() + ":" + authDetails.getPassword();
            String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
            requestHeadersSpec.header(HttpHeaders.AUTHORIZATION, "Basic " + encodedAuth);
            if (nfluxConfig.isLoggingEnabled()) {
                LOGGER.log(Level.INFO, "Applied Basic Authentication.");
            }
        } else if (authDetails.getAuthWays() == AUTH_WAYS.API_KEY && authDetails.getApiKeyName() != null && authDetails.getApiKeyValue() != null) {
            requestHeadersSpec.header(authDetails.getApiKeyName(), authDetails.getApiKeyValue());
            if (nfluxConfig.isLoggingEnabled()) {
                LOGGER.log(Level.INFO, "Applied API Key authentication using header '{0}'.", authDetails.getApiKeyName());
            }
        } else if (authDetails.getAuthWays() == AUTH_WAYS.OAUTH2 && authDetails.getToken() != null) {
            requestHeadersSpec.header(HttpHeaders.AUTHORIZATION, "Bearer " + authDetails.getToken());
            if (nfluxConfig.isLoggingEnabled()) {
                LOGGER.log(Level.INFO, "Applied OAUTH2 authentication (as Bearer Token).");
            }
        }
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

        String path = jsonPath.startsWith("$.") ? jsonPath.substring(2) : jsonPath;
        String[] parts = path.split("\\.");

        JsonNode currentNode = jsonNode;
        for (String part : parts) {
            if (currentNode == null) {
                return null;
            }

            if (part.contains("[")) {
                String arrayName = part.substring(0, part.indexOf("["));
                int index = Integer.parseInt(part.substring(part.indexOf("[") + 1, part.indexOf("]")));

                if (currentNode.has(arrayName) && currentNode.get(arrayName).isArray()) {
                    ArrayNode arrayNode = (ArrayNode) currentNode.get(arrayName);
                    if (index >= 0 && index < arrayNode.size()) {
                        currentNode = arrayNode.get(index);
                    } else {
                        return null;
                    }
                } else {
                    return null;
                }
            } else {
                if (currentNode.isObject() && currentNode.has(part)) {
                    currentNode = currentNode.get(part);
                } else {
                    return null;
                }
            }
        }

        if (currentNode == null || currentNode.isNull()) {
            return null;
        } else if (currentNode.isTextual()) {
            return currentNode.asText();
        } else if (currentNode.isNumber()) {
            return currentNode.numberValue();
        } else if (currentNode.isBoolean()) {
            return currentNode.booleanValue();
        } else {
            return currentNode;
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

        String path = jsonPath.startsWith("$.") ? jsonPath.substring(2) : jsonPath;
        String[] parts = path.split("\\.");

        Map<String, Object> currentMap = map;
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];

            if (part.contains("[")) {
                String arrayName = part.substring(0, part.indexOf("["));
                int index = Integer.parseInt(part.substring(part.indexOf("[") + 1, part.indexOf("]")));

                Object arrayObj = currentMap.get(arrayName);
                if (!(arrayObj instanceof List)) {
                    arrayObj = new java.util.ArrayList<>();
                    currentMap.put(arrayName, arrayObj);
                }
                List<Object> currentList = (List<Object>) arrayObj;

                while (currentList.size() <= index) {
                    currentList.add(new HashMap<>());
                }

                if (i == parts.length - 1) {
                    currentList.set(index, value);
                } else {
                    Object nextLevel = currentList.get(index);
                    if (!(nextLevel instanceof Map)) {
                        nextLevel = new HashMap<>();
                        currentList.set(index, nextLevel);
                    }
                    currentMap = (Map<String, Object>) nextLevel;
                }
            } else {
                if (i == parts.length - 1) {
                    currentMap.put(part, value);
                } else {
                    Object nextLevel = currentMap.get(part);
                    if (!(nextLevel instanceof Map)) {
                        nextLevel = new HashMap<>();
                        currentMap.put(part, nextLevel);
                    }
                    currentMap = (Map<String, Object>) nextLevel;
                }
            }
        }
    }

    /**
     * Processes the raw API response string into a JsonNode based on the configured output format.
     *
     * @param rawResponse The raw response as a String.
     * @param apiId The ID of the API being processed.
     * @return A JsonNode representing the processed response.
     */
    private JsonNode processResponse(String rawResponse, String apiId) {
        OutputFormat format = nfluxConfig.getOutputFormat();
        try {
            switch (format) {
                case JSON_OBJECT:
                    return objectMapper.readTree(rawResponse);
                case FLAT_STRING:
                    ObjectNode objectNode = objectMapper.createObjectNode();
                    objectNode.put("response", rawResponse);
                    return objectNode;
                case XML_DOCUMENT:
                    ObjectNode xmlNode = objectMapper.createObjectNode();
                    xmlNode.put("response", "XML_CONTENT: " + rawResponse);
                    return xmlNode;
                default:
                    if (nfluxConfig.isErrorLoggingEnabled()) {
                        LOGGER.log(Level.SEVERE, "Unsupported output format: {0} for API {1}", new Object[]{format, apiId});
                    }
                    return objectMapper.createObjectNode().put("error", "Unsupported output format: " + format);
            }
        } catch (Exception e) {
            if (nfluxConfig.isErrorLoggingEnabled()) {
                LOGGER.log(Level.SEVERE, "Failed to process response for API {0} with format {1}. Error: {2}",
                        new Object[]{apiId, format, e.getMessage()});
            }
            return objectMapper.createObjectNode().put("error", "Failed to process response");
        }
    }
}
