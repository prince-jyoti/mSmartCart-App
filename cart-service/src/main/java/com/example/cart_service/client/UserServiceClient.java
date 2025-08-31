package com.example.cart_service.client;


import com.example.cart_service.configuration.FeignClientConfig;
import com.example.cart_service.dto.UserDTO;
import com.example.cart_service.utils.BaseResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;

@FeignClient(name = "user-service", url = "${user.service.url}", fallback = UserServiceFallback.class, configuration = FeignClientConfig.class)
public interface UserServiceClient {
    @PostMapping("/user")
    BaseResponse<UserDTO> createOrUpdate();
}
