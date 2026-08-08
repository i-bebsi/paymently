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
public class PaymentRequest {
    private String partnerServiceId;
    private String customerNo;
    private String virtualAccountNo;
    private String virtualAccountName;
    private String trxId;
    private String language;
    private String paymentRequestId;
    private String channelCode;
    private String hashedSourceAccountNo;
    private String sourceBankCode;
    private Amount paidAmount;
    private String trxDateTime;
    private Amount totalAmount;
    private String referenceNo;
    private String journalNum;
    private Object billDetails;
    private Object additionalInfo;
}
