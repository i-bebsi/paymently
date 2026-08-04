package com.uii.paymently.dto;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BillInquiryResponse {

    private String responseCode;
    private String responseMessage;

    private Map<String, Object> additionalFields = new LinkedHashMap<>();

    @JsonAnySetter
    public void setAdditionalField(String key, Object value) {
        if (!"responseCode".equals(key) && !"responseMessage".equals(key)) {
            this.additionalFields.put(key, value);
        }
    }

    @JsonAnyGetter
    public Map<String, Object> getAdditionalFields() {
        return additionalFields;
    }
}