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

Configure orchestration behavior in your `application.properties`:

```properties
# Enable detailed logging
nflux.loggingEnabled=true

# Enable verbose error logging
nflux.errorLoggingEnabled=true

# Final output format (e.g., JSON_OBJECT, JSON_ARRAY)
nflux.outputFormat=JSON_OBJECT

# Stop orchestration on first API failure
nflux.errorMode=FAIL_FAST

# Timeout for orchestration in milliseconds
nflux.timeout=30000
```

---

## 🧱 Core Components

### ✅ API POJO

The `API` class represents each API in your orchestration.

#### Example: Independent API (Login API)

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

#### Example: Dependent API (Profile API)

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

### 🔄 InputMappingDetail Explained

| Field | Description |
|-------|-------------|
| `sourceApiId` | ID of the API to extract data from (e.g., `"user-login"`) |
| `sourcePath` | JSONPath expression to fetch value (e.g., `$.user.id`) |
| `targetName` | Placeholder to replace (e.g., `userId`) |
| `inputTargetType` | Type (e.g., `PATH_VARIABLE`, `REQUEST_BODY`, `HEADER`, etc.) |

---

## 🚀 Executing the Orchestration

Use the `NfluxFrameworkHolder` to execute your defined API flow.

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
| 🔗 Input Mapping | Supports JSONPath, Path Variables, Headers, etc. |
| ⏱️ Timeout | Global orchestration timeout control |
| ☠️ Error Modes | `FAIL_FAST`, etc. |
| 📦 Output | Customizable via `nflux.outputFormat` |

---

## 💬 Need Help?

If you run into any issues or have questions, feel free to raise an issue or contact the development team.

---

**Happy Orchestrating!** 🚀
