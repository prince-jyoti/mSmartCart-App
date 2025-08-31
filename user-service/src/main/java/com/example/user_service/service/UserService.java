package com.example.user_service.service;

import com.example.user_service.dto.UserDto;
import com.example.user_service.entity.User;
import com.example.user_service.repository.UserRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;


import java.util.Optional;


@Service
public class UserService {
    @Autowired
    private UserRepo userRepo;

    public UserDto createOrUpdateUser(User user) {
        Optional<User> existingUserOpt = userRepo.findByKeycloakUserId(user.getKeycloakUserId());
        User savedUser;
        if (existingUserOpt.isPresent()) {
            User existingUser = existingUserOpt.get();
            existingUser.setOrderIds(user.getOrderIds());
            existingUser.setCartId(user.getCartId());
            existingUser.setProductIds(user.getProductIds());
            savedUser = userRepo.save(existingUser);
        } else {
            savedUser = userRepo.save(user);
        }
        return toUserDto(savedUser);
    }

    private UserDto toUserDto(User user) {
        UserDto dto = new UserDto();
        dto.setId(user.getId());
        dto.setKeycloakUserId(user.getKeycloakUserId());
        dto.setOrderIds(user.getOrderIds());
        dto.setCartId(user.getCartId());
        dto.setProductIds(user.getProductIds());
        return dto;
    }

}