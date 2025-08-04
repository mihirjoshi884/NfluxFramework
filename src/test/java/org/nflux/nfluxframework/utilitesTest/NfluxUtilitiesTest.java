package org.nflux.nfluxframework.utilitesTest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.nflux.nfluxframework.config.NfluxConfig;
import org.nflux.nfluxframework.enums.API_TYPE;
import org.nflux.nfluxframework.enums.AUTH_WAYS;
import org.nflux.nfluxframework.enums.InputTargetType;
import org.nflux.nfluxframework.enums.config.OutputFormat;
import org.nflux.nfluxframework.pojo.API;
import org.nflux.nfluxframework.pojo.AuthDetails;
import org.nflux.nfluxframework.pojo.InputMappingDetail;
import org.nflux.nfluxframework.utilities.NfluxUtilities;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
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
    private NfluxConfig nfluxConfig;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        WebClient.Builder webClientBuilder = WebClient.builder()
                .baseUrl("http://localhost:" + wireMockExtension.getPort());

        // Correctly instantiate NfluxConfig and pass it to the utility class
        this.nfluxConfig = new NfluxConfig();
        this.nfluxUtilities = new NfluxUtilities(webClientBuilder, nfluxConfig);

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
    void testExecuteApi_DependentApiWithQueryParamMapping() throws Exception {
        // Simulate a previous API result with search criteria
        Map<String, JsonNode> previousResults = new HashMap<>();
        previousResults.put("search-criteria-api", objectMapper.createObjectNode()
                .put("query", "electronics")
                .put("page", 2));

        // Define a dependent API that uses values as query parameters
        API api = new API("test-dep-query-param", "/search", API_TYPE.DEPENDENT, HttpMethod.GET);
        Map<String, InputMappingDetail> mappings = new HashMap<>();
        mappings.put("searchQuery", new InputMappingDetail("search-criteria-api", "$.query", InputTargetType.QUERY_PARAM, "q"));
        mappings.put("pageNumber", new InputMappingDetail("search-criteria-api", "$.page", InputTargetType.QUERY_PARAM, "page"));
        api.setInputMappings(mappings);

        // Stub WireMock to expect the request with the dynamically injected query parameters
        wireMockExtension.stubFor(get(urlPathEqualTo("/search"))
                .withQueryParam("q", equalTo("electronics"))
                .withQueryParam("page", equalTo("2"))
                .willReturn(aResponse()
                        .withStatus(HttpStatus.OK.value())
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody("{\"results\": [\"item1\", \"item2\"]}")));

        // Execute and verify
        Mono<JsonNode> resultMono = nfluxUtilities.executeApi(api, previousResults);

        StepVerifier.create(resultMono)
                .assertNext(jsonNode -> {
                    assertNotNull(jsonNode);
                    assertTrue(jsonNode.has("results"));
                    assertEquals("item1", jsonNode.get("results").get(0).asText());
                })
                .verifyComplete();

        wireMockExtension.verify(getRequestedFor(urlPathEqualTo("/search"))
                .withQueryParam("q", equalTo("electronics"))
                .withQueryParam("page", equalTo("2")));
    }

    @Test
    void testExecuteApi_DependentApiWithRequestBodyMapping() throws Exception {
        // Simulate a previous API result with a user and products
        Map<String, JsonNode> previousResults = new HashMap<>();
        previousResults.put("user-details-api", objectMapper.readTree("{\"user\":{\"id\":\"user-1\"},\"products\":[{\"id\":\"p1\"}]}"));

        // Define a dependent API that uses values from previous results to build a request body
        API api = new API("test-dep-body-map", "/submit", API_TYPE.DEPENDENT, HttpMethod.POST);
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("cartId", "cart-123");
        api.setRequestBody(requestBody);

        Map<String, InputMappingDetail> mappings = new HashMap<>();
        mappings.put("userIdMapping", new InputMappingDetail("user-details-api", "$.user.id", InputTargetType.REQUEST_BODY_FIELD, "userId"));
        mappings.put("productIdMapping", new InputMappingDetail("user-details-api", "$.products[0].id", InputTargetType.REQUEST_BODY_FIELD, "items[0].productId"));
        api.setInputMappings(mappings);

        // Stub WireMock to expect the dynamically generated request body
        wireMockExtension.stubFor(post(urlEqualTo("/submit"))
                .withRequestBody(equalToJson("{\"cartId\":\"cart-123\",\"userId\":\"user-1\",\"items\":[{\"productId\":\"p1\"}]}"))
                .willReturn(aResponse()
                        .withStatus(HttpStatus.OK.value())
                        .withBody("{\"status\": \"success\"}")));

        // Execute and verify
        Mono<JsonNode> resultMono = nfluxUtilities.executeApi(api, previousResults);

        StepVerifier.create(resultMono)
                .assertNext(jsonNode -> {
                    assertNotNull(jsonNode);
                    assertEquals("success", jsonNode.get("status").asText());
                })
                .verifyComplete();
    }

    @Test
    void testExecuteApi_DependentApiWithHeaderMapping() throws Exception {
        // Simulate a previous API result with a correlation ID
        Map<String, JsonNode> previousResults = new HashMap<>();
        previousResults.put("session-api", objectMapper.createObjectNode()
                .put("correlationId", "corr-abc-123"));

        // Define a dependent API that uses the correlation ID in a custom header
        API api = new API("test-dep-header-map", "/events", API_TYPE.DEPENDENT, HttpMethod.POST);
        Map<String, InputMappingDetail> mappings = new HashMap<>();
        mappings.put("corrIdHeader", new InputMappingDetail("session-api", "$.correlationId", InputTargetType.HEADER, "X-Correlation-ID"));
        api.setInputMappings(mappings);

        // Stub WireMock to expect the request with the custom header
        wireMockExtension.stubFor(post(urlEqualTo("/events"))
                .withHeader("X-Correlation-ID", equalTo("corr-abc-123"))
                .willReturn(aResponse()
                        .withStatus(HttpStatus.OK.value())
                        .withBody("{\"eventStatus\": \"logged\"}")));

        // Execute and verify
        Mono<JsonNode> resultMono = nfluxUtilities.executeApi(api, previousResults);

        StepVerifier.create(resultMono)
                .assertNext(jsonNode -> {
                    assertNotNull(jsonNode);
                    assertEquals("logged", jsonNode.get("eventStatus").asText());
                })
                .verifyComplete();

        wireMockExtension.verify(postRequestedFor(urlEqualTo("/events"))
                .withHeader("X-Correlation-ID", equalTo("corr-abc-123")));
    }


    @Test
    void testExecuteApi_MappingToNonExistentSourceApi_ThrowsException() {
        // Define a dependent API without providing a result for its source
        API api = new API("test-dep-no-source", "/data", API_TYPE.DEPENDENT, HttpMethod.GET);
        Map<String, InputMappingDetail> mappings = new HashMap<>();
        mappings.put("badMapping", new InputMappingDetail("non-existent-api", "$.id", InputTargetType.PATH_VARIABLE, "id"));
        api.setInputMappings(mappings);

        // Execute and verify that an exception is thrown
        Mono<JsonNode> resultMono = nfluxUtilities.executeApi(api, Collections.emptyMap());

        StepVerifier.create(resultMono)
                .expectErrorMatches(throwable ->
                        throwable instanceof RuntimeException &&
                                throwable.getMessage().contains("Failed to apply input mapping") &&
                                throwable.getCause() instanceof IllegalStateException &&
                                throwable.getCause().getMessage().contains("Source API result not found")
                )
                .verify();
    }

    @Test
    void testExecuteApi_MappingWithNullExtractedValue_SkipsMapping() throws Exception {
        // Simulate a previous API result where a field is missing
        Map<String, JsonNode> previousResults = new HashMap<>();
        previousResults.put("source-api-missing-field", objectMapper.readTree("{\"data\":{\"id\":123}}"));

        // Define a dependent API with a mapping to a non-existent field
        API api = new API("test-dep-missing-field", "/data", API_TYPE.DEPENDENT, HttpMethod.GET);
        Map<String, InputMappingDetail> mappings = new HashMap<>();
        mappings.put("missingField", new InputMappingDetail("source-api-missing-field", "$.data.name", InputTargetType.QUERY_PARAM, "name"));
        api.setInputMappings(mappings);

        // Stub WireMock to expect a request without the missing query parameter
        wireMockExtension.stubFor(get(urlEqualTo("/data"))
                .willReturn(aResponse()
                        .withStatus(HttpStatus.OK.value())
                        .withBody("{\"status\": \"OK\"}")));

        // Execute and verify that no error is thrown and the call succeeds
        Mono<JsonNode> resultMono = nfluxUtilities.executeApi(api, previousResults);

        StepVerifier.create(resultMono)
                .assertNext(jsonNode -> assertEquals("OK", jsonNode.get("status").asText()))
                .verifyComplete();

        // Verify that the request was made without the 'name' query parameter
        wireMockExtension.verify(getRequestedFor(urlEqualTo("/data")));
    }


    @Test
    void testExecuteApi_CallFailsWith404Error_ReturnsErrorMono() {
        // Define an API for a non-existent endpoint
        API api = new API("test-404", "/non-existent", API_TYPE.INDEPENDENT, HttpMethod.GET);

        // Stub WireMock to return a 404 Not Found error
        wireMockExtension.stubFor(get(urlEqualTo("/non-existent"))
                .willReturn(aResponse().withStatus(HttpStatus.NOT_FOUND.value())));

        // Execute and verify that the Mono completes with an error
        Mono<JsonNode> resultMono = nfluxUtilities.executeApi(api, Collections.emptyMap());

        // Corrected assertion: Expect the specific RuntimeException with its message.
        StepVerifier.create(resultMono)
                .expectErrorMatches(throwable ->
                        throwable instanceof RuntimeException &&
                                throwable.getMessage().startsWith("API execution failed for test-404")
                )
                .verify();
    }

    @Test
    void testExecuteApi_OutputFormatIsFlatString_ReturnsObjectNodeWithRawString() {
        // Define a simple API
        API api = new API("test-flat-string", "/data", API_TYPE.INDEPENDENT, HttpMethod.GET);
        nfluxConfig.setOutputFormat(OutputFormat.FLAT_STRING);

        String rawResponse = "Hello, this is a plain text response.";
        wireMockExtension.stubFor(get(urlEqualTo("/data"))
                .willReturn(aResponse()
                        .withStatus(HttpStatus.OK.value())
                        .withBody(rawResponse)));

        Mono<JsonNode> resultMono = nfluxUtilities.executeApi(api, Collections.emptyMap());

        StepVerifier.create(resultMono)
                .assertNext(jsonNode -> {
                    assertTrue(jsonNode.isObject());
                    assertEquals(rawResponse, jsonNode.get("response").asText());
                })
                .verifyComplete();
    }

    @Test
    void testExecuteApi_OutputFormatIsXMLDocument_ReturnsObjectNodeWithRawString() {
        // Define a simple API
        API api = new API("test-xml", "/data", API_TYPE.INDEPENDENT, HttpMethod.GET);
        nfluxConfig.setOutputFormat(OutputFormat.XML_DOCUMENT);

        String rawResponse = "<data><item>1</item></data>";
        wireMockExtension.stubFor(get(urlEqualTo("/data"))
                .willReturn(aResponse()
                        .withStatus(HttpStatus.OK.value())
                        .withBody(rawResponse)));

        Mono<JsonNode> resultMono = nfluxUtilities.executeApi(api, Collections.emptyMap());

        StepVerifier.create(resultMono)
                .assertNext(jsonNode -> {
                    assertTrue(jsonNode.isObject());
                    assertEquals("XML_CONTENT: " + rawResponse, jsonNode.get("response").asText());
                })
                .verifyComplete();
    }
}
