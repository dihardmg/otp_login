package com.springboot.otplogin.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class OtpRequestDto {

    @NotBlank(message = "must not be blank")
    @Email(message = "Invalid email format")
    private String email;
}