package com.uii.paymently.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ReverseRequest {
    private String partnerServiceId;
    private String customerNo;
    private String virtualAccountNo;
    private String inquiryRequestId;
    private String paymentRequestId;
    private String originalPartnerReferenceNo;
    private String originalReferenceNo;
    private String originalExternalId;
    private String trxDateTime;
    private String language;
    private Amount amount;
    private Object additionalInfo;
}
