
package org.nflux.nfluxframework.utilitesTest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.nflux.nfluxframework.enums.API_TYPE;
import org.nflux.nfluxframework.enums.AUTH_WAYS;
import org.nflux.nfluxframework.enums.InputTargetType;
import org.nflux.nfluxframework.pojo.API;
import org.nflux.nfluxframework.pojo.AuthDetails;
import org.nflux.nfluxframework.pojo.InputMappingDetail;
import org.nflux.nfluxframework.utilities.NfluxUtilities;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException; // Import WebClientResponseException
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Unit and integration tests for the NfluxUtilities class.
 * This class verifies the correct execution of individual API calls,
 * including dynamic input mapping, various authentication types, and error handling.
 */
public class NfluxUtilitiesTest {

    @RegisterExtension
    static WireMockExtension wireMockExtension = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    private NfluxUtilities nfluxUtilities;
    private ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        WebClient.Builder webClientBuilder = WebClient.builder()
                .baseUrl("http://localhost:" + wireMockExtension.getPort());
        nfluxUtilities = new NfluxUtilities(webClientBuilder);
        wireMockExtension.resetAll(); // Clear stubs and requests before each test
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
                request.getHeaders().all().forEach(header -> System.err.println("    " + header.key() + ": " + header.values()));
                if (request.getBody() != null && request.getBody().length > 0) {
                    System.err.println("  Body: " + new String(request.getBody()));
                }
                System.err.println("--------------------------------------------------");
            });
            fail("Found unmatched requests in WireMock. Check console output for details.");
        }
    }

    @Test
    void testExecuteApi_GetRequestNoAuth() {
        // Define a simple GET API
        API api = new API("test-get-api", "/status", API_TYPE.INDEPENDENT, HttpMethod.GET);

        // Stub WireMock to respond to this GET request
        wireMockExtension.stubFor(get(urlEqualTo("/status"))
                .willReturn(aResponse()
                        .withStatus(HttpStatus.OK.value())
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody("{\"systemStatus\": \"Operational\"}")));

        // Execute the API and verify the response
        Mono<JsonNode> resultMono = nfluxUtilities.executeApi(api, Collections.emptyMap());

        StepVerifier.create(resultMono)
                .assertNext(jsonNode -> {
                    assertNotNull(jsonNode);
                    assertEquals("Operational", jsonNode.get("systemStatus").asText());
                })
                .verifyComplete();

        // Verify that WireMock received the request
        wireMockExtension.verify(getRequestedFor(urlEqualTo("/status")));
    }

    @Test
    void testExecuteApi_PostRequestWithBodyAndHeaders() {
        // Define a POST API with a request body and custom header
        API api = new API("test-post-api", "/orders", API_TYPE.INDEPENDENT, HttpMethod.POST);
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("item", "Keyboard");
        requestBody.put("quantity", 2);
        api.setRequestBody(requestBody);

        Map<String, String> headers = new HashMap<>();
        String requestId = UUID.randomUUID().toString();
        headers.put("X-Request-ID", requestId);
        api.setHeaders(headers);

        // Stub WireMock
        wireMockExtension.stubFor(post(urlEqualTo("/orders"))
                .withHeader("X-Request-ID", equalTo(requestId))
                .withRequestBody(equalToJson("{\"item\":\"Keyboard\",\"quantity\":2}"))
                .willReturn(aResponse()
                        .withStatus(HttpStatus.CREATED.value())
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody("{\"orderId\": \"ORDER-XYZ-789\", \"status\": \"created\"}")));

        // Execute and verify
        Mono<JsonNode> resultMono = nfluxUtilities.executeApi(api, Collections.emptyMap());

        StepVerifier.create(resultMono)
                .assertNext(jsonNode -> {
                    assertNotNull(jsonNode);
                    assertEquals("ORDER-XYZ-789", jsonNode.get("orderId").asText());
                    assertEquals("created", jsonNode.get("status").asText());
                })
                .verifyComplete();

        wireMockExtension.verify(postRequestedFor(urlEqualTo("/orders"))
                .withHeader("X-Request-ID", equalTo(requestId))
                .withRequestBody(equalToJson("{\"item\":\"Keyboard\",\"quantity\":2}")));
    }

    @Test
    void testExecuteApi_GetRequestWithBearerTokenAuth() {
        // Define a GET API with Bearer Token authentication
        API api = new API("test-auth-api", "/protected/resource", API_TYPE.INDEPENDENT, HttpMethod.GET);
        AuthDetails authDetails = AuthDetails.bearerToken("my-secret-token");
        api.setAuthDetails(authDetails);

        // Stub WireMock to expect the Bearer token
        wireMockExtension.stubFor(get(urlEqualTo("/protected/resource"))
                .withHeader(HttpHeaders.AUTHORIZATION, equalTo("Bearer my-secret-token"))
                .willReturn(aResponse()
                        .withStatus(HttpStatus.OK.value())
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody("{\"access\": \"granted\"}")));

        // Execute and verify
        Mono<JsonNode> resultMono = nfluxUtilities.executeApi(api, Collections.emptyMap());

        StepVerifier.create(resultMono)
                .assertNext(jsonNode -> {
                    assertNotNull(jsonNode);
                    assertEquals("granted", jsonNode.get("access").asText());
                })
                .verifyComplete();

        wireMockExtension.verify(getRequestedFor(urlEqualTo("/protected/resource"))
                .withHeader(HttpHeaders.AUTHORIZATION, equalTo("Bearer my-secret-token")));
    }

    @Test
    void testExecuteApi_GetRequestWithBasicAuth() {
        // Define a GET API with Basic Authentication
        API api = new API("test-basic-auth-api", "/admin/dashboard", API_TYPE.INDEPENDENT, HttpMethod.GET);
        AuthDetails authDetails = AuthDetails.basicAuth("testuser", "testpass");
        api.setAuthDetails(authDetails);

        // Calculate expected Basic Auth header
        String expectedAuth = "Basic " + Base64.getEncoder().encodeToString("testuser:testpass".getBytes());

        // Stub WireMock to expect the Basic Auth header
        wireMockExtension.stubFor(get(urlEqualTo("/admin/dashboard"))
                .withHeader(HttpHeaders.AUTHORIZATION, equalTo(expectedAuth))
                .willReturn(aResponse()
                        .withStatus(HttpStatus.OK.value())
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody("{\"adminStatus\": \"active\"}")));

        // Execute and verify
        Mono<JsonNode> resultMono = nfluxUtilities.executeApi(api, Collections.emptyMap());

        StepVerifier.create(resultMono)
                .assertNext(jsonNode -> {
                    assertNotNull(jsonNode);
                    assertEquals("active", jsonNode.get("adminStatus").asText());
                })
                .verifyComplete();

        wireMockExtension.verify(getRequestedFor(urlEqualTo("/admin/dashboard"))
                .withHeader(HttpHeaders.AUTHORIZATION, equalTo(expectedAuth)));
    }

    @Test
    void testExecuteApi_GetRequestWithApiKeyAuth() {
        // Define a GET API with API Key authentication
        API api = new API("test-api-key-auth", "/data/secure", API_TYPE.INDEPENDENT, HttpMethod.GET);
        AuthDetails authDetails = AuthDetails.apiKey("X-API-Key", "my-super-secret-api-key");
        api.setAuthDetails(authDetails);

        // Stub WireMock to expect the API Key header
        wireMockExtension.stubFor(get(urlEqualTo("/data/secure"))
                .withHeader("X-API-Key", equalTo("my-super-secret-api-key"))
                .willReturn(aResponse()
                        .withStatus(HttpStatus.OK.value())
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody("{\"dataAccess\": \"allowed\"}")));

        // Execute and verify
        Mono<JsonNode> resultMono = nfluxUtilities.executeApi(api, Collections.emptyMap());

        StepVerifier.create(resultMono)
                .assertNext(jsonNode -> {
                    assertNotNull(jsonNode);
                    assertEquals("allowed", jsonNode.get("dataAccess").asText());
                })
                .verifyComplete();

        wireMockExtension.verify(getRequestedFor(urlEqualTo("/data/secure"))
                .withHeader("X-API-Key", equalTo("my-super-secret-api-key")));
    }


    @Test
    void testExecuteApi_DependentApiWithPathVariableMapping() {
        // Simulate a previous API result that contains a user ID
        Map<String, JsonNode> previousResults = new HashMap<>();
        String userId = "user123";
        previousResults.put("api-get-user", objectMapper.createObjectNode().put("id", userId).put("name", "John Doe"));

        // Define a dependent API that uses the user ID as a path variable
        API api = new API("test-dep-path-var", "/users/{userId}/profile", API_TYPE.DEPENDENT, HttpMethod.GET);
        Map<String, InputMappingDetail> mappings = new HashMap<>();
        mappings.put("userIdPath", new InputMappingDetail("api-get-user", "$.id", InputTargetType.PATH_VARIABLE, "userId"));
        api.setInputMappings(mappings);

        // Stub WireMock to expect the request with the dynamically injected path variable
        wireMockExtension.stubFor(get(urlEqualTo("/users/" + userId + "/profile"))
                .willReturn(aResponse()
                        .withStatus(HttpStatus.OK.value())
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody("{\"profile\": {\"userId\": \"" + userId + "\", \"status\": \"active\"}}")));

        // Execute and verify
        Mono<JsonNode> resultMono = nfluxUtilities.executeApi(api, previousResults);

        StepVerifier.create(resultMono)
                .assertNext(jsonNode -> {
                    assertNotNull(jsonNode);
                    assertEquals(userId, jsonNode.get("profile").get("userId").asText());
                })
                .verifyComplete();

        wireMockExtension.verify(getRequestedFor(urlEqualTo("/users/" + userId + "/profile")));
    }

    @Test
    void testExecuteApi_DependentApiWithQueryParamMapping() {
        // Simulate a previous API result with a product category
        Map<String, JsonNode> previousResults = new HashMap<>();
        previousResults.put("api-get-category", objectMapper.createObjectNode().put("category", "electronics"));

        // Define a dependent API that uses the category as a query parameter
        API api = new API("test-dep-query-param", "/products/search", API_TYPE.DEPENDENT, HttpMethod.GET);
        Map<String, InputMappingDetail> mappings = new HashMap<>();
        mappings.put("categoryQuery", new InputMappingDetail("api-get-category", "$.category", InputTargetType.QUERY_PARAM, "cat"));
        api.setInputMappings(mappings);

        // Stub WireMock to expect the request with the dynamically injected query parameter
        wireMockExtension.stubFor(get(urlPathEqualTo("/products/search"))
                .withQueryParam("cat", equalTo("electronics"))
                .willReturn(aResponse()
                        .withStatus(HttpStatus.OK.value())
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody("{\"results\": [{\"name\": \"Laptop\"}]}")));

        // Execute and verify
        Mono<JsonNode> resultMono = nfluxUtilities.executeApi(api, previousResults);

        StepVerifier.create(resultMono)
                .assertNext(jsonNode -> {
                    assertNotNull(jsonNode);
                    assertTrue(jsonNode.has("results"));
                })
                .verifyComplete();

        wireMockExtension.verify(getRequestedFor(urlPathEqualTo("/products/search"))
                .withQueryParam("cat", equalTo("electronics")));
    }

    @Test
    void testExecuteApi_DependentApiWithRequestBodyFieldMapping() {
        // Simulate a previous API result with an order ID
        Map<String, JsonNode> previousResults = new HashMap<>();
        previousResults.put("api-create-order", objectMapper.createObjectNode().put("orderId", "ORDER-123"));

        // Define a dependent API (POST) that uses the order ID in its request body
        API api = new API("test-dep-body-field", "/order/update", API_TYPE.DEPENDENT, HttpMethod.POST);
        Map<String, Object> initialRequestBody = new HashMap<>();
        initialRequestBody.put("status", "shipped");
        api.setRequestBody(initialRequestBody);

        Map<String, InputMappingDetail> mappings = new HashMap<>();
        mappings.put("orderIdBody", new InputMappingDetail("api-create-order", "$.orderId", InputTargetType.REQUEST_BODY_FIELD, "orderDetails.id"));
        api.setInputMappings(mappings);

        // Stub WireMock to expect the request body with the dynamically injected order ID
        wireMockExtension.stubFor(post(urlEqualTo("/order/update"))
                .withRequestBody(equalToJson("{\"status\":\"shipped\", \"orderDetails\":{\"id\":\"ORDER-123\"}}"))
                .willReturn(aResponse()
                        .withStatus(HttpStatus.OK.value())
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody("{\"updateStatus\": \"success\"}")));

        // Execute and verify
        Mono<JsonNode> resultMono = nfluxUtilities.executeApi(api, previousResults);

        StepVerifier.create(resultMono)
                .assertNext(jsonNode -> {
                    assertNotNull(jsonNode);
                    assertEquals("success", jsonNode.get("updateStatus").asText());
                })
                .verifyComplete();

        wireMockExtension.verify(postRequestedFor(urlEqualTo("/order/update"))
                .withRequestBody(equalToJson("{\"status\":\"shipped\", \"orderDetails\":{\"id\":\"ORDER-123\"}}")));
    }

    @Test
    void testExecuteApi_DependentApiWithHeaderMapping() {
        // Simulate a previous API result with a session token
        Map<String, JsonNode> previousResults = new HashMap<>();
        previousResults.put("api-login", objectMapper.createObjectNode().put("sessionToken", "ABCDEF12345"));

        // Define a dependent API that uses the session token as a custom header
        API api = new API("test-dep-header", "/secure/data", API_TYPE.DEPENDENT, HttpMethod.GET);
        Map<String, InputMappingDetail> mappings = new HashMap<>();
        mappings.put("sessionTokenHeader", new InputMappingDetail("api-login", "$.sessionToken", InputTargetType.HEADER, "X-Session-Token"));
        api.setInputMappings(mappings);

        // Stub WireMock to expect the request with the dynamically injected header
        wireMockExtension.stubFor(get(urlEqualTo("/secure/data"))
                .withHeader("X-Session-Token", equalTo("ABCDEF12345"))
                .willReturn(aResponse()
                        .withStatus(HttpStatus.OK.value())
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody("{\"data\": \"sensitive info\"}")));

        // Execute and verify
        Mono<JsonNode> resultMono = nfluxUtilities.executeApi(api, previousResults);

        StepVerifier.create(resultMono)
                .assertNext(jsonNode -> {
                    assertNotNull(jsonNode);
                    assertEquals("sensitive info", jsonNode.get("data").asText());
                })
                .verifyComplete();

        wireMockExtension.verify(getRequestedFor(urlEqualTo("/secure/data"))
                .withHeader("X-Session-Token", equalTo("ABCDEF12345")));
    }

    @Test
    void testExecuteApi_ErrorResponseHandling() {
        // Define an API that will return an error
        API api = new API("test-error-api", "/api/error", API_TYPE.INDEPENDENT, HttpMethod.GET);

        // Stub WireMock to return an internal server error
        wireMockExtension.stubFor(get(urlEqualTo("/api/error"))
                .willReturn(aResponse()
                        .withStatus(HttpStatus.INTERNAL_SERVER_ERROR.value())
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody("{\"error\": \"Something went wrong\"}")));

        // Execute and verify that an error is propagated
        Mono<JsonNode> resultMono = nfluxUtilities.executeApi(api, Collections.emptyMap());

        StepVerifier.create(resultMono)
                .expectErrorMatches(throwable ->
                        throwable instanceof RuntimeException &&
                                throwable.getMessage().contains("API execution failed for test-error-api") &&
                                throwable.getMessage().contains("500 INTERNAL_SERVER_ERROR")) // Refined message check
                .verify();

        wireMockExtension.verify(getRequestedFor(urlEqualTo("/api/error")));
    }

    @Test
    void testExecuteApi_MissingSourceApiResultForMapping() {
        // Define a dependent API with a mapping, but don't provide the source API's result
        API api = new API("test-missing-source", "/data/{id}", API_TYPE.DEPENDENT, HttpMethod.GET);
        Map<String, InputMappingDetail> mappings = new HashMap<>();
        mappings.put("missingSource", new InputMappingDetail("non-existent-api", "$.someId", InputTargetType.PATH_VARIABLE, "id"));
        api.setInputMappings(mappings);

        // Execute and expect an IllegalStateException due to missing source result
        Mono<JsonNode> resultMono = nfluxUtilities.executeApi(api, Collections.emptyMap());

        StepVerifier.create(resultMono)
                .expectErrorMatches(throwable ->
                        throwable instanceof RuntimeException &&
                                throwable.getMessage().contains("Failed to apply input mapping for test-missing-source") &&
                                throwable.getCause() instanceof IllegalStateException &&
                                throwable.getCause().getMessage().contains("Source API result not found for mapping: non-existent-api for API: test-missing-source")) // Refined message check
                .verify();
    }
}
