
package org.nflux.nfluxframework.frameworkTest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.nflux.nfluxframework.framework.NfluxFramework;
import org.nflux.nfluxframework.config.NfluxConfig;
import org.nflux.nfluxframework.enums.API_TYPE;
import org.nflux.nfluxframework.framework.NfluxFrameworkAutoConfiguration; // Import the auto-configuration
import org.nflux.nfluxframework.pojo.API;
import org.nflux.nfluxframework.pojo.AuthDetails;
import org.nflux.nfluxframework.resources.TestApiDataFactory; // Import our API data factory
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration; // Needed for @Configuration
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod; 
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles; // For loading application-test.properties
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set; // Import Set
import java.util.stream.Collectors;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end integration test for the NfluxFramework.
 * This test loads the Spring context, injects the NfluxFramework bean,
 * and uses WireMock to simulate external API calls, verifying the entire
 * orchestration flow including dependency resolution, input mapping,
 * authentication, and error handling.
 */
@SpringBootTest(
        classes = {NfluxFrameworkTest.TestConfig.class, // Load a minimal config for the test
                NfluxFrameworkAutoConfiguration.class // Load the autoconfiguration to get NfluxFramework bean
        },
        properties = {
                "nflux.loggingEnabled=true",
                "nflux.errorMode=FAIL_FAST",
                "nflux.timeout=5000"
        }
)
@ActiveProfiles("test") // Ensures application-test.properties is loaded if it exists
public class NfluxFrameworkTest {

    @RegisterExtension
    static WireMockExtension wireMockExtension = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @Autowired
    private NfluxFramework nfluxFramework; // Inject the framework bean

    @Autowired
    private WebClient.Builder autowiredWebClientBuilder; // Autowire the WebClient.Builder from the test context

    private ObjectMapper objectMapper = new ObjectMapper();

    // Inner configuration class to define beans required for the test
    @Configuration
    static class TestConfig {
        // Define a WebClient.Builder bean that points to WireMock's dynamic port
        // This bean will be injected into NfluxFramework via NfluxFrameworkAutoConfiguration
        @Bean
        public WebClient.Builder webClientBuilder() {
            return WebClient.builder()
                    .baseUrl("http://localhost:" + wireMockExtension.getPort());
        }

        // Define a bean that provides all API definitions for the framework
        // This will be injected into NfluxFramework via NfluxFrameworkAutoConfiguration
        @Bean
        public List<API> userDefinedApis() {
            return TestApiDataFactory.createAllApis();
        }
    }


    @BeforeEach
    void setUp() {
        wireMockExtension.resetAll(); // Clear stubs and requests before each test
        setupWireMockStubs(); // Set up all necessary stubs for the test APIs
    }

    @AfterEach
    void tearDown() {
        // Debugging hook: After each test, check for unmatched requests and print them.
        if (!wireMockExtension.findAllUnmatchedRequests().isEmpty()) {
            System.err.println("\n--- WireMock Unmatched Requests (DEBUG INFO) ---");
            wireMockExtension.findAllUnmatchedRequests().forEach(request -> {
                System.err.println("  URL: " + request.getUrl());
                System.err.println("  Method: " + request.getMethod());
                System.err.println("  Headers:");
                // Correct way to iterate over WireMock's Request.Headers
                request.getHeaders().all().forEach(header -> System.err.println("    " + header.key() + ": " + header.values()));
                if (request.getBody() != null && request.getBody().length > 0) {
                    System.err.println("  Body: " + new String(request.getBody()));
                }
                System.err.println("--------------------------------------------------");
            });
            fail("Found unmatched requests in WireMock. Check console output for details.");
        }
    }

