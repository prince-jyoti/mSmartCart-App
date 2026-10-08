package com.example.order_service.client;

import com.example.order_service.dto.UserDTO;
import com.example.order_service.utils.BaseResponse;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

@Component
public class UserServiceFallbackFactory implements FallbackFactory<UserServiceClient> {
    @Override
    public UserServiceClient create(Throwable cause) {
        return new UserServiceClient() {
            @Override
            public BaseResponse<UserDTO> getCurrentUser() {
                throw FallbackErrors.translate(cause, "User not found", "User service unavailable");
            }
            @Override
            public BaseResponse<UserDTO> getUserById(Long id) {
                throw FallbackErrors.translate(cause, "User not found", "User service unavailable");
            }
        };
    }
}
