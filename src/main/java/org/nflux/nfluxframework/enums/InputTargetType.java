package org.nflux.nfluxframework.enums;

/**
 * Enum to specify the type of target for input mapping.
 * This indicates where an extracted value from a source API response
 * should be injected into the target API's request.
 */
public enum InputTargetType {
    PATH_VARIABLE,
    QUERY_PARAM,
    REQUEST_BODY_FIELD, // Indicates a field within the JSON request body
    HEADER, // Indicates a request header
    AUTH_DETAIL_FIELD // NEW: Indicates a field within the AuthDetails object (e.g., 'token')
}
