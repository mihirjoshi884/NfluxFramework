
package org.nflux.nfluxframework.resources;

import org.nflux.nfluxframework.enums.API_TYPE;
import org.nflux.nfluxframework.enums.AUTH_WAYS;
import org.springframework.http.HttpMethod;
import org.nflux.nfluxframework.pojo.API;
import org.nflux.nfluxframework.pojo.AuthDetails;
import org.nflux.nfluxframework.pojo.InputMappingDetail; // Corrected import based on your instruction
import org.nflux.nfluxframework.enums.InputTargetType; // Corrected import based on your instruction
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.ArrayList;

/**
 * A factory class to create predefined API objects for testing purposes.
 * This helps in creating consistent and reusable test data for various scenarios.
 */
public class TestApiDataFactory {

    /**
     * Creates a list of 6 independent API definitions.
     * These APIs have no dependencies on other APIs within the system.
     *
     * @return A list of independent API objects.
     */
    public static List<API> createIndependentApis() {
        // API constructor: public API(String id, String url, API_TYPE type, HttpMethod method)
        API api1 = new API("api-ind-1", "/products", API_TYPE.INDEPENDENT, HttpMethod.GET);
        API api2 = new API("api-ind-2", "/users", API_TYPE.INDEPENDENT, HttpMethod.GET);
        API api3 = new API("api-ind-3", "/orders", API_TYPE.INDEPENDENT, HttpMethod.POST);
        API api4 = new API("api-ind-4", "/inventory", API_TYPE.INDEPENDENT, HttpMethod.PUT);
        API api5 = new API("api-ind-5", "/health", API_TYPE.INDEPENDENT, HttpMethod.GET);
        API api6 = new API("api-ind-6", "/status", API_TYPE.INDEPENDENT, HttpMethod.GET);

        // Add some variety: headers, query params, body, auth
        Map<String, String> headers3 = new HashMap<>();
        headers3.put("X-Request-ID", UUID.randomUUID().toString());
        api3.setHeaders(headers3);

        Map<String, Object> requestBody3 = new HashMap<>();
        requestBody3.put("item", "Laptop");
        requestBody3.put("quantity", 1);
        api3.setRequestBody(requestBody3);

        Map<String, Object> queryParams1 = new HashMap<>();
        queryParams1.put("category", "electronics");
        queryParams1.put("limit", 10);
        api1.setQueryParams(queryParams1);
        // FIX: Explicitly set Accept header for api-ind-1 to match WireMock stub
        Map<String, String> headers1 = new HashMap<>();
        headers1.put(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
        api1.setHeaders(headers1);

        // Updated AuthDetails creation using static factory method for clarity
        AuthDetails bearerAuth2 = AuthDetails.bearerToken("some-bearer-token-123"); // FIX: Changed to lowercase 'b'
        api2.setAuthDetails(bearerAuth2);

        // For api4 (PUT /inventory), we'll simulate a body that might be used for matching in WireMock
        Map<String, Object> requestBody4 = new HashMap<>();
        requestBody4.put("productId", "PROD-XYZ-123");
        requestBody4.put("stockChange", -1);
        api4.setRequestBody(requestBody4);


        return Arrays.asList(api1, api2, api3, api4, api5, api6);
    }

    /**
     * Creates a list of 3 dependent API definitions.
     * These APIs have dependencies on the independent APIs or each other.
     *
     * @param independentApis A list of independent APIs to establish dependencies.
     * It's assumed these IDs exist.
     * @return A list of dependent API objects.
     */
    public static List<API> createDependentApis(List<API> independentApis) {
        // Map independent APIs by ID for easy lookup
        Map<String, API> indApiMap = new HashMap<>();
        independentApis.forEach(api -> indApiMap.put(api.getId(), api));

        // Dependent API 1: Depends on api-ind-1 and api-ind-2
        // This API will use the 'id' from 'api-ind-1' (products) as a query parameter
        // and 'userId' from 'api-ind-2' (users) as a request body field.
        API apiDep1 = new API("api-dep-1", "/process-order", API_TYPE.DEPENDENT, HttpMethod.POST);
        Set<String> dep1Dependencies = new HashSet<>(Arrays.asList(
                indApiMap.get("api-ind-1").getId(), // e.g., get product details
                indApiMap.get("api-ind-2").getId()  // e.g., get user details
        ));
        apiDep1.setDependsOn(dep1Dependencies);

        Map<String, InputMappingDetail> mappings1 = new HashMap<>();
        // Map product ID from api-ind-1 response to a query parameter for /process-order
        mappings1.put("productIdMapping", new InputMappingDetail("api-ind-1", "$.data[0].id", InputTargetType.QUERY_PARAM, "productId"));
        // Map user ID from api-ind-2 response to a request body field for /process-order
        mappings1.put("userIdMapping", new InputMappingDetail("api-ind-2", "$.userId", InputTargetType.REQUEST_BODY_FIELD, "customer.id"));
        // Add a static request body part
        Map<String, Object> requestBodyDep1 = new HashMap<>();
        requestBodyDep1.put("orderData", "processed");
        apiDep1.setRequestBody(requestBodyDep1);
        apiDep1.setInputMappings(mappings1);


        // Dependent API 2: Depends on api-dep-1 (linear dependency)
        // This API will use the 'processingStatus' from 'api-dep-1' as a header.
        API apiDep2 = new API("api-dep-2", "/confirm-payment", API_TYPE.DEPENDENT, HttpMethod.PUT);
        Set<String> dep2Dependencies = new HashSet<>(Collections.singletonList(apiDep1.getId()));
        apiDep2.setDependsOn(dep2Dependencies);
        AuthDetails basicAuth = AuthDetails.basicAuth("admin", "securepass");
        apiDep2.setAuthDetails(basicAuth);

        Map<String, InputMappingDetail> mappings2 = new HashMap<>();
        // Map processingStatus from api-dep-1 response to a custom header
        mappings2.put("processingStatusHeader", new InputMappingDetail("api-dep-1", "$.processingStatus", InputTargetType.HEADER, "X-Processing-Status"));
        apiDep2.setInputMappings(mappings2);


        // Dependent API 3: Depends on api-ind-4 and api-dep-2 (mixed dependencies)
        // This API will use 'inventoryStatus' from 'api-ind-4' as a query param
        // and 'paymentStatus' from 'api-dep-2' as a nested request body field.
        API apiDep3 = new API("api-dep-3", "/finalize-transaction", API_TYPE.DEPENDENT, HttpMethod.PATCH);
        Set<String> dep3Dependencies = new HashSet<>(Arrays.asList(
                indApiMap.get("api-ind-4").getId(), // e.g., update inventory
                apiDep2.getId() // e.g., payment confirmed
        ));
        apiDep3.setDependsOn(dep3Dependencies);
        apiDep3.setHeaders(Collections.singletonMap("Content-Type", MediaType.APPLICATION_JSON_VALUE)); // Ensure content type for PATCH body

        Map<String, InputMappingDetail> mappings3 = new HashMap<>();
        // Map inventoryStatus from api-ind-4 to a query parameter
        mappings3.put("inventoryResultParam", new InputMappingDetail("api-ind-4", "$.inventoryStatus", InputTargetType.QUERY_PARAM, "inventoryResult"));
        // Map paymentStatus from api-dep-2 to a nested request body field
        mappings3.put("paymentStatusBodyField", new InputMappingDetail("api-dep-2", "$.paymentStatus", InputTargetType.REQUEST_BODY_FIELD, "transactionDetails.paymentStatus"));
        // Add a static request body part
        Map<String, Object> requestBodyDep3 = new HashMap<>();
        requestBodyDep3.put("finalizationType", "automatic");
        apiDep3.setRequestBody(requestBodyDep3);
        apiDep3.setInputMappings(mappings3);


        return Arrays.asList(apiDep1, apiDep2, apiDep3);
    }

    /**
     * Creates a combined list of all independent and dependent APIs.
     *
     * @return A list containing all API objects, both independent and dependent.
     */
    public static List<API> createAllApis() {
        List<API> independent = createIndependentApis();
        List<API> dependent = createDependentApis(independent);
        List<API> allApis = new java.util.ArrayList<>();
        allApis.addAll(independent);
        allApis.addAll(dependent);
        return allApis;
    }
}
