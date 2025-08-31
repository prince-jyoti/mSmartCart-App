package com.example.order_service.client;

import com.example.order_service.dto.UserDTO;
import com.example.order_service.utils.BaseResponse;
import org.springframework.stereotype.Component;

@Component
public class UserServiceFallback implements UserServiceClient {
    @Override
    public BaseResponse<UserDTO> createOrUpdate() {
        UserDTO defaultUser = new UserDTO();
        defaultUser.setKeycloakUserId("default-id");
        return new BaseResponse<>(503, "User service unavailable", defaultUser);
    }
}
