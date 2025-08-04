
package org.nflux.nfluxframework.framework;

import com.fasterxml.jackson.databind.JsonNode;
import org.nflux.nfluxframework.pojo.API;
import org.nflux.nfluxframework.utilities.API_DAG;
import org.nflux.nfluxframework.utilities.NfluxUtilities;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import org.nflux.nfluxframework.config.NfluxConfig; // Import NfluxConfig

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The core orchestration class of the NfluxFramework.
 * This class is responsible for managing API dependencies, determining execution order
 * using API_DAG, and executing APIs using NfluxUtilities, while passing results
 * between dependent calls.
 */
public class NfluxFramework {

    private static final Logger LOGGER = Logger.getLogger(NfluxFramework.class.getName());

    private final NfluxUtilities nfluxUtilities;
    private final API_DAG apiDag;
    private final List<API> apiDefinitions;
    private final NfluxConfig nfluxConfig;

    /**
     * Constructor for NfluxFramework, designed to be used with Spring's autoconfiguration.
     * It receives the NfluxConfig and a list of API definitions.
     *
     * @param nfluxConfig The NfluxConfig bean, automatically provided by Spring.
     * @param apiDefinitions A list of API objects defining the orchestration flow, optionally provided by Spring.
     */
    public NfluxFramework(NfluxConfig nfluxConfig, WebClient.Builder webClientBuilder,List<API> apiDefinitions) {
        this.nfluxConfig = nfluxConfig;

        this.nfluxUtilities = new NfluxUtilities(webClientBuilder);
        this.apiDag = new API_DAG();
        this.apiDefinitions = apiDefinitions != null ? apiDefinitions : Collections.emptyList(); // Handle null userDefinedApis

        // Add all API definitions to the DAG
        this.apiDefinitions.forEach(apiDag::addApi);
    }

    /**
     * Executes the defined API orchestration flow.
     * It first determines the execution order based on dependencies using API_DAG,
     * then executes each API sequentially, passing results to subsequent dependent APIs.
     *
     * @return A Mono emitting a Map where keys are API IDs and values are their JsonNode responses.
     * The Mono will emit an error if any API execution fails or if a DAG issue is detected.
     */
    public Mono<Map<String, JsonNode>> executeOrchestration() {
        return Mono.defer(() -> {
            List<String> executionOrder;
            try {
                // Get the topologically sorted execution order from the DAG
                executionOrder = apiDag.getExecutionOrder();
                LOGGER.log(Level.INFO, "API Execution Order: {0}", executionOrder);
            } catch (IllegalStateException e) {
                LOGGER.log(Level.SEVERE, "DAG error during orchestration: {0}", e.getMessage());
                return Mono.error(new RuntimeException("DAG orchestration failed: " + e.getMessage(), e));
            }

            // Use a ConcurrentHashMap to store results, as it might be accessed from different threads
            // if we were to parallelize, but for sequential execution, HashMap is fine too.
            // Using ConcurrentHashMap for future-proofing or if reactive chains introduce concurrency.
            Map<String, JsonNode> orchestrationResults = new ConcurrentHashMap<>();

            // This is where the magic happens: chaining reactive calls based on the DAG order.
            // We use Mono.just(orchestrationResults) as an initial seed for the reduce operation.
            // The reduce operation accumulates results by executing APIs one by one.
            Mono<Map<String, JsonNode>> finalResultMono = Mono.just(orchestrationResults);

            for (String apiId : executionOrder) {
                finalResultMono = finalResultMono.flatMap(currentResults -> {
                    API currentApi = apiDefinitions.stream()
                            .filter(api -> api.getId().equals(apiId))
                            .findFirst()
                            .orElseThrow(() -> new IllegalStateException("API definition not found for ID: " + apiId));

                    LOGGER.log(Level.INFO, "Executing API: {0} - {1} ({2})",
                            new Object[]{currentApi.getId(), currentApi.getUrl(), currentApi.getMethod()});

                    // Execute the current API, passing the accumulated results for dependency resolution
                    // The previousResults map is now correctly typed as Map<String, JsonNode>
                    return nfluxUtilities.executeApi(currentApi, currentResults)
                            .map(responseJson -> {
                                // Store the response for the current API
                                currentResults.put(apiId, responseJson);
                                LOGGER.log(Level.INFO, "Successfully executed API: {0}. Response: {1}",
                                        new Object[]{apiId, responseJson.toPrettyString()});
                                return currentResults; // Pass the updated results map to the next flatMap
                            })
                            .onErrorResume(error -> {
                                LOGGER.log(Level.SEVERE, "Error executing API {0}: {1}",
                                        new Object[]{apiId, error.getMessage()});
                                return Mono.error(new RuntimeException("API execution failed for " + apiId + ": " + error.getMessage(), error));
                            });
                });
            }
            return finalResultMono;
        });
    }
}
