
package org.nflux.nfluxframework.utilities;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.nflux.nfluxframework.config.NfluxConfig; // Import NfluxConfig
import org.nflux.nfluxframework.enums.API_TYPE;
import org.nflux.nfluxframework.enums.AUTH_WAYS;
import org.nflux.nfluxframework.enums.config.OutputFormat; // Import OutputFormat
import org.nflux.nfluxframework.pojo.API;
import org.nflux.nfluxframework.pojo.AuthDetails;
import org.nflux.nfluxframework.pojo.InputMappingDetail;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
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

    /**
     * Executes an API call based on the provided API definition.
     * It dynamically applies input mappings from previous API results
     * and uses the NfluxConfig to control logging and output format.
     *
     * @param api The API definition to execute.
     * @param previousResults A map of previously executed API IDs to their JsonNode responses.
     * @return A Mono emitting the JsonNode response of the executed API.
     */
    public Mono<JsonNode> executeApi(API api, Map<String, JsonNode> previousResults) {
        String currentUrl = api.getUrl();
        Map<String, String> currentHeaders = api.getHeaders() != null ? new HashMap<>(api.getHeaders()) : new HashMap<>();
        Map<String, Object> currentQueryParams = api.getQueryParams() != null ? new HashMap<>(api.getQueryParams()) : new HashMap<>();
        Map<String, Object> currentRequestBody = api.getRequestBody() != null ? new HashMap<>(api.getRequestBody()) : new HashMap<>();

        if (api.getType() == API_TYPE.DEPENDENT && api.getInputMappings() != null && !api.getInputMappings().isEmpty()) {
            for (Map.Entry<String, InputMappingDetail> entry : api.getInputMappings().entrySet()) {
                InputMappingDetail mapping = entry.getValue();
                try {
                    JsonNode sourceResult = previousResults.get(mapping.getSourceApiId());
                    if (sourceResult == null) {
                        throw new IllegalStateException("Source API result not found for mapping: " + mapping.getSourceApiId() + " for API: " + api.getId());
                    }
                    Object extractedValue = extractValueFromJsonNode(sourceResult, mapping.getSourceJsonPath());
                    if (extractedValue == null) {
                        if (nfluxConfig.isLoggingEnabled()) {
                            LOGGER.log(Level.WARNING, "Extracted null value for mapping '{0}' from API '{1}' using path '{2}'. Skipping.",
                                    new Object[]{entry.getKey(), mapping.getSourceApiId(), mapping.getSourceJsonPath()});
                        }
                        continue;
                    }
                    switch (mapping.getTargetType()) {
                        case PATH_VARIABLE:
                            currentUrl = currentUrl.replace("{" + mapping.getTargetField() + "}",
                                    URLEncoder.encode(String.valueOf(extractedValue), StandardCharsets.UTF_8));
                            break;
                        case QUERY_PARAM:
                            currentQueryParams.put(mapping.getTargetField(), extractedValue);
                            break;
                        case REQUEST_BODY_FIELD:
                            setValueInMap(currentRequestBody, mapping.getTargetField(), extractedValue);
                            break;
                        case HEADER:
                            currentHeaders.put(mapping.getTargetField(), String.valueOf(extractedValue));
                            break;
                        default:
                            if (nfluxConfig.isLoggingEnabled()) {
                                LOGGER.log(Level.WARNING, "Unsupported InputTargetType: {0}", mapping.getTargetType());
                            }
                            break;
                    }
                } catch (Exception e) {
                    if (nfluxConfig.isErrorLoggingEnabled()) {
                        LOGGER.log(Level.SEVERE, "Failed to apply input mapping '{0}' for API '{1}': {2}",
                                new Object[]{entry.getKey(), api.getId(), e.getMessage()});
                    }
                    return Mono.error(new RuntimeException("Failed to apply input mapping for " + api.getId(), e));
                }
            }
        }

        WebClient webClient = webClientBuilder.build();
        String finalCurrentUrl = currentUrl;
        WebClient.RequestHeadersSpec<?> requestSpec;

        if (api.getMethod() == HttpMethod.POST || api.getMethod() == HttpMethod.PUT || api.getMethod() == HttpMethod.PATCH) {
            requestSpec = webClient.method(api.getMethod())
                    .uri(uriBuilder -> {
                        uriBuilder.path(finalCurrentUrl);
                        currentQueryParams.forEach((key, value) -> uriBuilder.queryParam(key, value));
                        return uriBuilder.build();
                    })
                    .headers(httpHeaders -> currentHeaders.forEach(httpHeaders::add))
                    .body(BodyInserters.fromValue(currentRequestBody));
        } else {
            requestSpec = webClient.method(api.getMethod())
                    .uri(uriBuilder -> {
                        uriBuilder.path(finalCurrentUrl);
                        currentQueryParams.forEach((key, value) -> uriBuilder.queryParam(key, value));
                        return uriBuilder.build();
                    })
                    .headers(httpHeaders -> currentHeaders.forEach(httpHeaders::add));
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
                    // ... [rest of the generic error handling] ...
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
