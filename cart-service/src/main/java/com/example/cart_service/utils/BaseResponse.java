package com.example.cart_service.utils;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@Data
@NoArgsConstructor
public class BaseResponse<T> {
    private int status;
    private String message;
    private T data;

}
