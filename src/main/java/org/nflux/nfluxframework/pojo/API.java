// File: src/main/java/org/nflux/nfluxframework/pojo/API.java
package org.nflux.nfluxframework.pojo;

import lombok.Getter; // Import Lombok's Getter annotation
import lombok.Setter; // Import Lombok's Setter annotation
import lombok.ToString; // Import Lombok's ToString annotation

import org.nflux.nfluxframework.enums.API_TYPE;
import org.springframework.http.HttpMethod;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID; // For auto-generating unique IDs

/**
 * Represents an API definition, including its ID, URL, type, HTTP method,
 * authentication details, dependencies, headers, query parameters, and request body.
 *
 * Uses Lombok annotations for automatic generation of getters, setters, and toString.
 *
 * Added inputMappings to define how data from previous API results
 * should be used as input for this API.
 */
@Getter // Automatically generates public getters for all fields
@Setter // Automatically generates public setters for all non-final fields
@ToString // Automatically generates a toString() method
public class API {

    // Unique identifier for the API. Made final to ensure immutability once set.
    private final String id;
    private final String url;
    private final API_TYPE type;
    private final HttpMethod method;


    // Optional fields with default null or empty collection initializations
    private AuthDetails authDetails;
    private Set<String> dependsOn; // IDs of APIs this API depends on
    // Defines how input from previous API results maps to this API's request
    private Map<String, InputMappingDetail> inputMappings; // Changed from List to Map for easier lookup by a key (e.g., mapping name)
    private Map<String, String> headers;
    private Map<String, Object> queryParams;
    private Map<String, Object> requestBody;

    /**
     * Constructor for creating an API object with an auto-generated unique ID (UUID).
     * Initializes collections to empty, non-null maps/sets.
     *
     * @param url The URL endpoint of the API.
     * @param type The type of API (e.g., INDEPENDENT, DEPENDENT).
     * @param method The HTTP method (e.g., GET, POST, PUT, DELETE).
     */
    public API(String url, API_TYPE type, HttpMethod method) {
        // Calls the more comprehensive constructor with a new UUID for the ID
        this(UUID.randomUUID().toString(), url, type, method);
    }

    /**
     * Constructor for creating an API object with a specified ID.
     * Initializes collections to empty, non-null maps/sets.
     *
     * @param id A unique identifier for this API.
     * @param url The URL endpoint of the API.
     * @param type The type of API (e.g., INDEPENDENT, DEPENDENT).
     * @param method The HTTP method (e.g., GET, POST, PUT, DELETE).
     */
    public API(String id, String url, API_TYPE type, HttpMethod method) {
        this.id = id;
        this.url = url;
        this.type = type;
        this.method = method;
        // Initialize collections to empty but non-null to avoid NullPointerExceptions later
        this.dependsOn = new HashSet<>();
        this.headers = new HashMap<>();
        this.queryParams = new HashMap<>();
        this.requestBody = new HashMap<>();
        this.inputMappings = new HashMap<>(); // Initialize new field
    }

    /**
     * Comprehensive constructor for creating an API object with all possible fields.
     * This constructor allows for full control over all API properties at creation.
     * It ensures that collection fields are never null, initializing them as empty
     * if the provided arguments are null.
     *
     * @param id A unique identifier for this API.
     * @param url The URL endpoint of the API.
     * @param type The type of API (e.g., INDEPENDENT, DEPENDENT).
     * @param method The HTTP method (e.g., GET, POST, PUT, DELETE).
     * @param authDetails Authentication details for the API.
     * @param dependsOn A set of API IDs that this API depends on.
     * @param headers HTTP headers to be sent with the request.
     * @param queryParams Query parameters for the URL.
     * @param requestBody The request body for POST/PUT requests.
     * @param inputMappings Defines how data from previous API results maps to this API's request.
     */
    public API(String id, String url, API_TYPE type, HttpMethod method, AuthDetails authDetails, Set<String> dependsOn, Map<String, String> headers, Map<String, Object> queryParams, Map<String, Object> requestBody, Map<String, InputMappingDetail> inputMappings) {
        this.id = id;
        this.url = url;
        this.type = type;
        this.method = method;
        this.authDetails = authDetails;
        // Ensure collections are non-null and mutable copies
        this.dependsOn = dependsOn != null ? new HashSet<>(dependsOn) : new HashSet<>();
        this.headers = headers != null ? new HashMap<>(headers) : new HashMap<>();
        this.queryParams = queryParams != null ? new HashMap<>(queryParams) : new HashMap<>();
        this.requestBody = requestBody != null ? new HashMap<>(requestBody) : new HashMap<>();
        this.inputMappings = inputMappings != null ? new HashMap<>(inputMappings) : new HashMap<>(); // Initialize new field
    }


}
