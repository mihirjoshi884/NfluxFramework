package org.nflux.nfluxframework.utilitesTest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.nflux.nfluxframework.enums.API_TYPE;
import org.nflux.nfluxframework.pojo.API;
import org.nflux.nfluxframework.utilities.API_DAG;
import org.springframework.http.HttpMethod;

import java.util.Collections;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class API_DAG_Test {
    private API_DAG apiDag;

    /**
     * Set up a new API_DAG instance before each test to ensure isolation.
     */
    @BeforeEach
    void setUp() {
        apiDag = new API_DAG();
    }

    /**
     * Test case for adding independent APIs and verifying their order.
     * Independent APIs should appear first in the topological sort.
     * Uses the API constructor where ID is provided.
     */
    @Test
    void testAddIndependentApis() {
        API api1 = new API("api1-id", "url1", API_TYPE.INDEPENDENT, HttpMethod.GET);
        API api2 = new API("api2-id", "url2", API_TYPE.INDEPENDENT, HttpMethod.POST);

        apiDag.addApi(api1);
        apiDag.addApi(api2);

        assertEquals(2, apiDag.size(), "DAG should contain 2 APIs.");

        List<String> order = apiDag.getExecutionOrder();
        // For independent APIs, the order can vary, but both should be present.
        assertTrue(order.contains(api1.getId()));
        assertTrue(order.contains(api2.getId()));
        assertEquals(2, order.size());
    }

    /**
     * Test case for adding APIs with simple linear dependencies.
     * api2 depends on api1. Expected order: api1, api2.
     * Uses the API constructor where ID is provided.
     */
    @Test
    void testAddLinearDependencies() {
        API api1 = new API("api1-id", "api1-url", API_TYPE.INDEPENDENT, HttpMethod.GET);
        API api2 = new API("api2-id", "api2-url", API_TYPE.DEPENDENT, HttpMethod.POST);

        // Set dependency using setter (Lombok provides this)
        api2.setDependsOn(new HashSet<>(Collections.singletonList(api1.getId())));

        apiDag.addApi(api1);
        apiDag.addApi(api2);

        assertEquals(2, apiDag.size(), "DAG should contain 2 APIs.");

        List<String> order = apiDag.getExecutionOrder();
        assertEquals(2, order.size());
        assertEquals(api1.getId(), order.get(0), "API1 should be executed before API2.");
        assertEquals(api2.getId(), order.get(1), "API2 should be executed after API1.");
    }

    /**
     * Test case for adding APIs with multiple dependencies.
     * api3 depends on api1 and api2. Expected order: api1, api2, api3 (or api2, api1, api3).
     * Uses the API constructor where ID is provided.
     */
    @Test
    void testAddMultipleDependencies() {
        API api1 = new API("api1-id", "api1-url", API_TYPE.INDEPENDENT, HttpMethod.GET);
        API api2 = new API("api2-id", "api2-url", API_TYPE.INDEPENDENT, HttpMethod.POST);
        API api3 = new API("api3-id", "api3-url", API_TYPE.DEPENDENT, HttpMethod.PUT);

        Set<String> api3Deps = new HashSet<>(Arrays.asList(api1.getId(), api2.getId()));
        api3.setDependsOn(api3Deps); // Set dependencies using setter

        apiDag.addApi(api1);
        apiDag.addApi(api2);
        apiDag.addApi(api3);

        assertEquals(3, apiDag.size(), "DAG should contain 3 APIs.");

        List<String> order = apiDag.getExecutionOrder();
        assertEquals(3, order.size());

        // Verify that api3 is always last
        assertEquals(api3.getId(), order.get(2), "API3 should be executed last.");

        // Verify that api1 and api2 are before api3
        assertTrue(order.indexOf(api1.getId()) < order.indexOf(api3.getId()));
        assertTrue(order.indexOf(api2.getId()) < order.indexOf(api3.getId()));
    }

    /**
     * Test case for detecting a direct cycle in API dependencies.
     * api1 depends on api2, and api2 depends on api1.
     * Uses the API constructor where ID is provided.
     */
    @Test
    void testCycleDetectionDirect() {
        API api1 = new API("api1-cycle-id", "api1-url", API_TYPE.DEPENDENT, HttpMethod.GET);
        API api2 = new API("api2-cycle-id", "api2-url", API_TYPE.DEPENDENT, HttpMethod.POST);

        // Create a cycle: api1 depends on api2, api2 depends on api1
        Set<String> api1Deps = new HashSet<>(Collections.singletonList(api2.getId()));
        Set<String> api2Deps = new HashSet<>(Collections.singletonList(api1.getId()));

        api1.setDependsOn(api1Deps);
        api2.setDependsOn(api2Deps);

        apiDag.addApi(api1);
        apiDag.addApi(api2);

        IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> {
            apiDag.getExecutionOrder();
        }, "Should throw IllegalStateException for a cycle.");

        assertTrue(thrown.getMessage().contains("Cycle detected in API dependencies!"), "Error message should indicate a cycle.");
    }

    /**
     * Test case for detecting a more complex cycle in API dependencies.
     * api1 -> api2 -> api3 -> api1
     * Uses the API constructor where ID is provided.
     */
    @Test
    void testCycleDetectionComplex() {
        API api1 = new API("api1-complex-id", "api1-url", API_TYPE.DEPENDENT, HttpMethod.GET);
        API api2 = new API("api2-complex-id", "api2-url", API_TYPE.DEPENDENT, HttpMethod.POST);
        API api3 = new API("api3-complex-id", "api3-url", API_TYPE.DEPENDENT, HttpMethod.PUT);

        api1.setDependsOn(new HashSet<>(Collections.singletonList(api3.getId()))); // api1 depends on api3
        api2.setDependsOn(new HashSet<>(Collections.singletonList(api1.getId()))); // api2 depends on api1
        api3.setDependsOn(new HashSet<>(Collections.singletonList(api2.getId()))); // api3 depends on api2

        apiDag.addApi(api1);
        apiDag.addApi(api2);
        apiDag.addApi(api3);

        IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> {
            apiDag.getExecutionOrder();
        }, "Should throw IllegalStateException for a cycle.");

        assertTrue(thrown.getMessage().contains("Cycle detected in API dependencies!"), "Error message should indicate a cycle.");
    }

    /**
     * Test case for adding an API with a duplicate ID.
     * Uses the API constructor where ID is provided.
     */
    @Test
    void testAddDuplicateApiId() {
        API api1 = new API("testId", "url1", API_TYPE.INDEPENDENT, HttpMethod.GET);
        API api1Duplicate = new API("testId", "url2", API_TYPE.INDEPENDENT, HttpMethod.POST); // Same ID

        apiDag.addApi(api1);

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> {
            apiDag.addApi(api1Duplicate);
        }, "Should throw IllegalArgumentException for duplicate API ID.");

        assertTrue(thrown.getMessage().contains("already exists in the DAG."), "Error message should indicate duplicate ID.");
    }

    /**
     * Test case for adding an API that depends on itself.
     * Uses the API constructor where ID is provided.
     */
    @Test
    void testApiDependsOnItself() {
        API api1 = new API("selfDepApi", "url1", API_TYPE.DEPENDENT, HttpMethod.GET);
        api1.setDependsOn(new HashSet<>(Collections.singletonList(api1.getId()))); // Depends on itself

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> {
            apiDag.addApi(api1);
        }, "Should throw IllegalArgumentException if API depends on itself.");

        assertTrue(thrown.getMessage().contains("cannot depend on itself."), "Error message should indicate self-dependency.");
    }

    /**
     * Test clearing the DAG.
     */
    @Test
    void testClearDag() {
        API api1 = new API("url1", API_TYPE.INDEPENDENT, HttpMethod.GET);
        apiDag.addApi(api1);
        assertEquals(1, apiDag.size());

        apiDag.clear();
        assertEquals(0, apiDag.size(), "DAG should be empty after clearing.");
        assertTrue(apiDag.getExecutionOrder().isEmpty(), "Execution order should be empty after clearing.");
    }

    /**
     * Test retrieving an API by ID.
     * Uses the API constructor where ID is provided.
     */
    @Test
    void testGetApi() {
        API api1 = new API("test-api-id", "url1", API_TYPE.INDEPENDENT, HttpMethod.GET);
        apiDag.addApi(api1);

        API retrievedApi = apiDag.getApi("test-api-id");
        assertNotNull(retrievedApi);
        assertEquals(api1.getId(), retrievedApi.getId());
        assertEquals(api1.getUrl(), retrievedApi.getUrl());

        assertNull(apiDag.getApi("non-existent-api"), "Should return null for non-existent API.");
    }

    /**
     * Test case for creating an API object where the ID is auto-generated using UUID.
     * Verifies that the ID is not null, has a UUID format, and other fields are correctly initialized.
     * Uses the API constructor where ID is NOT provided.
     */
    @Test
    void testApiCreationWithAutoGeneratedId() {
        String url = "auto-gen-id-url";
        API_TYPE type = API_TYPE.INDEPENDENT;
        HttpMethod method = HttpMethod.GET;

        // Use the constructor that auto-generates the ID
        API api = new API(url, type, method);

        assertNotNull(api.getId(), "API ID should not be null when auto-generated.");
        // A simple check for UUID format: 36 characters long (32 hex digits + 4 hyphens)
        assertEquals(36, api.getId().length(), "Auto-generated ID should be a UUID of 36 characters.");
        // More rigorous regex check for UUID format (e.g., "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx")
        assertTrue(api.getId().matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"),
                "Auto-generated ID should match UUID format.");

        assertEquals(url, api.getUrl());
        assertEquals(type, api.getType());
        assertEquals(method, api.getMethod());

        // Ensure default empty collections for optional fields
        assertTrue(api.getDependsOn().isEmpty(), "dependsOn should be an empty set by default.");
        assertTrue(api.getHeaders().isEmpty(), "headers should be an empty map by default.");
        assertTrue(api.getQueryParams().isEmpty(), "queryParams should be an empty map by default.");
        assertTrue(api.getRequestBody().isEmpty(), "requestBody should be an empty map by default.");
        assertNull(api.getAuthDetails(), "authDetails should be null by default.");
    }
}
