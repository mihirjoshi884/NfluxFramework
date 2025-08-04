package org.nflux.nfluxframework.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.nflux.nfluxframework.enums.InputTargetType;

@Data
@NoArgsConstructor
@AllArgsConstructor
public  class InputMappingDetail {
    // The ID of the API whose result contains the source data
    private String sourceApiId;
    // A JSONPath-like string to extract the value from the source API's response
    // e.g., "$.orderId", "$.data[0].id", "$.user.profile.name"
    private String sourceJsonPath;
    // The type of target where the extracted value should be injected
    private InputTargetType targetType;
    // The name of the target field (e.g., "orderId" for path/query, or "$.newField" for request body)
    private String targetField;
}
