// File: src/main/java/org/nflux/nfluxframework/utilities/API_DAG.java
package org.nflux.nfluxframework.utilities;

import org.nflux.nfluxframework.pojo.API;

import java.util.*;
import java.util.stream.Collectors;

/**
 * A Directed Acyclic Graph (DAG) utility for managing and ordering API dependencies.
 * This class helps determine a valid execution order for APIs based on their
 * 'dependsOn' relationships, ensuring that all dependencies are met before an API is executed.
 */
public class API_DAG {

    // Adjacency list representation of the graph:
    // Key: API ID
    // Value: Set of API IDs that the key API depends on (its predecessors)
    private final Map<String, Set<String>> adj = new HashMap<>();

    // Map to store the actual API objects by their ID for quick lookup
    private final Map<String, API> apiMap = new HashMap<>();

    /**
     * Adds an API to the DAG.
     *
     * @param api The API object to add.
     * @throws IllegalArgumentException if an API with the same ID already exists.
     */
    public void addApi(API api) {
        if (apiMap.containsKey(api.getId())) {
            throw new IllegalArgumentException("API with ID " + api.getId() + " already exists.");
        }
        // Validate self-dependency at add time
        if (api.getDependsOn() != null && api.getDependsOn().contains(api.getId())) {
            throw new IllegalArgumentException("API '" + api.getId() + "' cannot depend on itself.");
        }
        apiMap.put(api.getId(), api);
        adj.put(api.getId(), api.getDependsOn() != null ? new HashSet<>(api.getDependsOn()) : new HashSet<>());
    }

    /**
     * Returns a topologically sorted list of API IDs, representing a valid execution order.
     * This method uses Kahn's algorithm (based on in-degrees).
     *
     * @return A list of API IDs in execution order.
     * @throws IllegalStateException if a cycle is detected in the dependencies,
     * making a topological sort impossible, or if a dependency points to a non-existent API.
     */
    public List<String> getExecutionOrder() {
        // Calculate in-degrees for all nodes
        Map<String, Integer> inDegree = new HashMap<>();
        for (String apiId : apiMap.keySet()) {
            inDegree.put(apiId, 0); // Initialize all to 0
        }

        // First pass: Populate in-degrees and check for unresolved dependencies
        for (Map.Entry<String, Set<String>> entry : adj.entrySet()) {
            String apiId = entry.getKey();
            Set<String> dependencies = entry.getValue();
            for (String depId : dependencies) {
                // If a dependency is not in apiMap, it's an unresolvable dependency.
                if (!apiMap.containsKey(depId)) {
                    throw new IllegalStateException("Unresolved dependency: API '" + apiId + "' depends on non-existent API '" + depId + "'");
                }
                // Increment the in-degree of the dependent API (apiId depends on depId)
                inDegree.put(apiId, inDegree.get(apiId) + 1);
            }
        }

        // Queue for nodes with in-degree 0 (ready to be executed)
        Queue<String> q = inDegree.entrySet().stream()
                .filter(entry -> entry.getValue() == 0)
                .map(Map.Entry::getKey)
                .collect(Collectors.toCollection(LinkedList::new));

        List<String> topologicalOrder = new ArrayList<>();
        int visitedNodes = 0;

        while (!q.isEmpty()) {
            String currentApiId = q.poll();
            topologicalOrder.add(currentApiId);
            visitedNodes++;

            // Find APIs that depend on currentApiId and decrement their in-degree
            for (String dependentApiId : apiMap.keySet()) {
                // Check if dependentApiId exists in adj and depends on currentApiId
                if (adj.containsKey(dependentApiId) && adj.get(dependentApiId).contains(currentApiId)) {
                    inDegree.put(dependentApiId, inDegree.get(dependentApiId) - 1);
                    if (inDegree.get(dependentApiId) == 0) {
                        q.add(dependentApiId);
                    }
                }
            }
        }

        // If visitedNodes count is less than total APIs, a cycle exists
        if (visitedNodes != apiMap.size()) {
            Set<String> remainingNodes = inDegree.entrySet().stream()
                    .filter(entry -> entry.getValue() > 0)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toSet());
            throw new IllegalStateException("Cycle detected in API dependencies! Cannot determine a valid execution order. Remaining unresolvable APIs: " + remainingNodes);
        }

        return topologicalOrder;
    }

    /**
     * Returns the number of APIs currently in the DAG.
     *
     * @return The size of the API map.
     */
    public int size() {
        return apiMap.size();
    }

    /**
     * Retrieves an API object by its ID.
     *
     * @param apiId The ID of the API to retrieve.
     * @return The API object, or null if not found.
     */
    public API getApi(String apiId) {
        return apiMap.get(apiId);
    }

    /**
     * Clears all APIs and dependencies from the DAG.
     * Useful for resetting the DAG between tests or orchestration runs.
     */
    public void clear() {
        adj.clear();
        apiMap.clear();
    }
}