    /**
     * Sets up all WireMock stubs for the APIs defined in TestApiDataFactory.
     * This ensures that when NfluxUtilities makes calls, WireMock responds as expected.
     * Includes stubs for input mapping scenarios.
     */
    private void setupWireMockStubs() {
        // Independent APIs
        wireMockExtension.stubFor(get(urlPathEqualTo("/products"))
                .withQueryParam("category", equalTo("electronics"))
                .withQueryParam("limit", equalTo("10"))
                .withHeader(HttpHeaders.ACCEPT, equalTo(MediaType.APPLICATION_JSON_VALUE))
                .willReturn(aResponse()
                        .withStatus(HttpStatus.OK.value())
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody("{\"status\": \"success\", \"data\": [{\"id\": \"PROD-1\", \"name\": \"Laptop\"}]}")));

        wireMockExtension.stubFor(get(urlEqualTo("/users"))
                .withHeader(HttpHeaders.AUTHORIZATION, equalTo("Bearer some-bearer-token-123"))
                .willReturn(aResponse()
                        .withStatus(HttpStatus.OK.value())
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody("{\"userId\": \"user456\", \"username\": \"testuser\", \"email\": \"test@example.com\"}")));

        wireMockExtension.stubFor(post(urlEqualTo("/orders"))
                .withHeader(HttpHeaders.CONTENT_TYPE, equalTo(MediaType.APPLICATION_JSON_VALUE))
                .withHeader("X-Request-ID", matching(".*")) // Match any X-Request-ID
                .withRequestBody(equalToJson("{\"item\":\"Laptop\",\"quantity\":1}"))
                .willReturn(aResponse()
                        .withStatus(HttpStatus.CREATED.value())
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody("{\"orderId\": \"ORDER-ABC-123\", \"status\": \"placed\"}")));

        wireMockExtension.stubFor(put(urlEqualTo("/inventory"))
                .withRequestBody(equalToJson("{\"productId\":\"PROD-XYZ-123\",\"stockChange\":-1}")) // Match exact body
                .willReturn(aResponse()
                        .withStatus(HttpStatus.OK.value())
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody("{\"inventoryStatus\": \"updated\"}")));

        wireMockExtension.stubFor(get(urlEqualTo("/health"))
                .willReturn(aResponse()
                        .withStatus(HttpStatus.OK.value())
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody("{\"health\": \"UP\"}")));

        wireMockExtension.stubFor(get(urlEqualTo("/status"))
                .willReturn(aResponse()
                        .withStatus(HttpStatus.OK.value())
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody("{\"systemStatus\": \"Operational\"}")));

        // Dependent APIs - these will receive dynamically mapped inputs
        // api-dep-1: POST /process-order (productId from api-ind-1 as query, userId from api-ind-2 as body.customer.id)
        wireMockExtension.stubFor(post(urlPathEqualTo("/process-order"))
                .withQueryParam("productId", equalTo("PROD-1")) // Mapped from api-ind-1
                .withRequestBody(equalToJson("{\"orderData\":\"processed\", \"customer\":{\"id\":\"user456\"}}")) // Mapped from api-ind-2
                .willReturn(aResponse()
                        .withStatus(HttpStatus.OK.value())
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody("{\"processingStatus\": \"completed\"}")));

        // api-dep-2: PUT /confirm-payment (processingStatus from api-dep-1 as header)
        String basicAuthHeader = "Basic " + Base64.getEncoder().encodeToString("admin:securepass".getBytes());
        wireMockExtension.stubFor(put(urlEqualTo("/confirm-payment"))
                .withHeader(HttpHeaders.AUTHORIZATION, equalTo(basicAuthHeader))
                .withHeader("X-Processing-Status", equalTo("completed")) // Mapped from api-dep-1
                .willReturn(aResponse()
                        .withStatus(HttpStatus.OK.value())
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody("{\"paymentStatus\": \"confirmed\"}")));

        // api-dep-3: PATCH /finalize-transaction (inventoryStatus from api-ind-4 as query, paymentStatus from api-dep-2 as body.transactionDetails.paymentStatus)
        wireMockExtension.stubFor(patch(urlPathEqualTo("/finalize-transaction"))
                .withQueryParam("inventoryResult", equalTo("updated")) // Mapped from api-ind-4
                .withHeader(HttpHeaders.CONTENT_TYPE, equalTo(MediaType.APPLICATION_JSON_VALUE))
                .withRequestBody(equalToJson("{\"finalizationType\":\"automatic\", \"transactionDetails\":{\"paymentStatus\":\"confirmed\"}}")) // Mapped from api-dep-2
                .willReturn(aResponse()
                        .withStatus(HttpStatus.OK.value())
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody("{\"transactionStatus\": \"finalized\"}")));

        // Error stubs (if needed for specific error flow tests)
        wireMockExtension.stubFor(get(urlEqualTo("/api/error"))
                .willReturn(aResponse()
                        .withStatus(HttpStatus.INTERNAL_SERVER_ERROR.value())
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody("{\"error\": \"Internal Server Error\"}")));
    }

