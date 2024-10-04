package org.nFlux.utils;

import org.nFlux.enums.API_Type;
import org.nFlux.enums.AUTHWAYS;
import org.nFlux.pojo.API;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.*;

@Component
public class ApiUtility {

    private List<API> semiIndependentAPIs = new ArrayList<>();
    private List<API> independentAPIs = new ArrayList<>();
    private List<API> dependableAPIs = new ArrayList<>();
    private List<API> originalAPIList = new ArrayList<>();
    private Map<Integer, Integer> indexMapping = new HashMap<>();

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

    public void executeGetIndependentAPIWithNoAuth(List<API> executableAPIs) {
        segregateAPIs(executableAPIs);
        WebClient webClient = WebClient.builder().build();

        independentAPIs.forEach(api -> {
            WebClient.RequestHeadersSpec<?> requestSpec = (WebClient.RequestHeadersSpec<?>) webClient.get()
                    .uri(api.getUrl())
                    .retrieve()
                    .bodyToMono(String.class)
                    .subscribe(System.out::println);
        });
    }

    public void executeGetIndependentAPIWithBearerToken(List<API> executableAPIs) {
        segregateAPIs(executableAPIs);
        WebClient webClient = WebClient.builder().build();

        independentAPIs.forEach(api -> {
            WebClient.RequestHeadersSpec<?> requestSpec = (WebClient.RequestHeadersSpec<?>) webClient.get()
                    .uri(api.getUrl())
                    .header("Authorization", "Bearer " + api.getAuth().get(AUTHWAYS.BEARER_TOKEN).getBearerToken())
                    .retrieve()
                    .bodyToMono(String.class)
                    .subscribe(System.out::println);
        });
    }

    public void executeGetIndependentAPIWithBasicAuth(List<API> executableAPIs) {
        segregateAPIs(executableAPIs);
        WebClient webClient = WebClient.builder().build();

        independentAPIs.forEach(api -> {
            String basicAuthHeader = "Basic " + Base64.getEncoder().encodeToString(
                    (api.getAuth().get(AUTHWAYS.BASIC_AUTH).getBasicUsername()
                            + ":" + api.getAuth().get(AUTHWAYS.BASIC_AUTH).getBasicPassword())
                            .getBytes()
            );
            WebClient.RequestHeadersSpec<?> requestSpec = (WebClient.RequestHeadersSpec<?>) webClient.get()
                    .uri(api.getUrl())
                    .header("Authorization", basicAuthHeader)
                    .retrieve()
                    .bodyToMono(String.class)
                    .subscribe(System.out::println);
        });
    }

    public void executeGetIndependentAPIWithClientIdSecret(List<API> executableAPIs) {
        segregateAPIs(executableAPIs);
        WebClient webClient = WebClient.builder().build();

        independentAPIs.forEach(api -> {
            WebClient.RequestHeadersSpec<?> requestSpec = (WebClient.RequestHeadersSpec<?>) webClient.get()
                    .uri(api.getUrl())
                    .header("Client-Id", api.getAuth().get(AUTHWAYS.CLIENT_ID_SECRET).getClientId())
                    .header("Client-Secret", api.getAuth().get(AUTHWAYS.CLIENT_ID_SECRET).getClientSecret())
                    .retrieve()
                    .bodyToMono(String.class)
                    .subscribe(System.out::println);
        });
    }

    public void executeGetIndependentAPIWithApiKey(List<API> executableAPIs) {
        segregateAPIs(executableAPIs);
        WebClient webClient = WebClient.builder().build();

        independentAPIs.forEach(api -> {
            WebClient.RequestHeadersSpec<?> requestSpec = (WebClient.RequestHeadersSpec<?>) webClient.get()
                    .uri(api.getUrl())
                    .header("x-api-key", api.getAuth().get(AUTHWAYS.API_KEY).getApiKey())
                    .retrieve()
                    .bodyToMono(String.class)
                    .subscribe(System.out::println);
        });
    }

    public void executeGetIndependentAPIWithQueryParameter(List<API> executableAPIs) {
        segregateAPIs(executableAPIs);
        WebClient webClient = WebClient.builder().build();

        independentAPIs.forEach(api -> {
            WebClient.RequestHeadersSpec<?> requestSpec = (WebClient.RequestHeadersSpec<?>) webClient.get()
                    .uri(uriBuilder -> uriBuilder.path(api.getUrl())
                            .queryParam(api.getAuth().get(AUTHWAYS.QUERY_PARAMETER).getQueryParameterName(),
                                    api.getAuth().get(AUTHWAYS.QUERY_PARAMETER).getQueryParameterValue())
                            .build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .subscribe(System.out::println);
        });
    }

    public void executeGetIndependentAPIWithCustomHeader(List<API> executableAPIs) {
        segregateAPIs(executableAPIs);
        WebClient webClient = WebClient.builder().build();

        independentAPIs.forEach(api -> {
            WebClient.RequestHeadersSpec<?> requestSpec = (WebClient.RequestHeadersSpec<?>) webClient.get()
                    .uri(api.getUrl())
                    .header(api.getAuth().get(AUTHWAYS.CUSTOM_HEADER).getCustomHeaderName(),
                            api.getAuth().get(AUTHWAYS.CUSTOM_HEADER).getCustomHeaderValue())
                    .retrieve()
                    .bodyToMono(String.class)
                    .subscribe(System.out::println);
        });
    }
}
