package com.example.order_service.client;

import com.example.order_service.configuration.FeignClientConfig;
import com.example.order_service.dto.UserDTO;
import com.example.order_service.utils.BaseResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "user-service", fallback = UserServiceFallback.class, configuration = FeignClientConfig.class)
public interface UserServiceClient {
    @GetMapping("/user/me")
    BaseResponse<UserDTO> getCurrentUser();

    @GetMapping("/user/{id}")
    BaseResponse<UserDTO> getUserById(@PathVariable("id") Long id);
}