    /**
     * Test the full orchestration flow of independent and dependent APIs.
     * This verifies the core functionality of the NfluxFramework, including
     * dependency resolution and input mapping.
     */
    @Test
    void testFullApiOrchestrationFlow() {
        // Execute the orchestration flow
        Mono<Map<String, JsonNode>> orchestrationResultMono = nfluxFramework.executeOrchestration();

        StepVerifier.create(orchestrationResultMono)
                .assertNext(results -> {
                    assertNotNull(results, "Orchestration results should not be null.");

                    // Verify that all APIs executed and their results are present
                    List<API> allApis = TestApiDataFactory.createAllApis();
                    assertEquals(allApis.size(), results.size(), "All APIs should have executed and stored results.");

                    // Verify specific results from the mock responses and input mappings
                    // api-ind-1: /products
                    assertTrue(results.containsKey("api-ind-1"));
                    assertEquals("success", results.get("api-ind-1").get("status").asText());
                    assertEquals("PROD-1", results.get("api-ind-1").get("data").get(0).get("id").asText());

                    // api-ind-2: /users (Bearer Token)
                    assertTrue(results.containsKey("api-ind-2"));
                    assertEquals("user456", results.get("api-ind-2").get("userId").asText());

                    // api-ind-3: /orders (POST)
                    assertTrue(results.containsKey("api-ind-3"));
                    assertEquals("ORDER-ABC-123", results.get("api-ind-3").get("orderId").asText());

                    // api-ind-4: /inventory
                    assertTrue(results.containsKey("api-ind-4"));
                    assertEquals("updated", results.get("api-ind-4").get("inventoryStatus").asText());

                    // api-dep-1: /process-order (depends on api-ind-1 and api-ind-2)
                    assertTrue(results.containsKey("api-dep-1"));
                    assertEquals("completed", results.get("api-dep-1").get("processingStatus").asText());
                    // Verify WireMock received the correct mapped inputs for api-dep-1
                    wireMockExtension.verify(postRequestedFor(urlPathEqualTo("/process-order"))
                            .withQueryParam("productId", equalTo("PROD-1"))
                            .withRequestBody(equalToJson("{\"orderData\":\"processed\", \"customer\":{\"id\":\"user456\"}}")));


                    // api-dep-2: /confirm-payment (depends on api-dep-1)
                    assertTrue(results.containsKey("api-dep-2"));
                    assertEquals("confirmed", results.get("api-dep-2").get("paymentStatus").asText());
                    // Verify WireMock received the correct mapped inputs for api-dep-2
                    String basicAuthHeader = "Basic " + Base64.getEncoder().encodeToString("admin:securepass".getBytes());
                    wireMockExtension.verify(putRequestedFor(urlEqualTo("/confirm-payment"))
                            .withHeader(HttpHeaders.AUTHORIZATION, equalTo(basicAuthHeader))
                            .withHeader("X-Processing-Status", equalTo("completed")));


                    // api-dep-3: /finalize-transaction (depends on api-ind-4 and api-dep-2)
                    assertTrue(results.containsKey("api-dep-3"));
                    assertEquals("finalized", results.get("api-dep-3").get("transactionStatus").asText());
                    // Verify WireMock received the correct mapped inputs for api-dep-3
                    wireMockExtension.verify(patchRequestedFor(urlPathEqualTo("/finalize-transaction"))
                            .withQueryParam("inventoryResult", equalTo("updated"))
                            .withRequestBody(equalToJson("{\"finalizationType\":\"automatic\", \"transactionDetails\":{\"paymentStatus\":\"confirmed\"}}")));

                    System.out.println("\nFull API orchestration flow completed successfully and verified!");
                })
                .verifyComplete();
    }

    /**
     * Test a scenario where a dependency is missing within the DAG, leading to an orchestration failure.
     */
    @Test
    void testOrchestrationWithMissingDependency() {
        // Create a custom list of APIs, intentionally omitting a dependency
        API api1 = new API("api-A", "/a", API_TYPE.INDEPENDENT, HttpMethod.GET);
        API api2 = new API("api-B", "/b", API_TYPE.DEPENDENT, HttpMethod.GET);
        api2.setDependsOn(Set.of("api-C")); // api-C is missing from the list

        List<API> customApis = List.of(api1, api2);

        // Create a new NfluxFramework instance with the custom (problematic) API list
        // Pass the autowired WebClient.Builder to ensure it points to WireMock
        NfluxFramework frameworkWithMissingDep = new NfluxFramework(new NfluxConfig(), autowiredWebClientBuilder, customApis);

        // Execute the orchestration and expect an error due to the missing dependency
        Mono<Map<String, JsonNode>> resultMono = frameworkWithMissingDep.executeOrchestration();

        StepVerifier.create(resultMono)
                .expectErrorMatches(e ->
                        e instanceof RuntimeException &&
                                e.getMessage().contains("DAG orchestration failed") &&
                                e.getCause() instanceof IllegalStateException &&
                                e.getCause().getMessage().contains("Unresolved dependency: API 'api-B' depends on non-existent API 'api-C'"))
                .verify();
    }

