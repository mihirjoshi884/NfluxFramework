package org.nFlux.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.nFlux.enums.API_Type;
import org.nFlux.enums.AUTHWAYS;
import org.nFlux.enums.RESPONSE;
import org.nFlux.pojo.API;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import org.nFlux.enums.RESPONSE.*;

@Component
public class ApiUtility {

    private List<API> semiIndependentAPIs = new ArrayList<>();
    private List<API> independentAPIs = new ArrayList<>();
    private List<API> dependableAPIs = new ArrayList<>();
    private List<API> originalAPIList = new ArrayList<>();
    private Map<Integer, Mono<JsonNode>> responseMap = new ConcurrentHashMap<>();
    private ObjectMapper objectMapper = new ObjectMapper();

    private void segregateAPIs(List<API> executableAPIs) {
        originalAPIList = executableAPIs;
        executableAPIs.forEach(api -> {
            if (api.getApi_type() == API_Type.DEPENDENT) {
                semiIndependentAPIs.add(api.getDependencyIndex(), executableAPIs.get(api.getDependencyIndex()));
                dependableAPIs.add(api);
            } else if (api.getApi_type() == API_Type.INDEPENDENT) {
                independentAPIs.add(api);
            }
        });
    }

    public Mono<String> executeGetIndependentAPIWithNoAuth(List<API> executableAPIs) {
        segregateAPIs(executableAPIs);
        WebClient webClient = WebClient.builder().build();

        return independentAPIs.stream()
                .map(api -> webClient.get()
                        .uri(api.getUrl())
                        .retrieve()
                        .bodyToMono(String.class))
                .findFirst()
                .orElse(Mono.empty());
    }

    public Mono<String> executeGetIndependentAPIWithBearerToken(List<API> executableAPIs) {
        segregateAPIs(executableAPIs);
        WebClient webClient = WebClient.builder().build();

        return independentAPIs.stream()
                .map(api -> webClient.get()
                        .uri(api.getUrl())
                        .header("Authorization", "Bearer " + api.getAuth().get(AUTHWAYS.BEARER_TOKEN).getBearerToken())
                        .retrieve()
                        .bodyToMono(String.class))
                .findFirst()
                .orElse(Mono.empty());
    }

    public Mono<String> executeGetIndependentAPIWithBasicAuth(List<API> executableAPIs) {
        segregateAPIs(executableAPIs);
        WebClient webClient = WebClient.builder().build();

        return independentAPIs.stream()
                .map(api -> {
                    String basicAuthHeader = "Basic " + Base64.getEncoder().encodeToString(
                            (api.getAuth().get(AUTHWAYS.BASIC_AUTH).getBasicUsername()
                                    + ":" + api.getAuth().get(AUTHWAYS.BASIC_AUTH).getBasicPassword())
                                    .getBytes()
                    );
                    return webClient.get()
                            .uri(api.getUrl())
                            .header("Authorization", basicAuthHeader)
                            .retrieve()
                            .bodyToMono(String.class);
                })
                .findFirst()
                .orElse(Mono.empty());
    }

    public Mono<String> executeGetIndependentAPIWithClientIdSecret(List<API> executableAPIs) {
        segregateAPIs(executableAPIs);
        WebClient webClient = WebClient.builder().build();

        return independentAPIs.stream()
                .map(api -> webClient.get()
                        .uri(api.getUrl())
                        .header("Client-Id", api.getAuth().get(AUTHWAYS.CLIENT_ID_SECRET).getClientId())
                        .header("Client-Secret", api.getAuth().get(AUTHWAYS.CLIENT_ID_SECRET).getClientSecret())
                        .retrieve()
                        .bodyToMono(String.class))
                .findFirst()
                .orElse(Mono.empty());
    }

    public Mono<String> executeGetIndependentAPIWithApiKey(List<API> executableAPIs) {
        segregateAPIs(executableAPIs);
        WebClient webClient = WebClient.builder().build();

        return independentAPIs.stream()
                .map(api -> webClient.get()
                        .uri(api.getUrl())
                        .header("x-api-key", api.getAuth().get(AUTHWAYS.API_KEY).getApiKey())
                        .retrieve()
                        .bodyToMono(String.class))
                .findFirst()
                .orElse(Mono.empty());
    }

