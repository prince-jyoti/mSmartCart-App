package com.example.order_service.client;

import com.example.order_service.dto.PaymentRes;
import com.example.order_service.utils.BaseResponse;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

@Component
public class PaymentServiceFallbackFactory implements FallbackFactory<PaymentServiceClient> {
    @Override
    public PaymentServiceClient create(Throwable cause) {
        return new PaymentServiceClient() {
            @Override
            public BaseResponse<PaymentRes> getPaymentById(String id) {
                throw FallbackErrors.translate(cause, "Payment not found", "Payment service unavailable");
            }
        };
    }
}