    /**
     * Test a scenario with a circular dependency, leading to an orchestration failure.
     */
    @Test
    void testOrchestrationWithCircularDependency() {
        // Create a custom list of APIs with a circular dependency
        API api1 = new API("api-X", "/x", API_TYPE.DEPENDENT, HttpMethod.GET);
        API api2 = new API("api-Y", "/y", API_TYPE.DEPENDENT, HttpMethod.GET);
        API api3 = new API("api-Z", "/z", API_TYPE.DEPENDENT, HttpMethod.GET);

        api1.setDependsOn(Set.of("api-Y"));
        api2.setDependsOn(Set.of("api-Z"));
        api3.setDependsOn(Set.of("api-X")); // Circular: X -> Y -> Z -> X

        List<API> customApis = List.of(api1, api2, api3);

        // Create a new NfluxFramework instance with the custom (problematic) API list
        // Pass the autowired WebClient.Builder
        NfluxFramework frameworkWithCircularDep = new NfluxFramework(new NfluxConfig(), autowiredWebClientBuilder, customApis);

        // Execute the orchestration and expect an error due to the circular dependency
        Mono<Map<String, JsonNode>> resultMono = frameworkWithCircularDep.executeOrchestration();

        StepVerifier.create(resultMono)
                .expectErrorMatches(e ->
                        e instanceof RuntimeException &&
                                e.getMessage().contains("DAG orchestration failed") &&
                                e.getCause() instanceof IllegalStateException &&
                                e.getCause().getMessage().contains("Cycle detected in API dependencies! Cannot determine a valid execution order."))
                .verify();
    }

    /**
     * Test a scenario where an API call within the orchestration fails (e.g., 500 error).
     * The orchestration should propagate the error.
     */
    @Test
    void testOrchestrationWithApiExecutionFailure() {
        // Create a list of APIs including one that will return an error
        API api1 = new API("api-good", "/status", API_TYPE.INDEPENDENT, HttpMethod.GET);
        API apiWithError = new API("api-bad", "/api/error", API_TYPE.INDEPENDENT, HttpMethod.GET);
        API api3 = new API("api-dependent-on-bad", "/follow-up", API_TYPE.DEPENDENT, HttpMethod.GET);
        api3.setDependsOn(Set.of("api-bad")); // This API will never run if api-bad fails

        List<API> apisWithFailure = List.of(api1, apiWithError, api3);

        // Create a new NfluxFramework instance with this API list
        // Pass the autowired WebClient.Builder
        NfluxFramework frameworkWithFailure = new NfluxFramework(new NfluxConfig(), autowiredWebClientBuilder, apisWithFailure);

        // Execute the orchestration and expect an error
        Mono<Map<String, JsonNode>> resultMono = frameworkWithFailure.executeOrchestration();

        StepVerifier.create(resultMono)
                .expectErrorMatches(e ->
                        e instanceof RuntimeException &&
                                e.getMessage().contains("API execution failed for api-bad") && // Expecting api-bad to fail
                                e.getCause() instanceof RuntimeException && // First intermediate RuntimeException
                                e.getCause().getCause() instanceof RuntimeException && // Second intermediate RuntimeException
                                e.getCause().getCause().getCause() instanceof WebClientResponseException && // The actual WebClientResponseException
                                ((WebClientResponseException) e.getCause().getCause().getCause()).getStatusCode() == HttpStatus.INTERNAL_SERVER_ERROR)
                .verify();

        // Verify that api-good was called successfully
        wireMockExtension.verify(getRequestedFor(urlEqualTo("/status")));
        // Verify that api-bad was called and returned an error
        wireMockExtension.verify(getRequestedFor(urlEqualTo("/api/error")));
        // Verify that api-dependent-on-bad was NOT called
        wireMockExtension.verify(0, getRequestedFor(urlEqualTo("/follow-up")));
    }
}
