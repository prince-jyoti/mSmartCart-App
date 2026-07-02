package com.example.order_service.client;

import com.example.order_service.configuration.FeignClientConfig;
import com.example.order_service.dto.PaymentRes;
import com.example.order_service.utils.BaseResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "payment-service", fallback = PaymentServiceFallback.class, configuration = FeignClientConfig.class)
public interface PaymentServiceClient {
    @GetMapping("/payments/{id}")
    BaseResponse<PaymentRes> getPaymentById(@PathVariable String id);
}
