package com.example.user_service.service;

import com.example.user_service.dto.UserDto;
import com.example.user_service.entity.User;
import com.example.user_service.repository.UserRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;


@Service
public class UserService {
    @Autowired
    private UserRepo userRepo;

    public UserDto getByEmail(String email) {
        User user = userRepo.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found: " + email));
        return toUserDto(user);
    }

    public UserDto getById(Long id) {
        User user = userRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found: " + id));
        return toUserDto(user);
    }

    private UserDto toUserDto(User user) {
        UserDto dto = new UserDto();
        dto.setId(user.getId());
        dto.setEmail(user.getEmail());
        dto.setName(user.getName());
        dto.setRole(user.getRole());
        dto.setOrderIds(user.getOrderIds());
        dto.setCartId(user.getCartId());
        dto.setProductIds(user.getProductIds());
        return dto;
    }

}