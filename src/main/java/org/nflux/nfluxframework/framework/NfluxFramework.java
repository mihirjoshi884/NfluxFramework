// File: src/main/java/org/nflux.nfluxframework.framework/NfluxFramework.java
package org.nflux.nfluxframework.framework;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.nflux.nfluxframework.config.NfluxConfig; // Import NfluxConfig
import org.nflux.nfluxframework.enums.config.ErrorMode; // Import ErrorMode
import org.nflux.nfluxframework.pojo.API;
import org.nflux.nfluxframework.utilities.API_DAG;
import org.nflux.nfluxframework.utilities.NfluxUtilities;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public class NfluxFramework {

    private static final Logger LOGGER = Logger.getLogger(NfluxFramework.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final NfluxUtilities nfluxUtilities;
    private final API_DAG apiDag;
    private final List<API> apiDefinitions;
    private final NfluxConfig nfluxConfig;

    /**
     * Constructor for NfluxFramework, now taking NfluxConfig.
     * @param nfluxConfig The NfluxConfig bean.
     * @param webClientBuilder The WebClient.Builder bean.
     * @param apiDefinitions A list of API objects.
     */
    public NfluxFramework(NfluxConfig nfluxConfig, WebClient.Builder webClientBuilder, List<API> apiDefinitions) {
        this.nfluxConfig = nfluxConfig;
        // Pass the NfluxConfig to NfluxUtilities
        this.nfluxUtilities = new NfluxUtilities(webClientBuilder, nfluxConfig);
        this.apiDag = new API_DAG();
        this.apiDefinitions = apiDefinitions != null ? apiDefinitions : Collections.emptyList();
        this.apiDefinitions.forEach(apiDag::addApi);
    }

    // The rest of the executeOrchestration() method remains the same as previously discussed.
    public Mono<Map<String, JsonNode>> executeOrchestration() {
        return Mono.defer(() -> {
            List<String> executionOrder;
            try {
                executionOrder = apiDag.getExecutionOrder();
                if (nfluxConfig.isLoggingEnabled()) {
                    LOGGER.log(Level.INFO, "API Execution Order: {0}", executionOrder);
                }
            } catch (IllegalStateException e) {
                if (nfluxConfig.isErrorLoggingEnabled()) {
                    LOGGER.log(Level.SEVERE, "DAG error during orchestration: {0}", e.getMessage());
                }
                return Mono.error(new RuntimeException("DAG orchestration failed: " + e.getMessage(), e));
            }

            Map<String, JsonNode> orchestrationResults = new ConcurrentHashMap<>();
            Mono<Map<String, JsonNode>> finalResultMono = Mono.just(orchestrationResults);

            for (String apiId : executionOrder) {
                finalResultMono = finalResultMono.flatMap(currentResults -> {
                    API currentApi = apiDefinitions.stream()
                            .filter(api -> api.getId().equals(apiId))
                            .findFirst()
                            .orElseThrow(() -> new IllegalStateException("API definition not found for ID: " + apiId));

                    return nfluxUtilities.executeApi(currentApi, currentResults)
                            .map(responseJson -> {
                                currentResults.put(apiId, responseJson);
                                if (nfluxConfig.isLoggingEnabled()) {
                                    LOGGER.log(Level.INFO, "Successfully executed API: {0}. Response stored.", apiId);
                                }
                                return currentResults;
                            })
                            .onErrorResume(error -> {
                                if (nfluxConfig.getErrorMode() == ErrorMode.FAIL_FAST) {
                                    if (nfluxConfig.isErrorLoggingEnabled()) {
                                        LOGGER.log(Level.SEVERE, "Fail-fast mode: Stopping orchestration due to error in API {0}", apiId);
                                    }
                                    return Mono.error(new RuntimeException("API execution failed for " + apiId + ": " + error.getMessage(), error));
                                } else if (nfluxConfig.getErrorMode() == ErrorMode.CONTINUE_ON_ERROR) {
                                    if (nfluxConfig.isErrorLoggingEnabled()) {
                                        LOGGER.log(Level.WARNING, "Continue-on-error mode: API {0} failed, continuing orchestration.", apiId);
                                    }
                                    ObjectNode errorNode = MAPPER.createObjectNode();
                                    errorNode.put("status", "failed");
                                    errorNode.put("error", error.getMessage());
                                    currentResults.put(apiId, errorNode);
                                    return Mono.just(currentResults);
                                } else if (nfluxConfig.getErrorMode() == ErrorMode.LOG_ONLY) {
                                    if (nfluxConfig.isErrorLoggingEnabled()) {
                                        LOGGER.log(Level.WARNING, "Log-only mode: API {0} failed, continuing without result.", apiId);
                                    }
                                    return Mono.just(currentResults);
                                }
                                return Mono.error(new IllegalStateException("Unknown ErrorMode: " + nfluxConfig.getErrorMode()));
                            });
                });
            }
            return finalResultMono;
        });
    }
}