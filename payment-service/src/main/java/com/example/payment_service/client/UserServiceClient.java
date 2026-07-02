package com.example.payment_service.client;

import com.example.payment_service.configuration.FeignClientConfig;
import com.example.payment_service.dto.UserDTO;
import com.example.payment_service.utils.BaseResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

@FeignClient(name = "user-service", fallback = UserServiceFallback.class, configuration = FeignClientConfig.class)
public interface UserServiceClient {
    @GetMapping("/user/me")
    BaseResponse<UserDTO> getCurrentUser();
}