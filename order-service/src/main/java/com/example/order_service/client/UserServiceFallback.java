package com.example.order_service.client;

import com.example.order_service.dto.UserDTO;
import com.example.order_service.utils.BaseResponse;
import org.springframework.stereotype.Component;

@Component
public class UserServiceFallback implements UserServiceClient {
    @Override
    public BaseResponse<UserDTO> getCurrentUser() {
        UserDTO defaultUser = new UserDTO();
        defaultUser.setEmail("default@unknown");
        return new BaseResponse<>(503, "User service unavailable", defaultUser);
    }

    @Override
    public BaseResponse<UserDTO> getUserById(Long id) {
        UserDTO defaultUser = new UserDTO();
        defaultUser.setEmail("default@unknown");
        return new BaseResponse<>(503, "User service unavailable", defaultUser);
    }
}