    public Mono<String> executeGetIndependentAPIWithQueryParameter(List<API> executableAPIs) {
        segregateAPIs(executableAPIs);
        WebClient webClient = WebClient.builder().build();

        return independentAPIs.stream()
                .map(api -> webClient.get()
                        .uri(uriBuilder -> uriBuilder.path(api.getUrl())
                                .queryParam(api.getAuth().get(AUTHWAYS.QUERY_PARAMETER).getQueryParameterName(),
                                        api.getAuth().get(AUTHWAYS.QUERY_PARAMETER).getQueryParameterValue())
                                .build())
                        .retrieve()
                        .bodyToMono(String.class))
                .findFirst()
                .orElse(Mono.empty());
    }

    public Mono<String> executeGetIndependentAPIWithCustomHeader(List<API> executableAPIs) {
        segregateAPIs(executableAPIs);
        WebClient webClient = WebClient.builder().build();

        return independentAPIs.stream()
                .map(api -> webClient.get()
                        .uri(api.getUrl())
                        .header(api.getAuth().get(AUTHWAYS.CUSTOM_HEADER).getCustomHeaderName(),
                                api.getAuth().get(AUTHWAYS.CUSTOM_HEADER).getCustomHeaderValue())
                        .retrieve()
                        .bodyToMono(String.class))
                .findFirst()
                .orElse(Mono.empty());
    }

    public Mono<Void> executeAPIs(List<API> executableAPIs) {
        segregateAPIs(executableAPIs);
        return executeSemiIndependentAPIs().then(Mono.defer(this::executeDependableAPIs));
    }

    private Mono<Void> executeSemiIndependentAPIs() {
        WebClient webClient = WebClient.builder().build();

        List<Mono<JsonNode>> semiIndependentApiMonos = new ArrayList<>();
        semiIndependentAPIs.forEach(api -> {
            Mono<JsonNode> apiMono = webClient.get()
                    .uri(api.getUrl())
                    .header(api.getAuth().get(AUTHWAYS.CUSTOM_HEADER).getCustomHeaderName(),
                            api.getAuth().get(AUTHWAYS.CUSTOM_HEADER).getCustomHeaderValue())
                    .retrieve()
                    .bodyToMono(String.class)
                    .flatMap(response -> {
                        try {
                            JsonNode jsonNode = objectMapper.readTree(response);
                            responseMap.put(api.getDependencyIndex(), Mono.just(jsonNode));
                            return Mono.just(jsonNode);
                        } catch (Exception e) {
                            return Mono.error(e);
                        }
                    });

            semiIndependentApiMonos.add(apiMono);
        });

        return Flux.merge(semiIndependentApiMonos).then();
    }

    private Mono<Void> executeDependableAPIs() {
        WebClient webClient = WebClient.builder().build();

        List<Mono<Void>> dependentApiMonos = new ArrayList<>();
        dependableAPIs.forEach(api -> {
            Mono<JsonNode> dependencyResponseMono = responseMap.get(api.getDependencyIndex());

            Mono<Void> apiMono = dependencyResponseMono.flatMap(dependencyResponse -> {
                String extractedValue = extractValueFromJson(dependencyResponse, api.getResponseField());

                WebClient.RequestHeadersUriSpec<?> requestSpec = (WebClient.RequestHeadersUriSpec<?>) webClient.get()
                        .uri(api.getUrl());

                switch (api.getResponseWay()) {
                    case HEADER:
                        requestSpec.header(api.getResponseField(), extractedValue);
                        break;
                    case PARAM_QUERY:
                        requestSpec.uri(uriBuilder -> uriBuilder
                                .path(api.getUrl())
                                .queryParam(api.getResponseField(), extractedValue)
                                .build());
                        break;
                    case REQUEST_BODY:
                        // If the request requires a body, you may need to change the request method to POST or PUT, etc.
                        // Example for POST (modify as needed):
                        return webClient.post()
                                .uri(api.getUrl())
                                .bodyValue(extractedValue) // Use the extracted value as the body
                                .retrieve()
                                .bodyToMono(String.class)
                                .then();
                    case PATH_VARIABLE:
                        // Here, you might construct a URI that includes the extracted value in the path.
                        String urlWithPathVariable = api.getUrl().replace("{variable}", extractedValue); // Assuming your URL has a placeholder
                        requestSpec.uri(urlWithPathVariable);
                        break;
                    default:
                        // Handle any unexpected response way
                        return Mono.error(new IllegalArgumentException("Unsupported response way: " + api.getResponseWay()));
                }

                return requestSpec.retrieve().bodyToMono(String.class).then();
            });

            dependentApiMonos.add(apiMono);
        });

        return Flux.merge(dependentApiMonos).then();
    }


    private String extractValueFromJson(JsonNode jsonNode, String responseField) {
        return jsonNode.path(responseField).asText();
    }
}
