package com.uii.paymently.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BillInquiryRequest {

    private String partnerServiceId;
    private String customerNo;
    private String virtualAccountNo;
    private String trxDateInit;
    private Integer channelCode;
    private String language;
    private BigDecimal amount;
    private String hashedSourceAccountNo;
    private String sourceBankCode;
    private String passApp;
    private String inquiryRequestId;
    private String paymentRequestId;
    private AdditionalInfo additionalInfo;
}