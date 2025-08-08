# Nflux Framework Developer's Guide

Welcome to the **Nflux Framework** — a lightweight, annotation-driven API orchestration framework built to work seamlessly with Spring Boot. This guide will walk you through everything from configuration to execution so you can design complex, dependency-based API workflows effortlessly.

---

## 🏁 Getting Started

### 1. Enable the Framework

In your main Spring Boot application class, use the `@EnableNfluxFramework` annotation:

```java
@SpringBootApplication
@EnableNfluxFramework
public class DemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
}
```

---

## ⚙️ Configuration Properties

The `application.properties` file is where you can customize the framework's behavior. Understanding these properties allows you to fine-tune your orchestration.

| Property | Description | Example |
|----------|-------------|---------|
| `nflux.loggingEnabled` | Enables detailed logs of the orchestration's flow, including which APIs are running and what their responses are. | `nflux.loggingEnabled=true` |
| `nflux.errorLoggingEnabled` | Enables verbose stack traces and detailed error messages when an API call fails. | `nflux.errorLoggingEnabled=true` |
| `nflux.outputFormat` | Specifies the structure of the final output. `JSON_OBJECT` returns a map of API IDs to their responses. | `nflux.outputFormat=JSON_OBJECT` |
| `nflux.errorMode` | Controls behavior on failure. `FAIL_FAST` stops immediately, `CONTINUE` proceeds with independent APIs. | `nflux.errorMode=FAIL_FAST` |
| `nflux.timeout` | Global timeout for the entire orchestration in milliseconds. | `nflux.timeout=30000` |

---

## 🧱 Core Components

### ✅ The API POJO

The `API` class is the blueprint for every API call in your orchestration.

#### Constructor Parameters:

1. `apiId (String)` – Unique ID for the API.
2. `url (String)` – Full URL, including placeholders like `{userId}`.
3. `apiType (API_TYPE)` – Either `INDEPENDENT` or `DEPENDENT`.
4. `httpMethod (HttpMethod)` – HTTP method like GET, POST, etc.
5. `authDetails (AuthDetails)` – How to authenticate this API.
6. `dependsOn (Set<String>)` – List of dependent API IDs.
7. `headers (Map<String, String>)` – Custom headers.
8. `queryParams (Map<String, String>)` – Query parameters.
9. `requestBody (Map<String, Object>)` – Body for POST/PUT.
10. `inputMappings (List<InputMappingDetail>)` – Data linking from other APIs.

---

### 🟢 Example: Independent API (Login API)

```java
@Bean
public API loginApi() {
    Map<String, Object> requestBody = Map.of(
        "username", "john.doe",
        "password", "secretpassword"
    );

    return new API(
        "user-login",
        "http://localhost:8081/mock/api/v1/auth/login",
        API_TYPE.INDEPENDENT,
        HttpMethod.POST,
        null, null, null, null, requestBody, null
    );
}
```

---

### 🔵 Example: Dependent API (Profile API)

```java
@Bean
public API getProfileApi() {
    AuthDetails authDetails = new AuthDetails(
        AUTH_WAYS.BEARER_TOKEN,
        "user-login",
        "$.token"
    );

    List<InputMappingDetail> inputMappings = List.of(
        new InputMappingDetail(
            "user-login",
            "$.user.id",
            "userId",
            InputTargetType.PATH_VARIABLE
        )
    );

    return new API(
        "user-profile",
        "http://localhost:8081/mock/api/v1/users/{userId}/profile",
        API_TYPE.DEPENDENT,
        HttpMethod.GET,
        authDetails,
        Set.of("user-login"),
        null, null, null,
        inputMappings
    );
}
```

---

## 🔄 InputMappingDetail Explained

| Field | Description |
|-------|-------------|
| `sourceApiId` | ID of the source API |
| `sourcePath` | JSONPath to extract value |
| `targetName` | Placeholder name (e.g., userId) |
| `inputTargetType` | Where to put value: PATH_VARIABLE, HEADER, REQUEST_BODY, etc. |

---

## 🚀 Executing the Orchestration

Use the `NfluxFrameworkHolder` to trigger execution.

```java
@PostConstruct
public void runOrchestration() {
    try {
        NfluxFramework nfluxFramework = NfluxFrameworkHolder.getInstance();
        Mono<Map<String, JsonNode>> resultsMono = nfluxFramework.executeOrchestration();

        resultsMono.subscribe(
            finalResultMap -> {
                System.out.println("\n--- Orchestration complete. Final results: ---");
                finalResultMap.forEach((apiId, response) ->
                    System.out.println("API ID: " + apiId + "\nResponse: " + response.toPrettyString() + "\n")
                );
            },
            error -> {
                System.err.println("\n--- Orchestration failed with error: ---");
                System.err.println("Error Message: " + error.getMessage());
            }
        );
    } catch (Exception e) {
        System.err.println("Setup Error: " + e.getMessage());
        e.printStackTrace();
    }
}
```

---

## 📌 Summary

| Feature | Description |
|--------|-------------|
| 🔧 Annotation | `@EnableNfluxFramework` |
| 📁 Config File | `application.properties` |
| 🧠 API Type | `INDEPENDENT`, `DEPENDENT` |
| 🔗 Input Mapping | `InputMappingDetail` with JSONPath |
| ⏱️ Timeout | Controlled via `nflux.timeout` |
| ☠️ Error Modes | `FAIL_FAST`, `CONTINUE` |
| 📦 Output | Controlled via `nflux.outputFormat` |

---

## 💬 Need Help?

If you run into any issues or have questions, feel free to raise an issue or contact the development team.

---

**Happy Orchestrating!** 🚀
