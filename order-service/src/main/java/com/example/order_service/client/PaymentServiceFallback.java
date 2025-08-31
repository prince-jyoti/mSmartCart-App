package com.example.order_service.client;

import com.example.order_service.dto.PaymentRes;
import com.example.order_service.utils.BaseResponse;
import org.springframework.stereotype.Component;


@Component
public class PaymentServiceFallback implements PaymentServiceClient {
    @Override
    public BaseResponse<PaymentRes> getPaymentById(String id) {
        PaymentRes paymentRes = new PaymentRes();
        paymentRes.setPaymentId(id);
        return new BaseResponse<>(503, "Product service unavailable", paymentRes);
    }
}
