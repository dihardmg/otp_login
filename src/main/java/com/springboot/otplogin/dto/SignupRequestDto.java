package com.springboot.otplogin.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SignupRequestDto {

    @NotNull(message = "must not be null")
    @NotBlank(message = "must not be blank")
    @Size(min = 2, max = 50, message = "Name must be between 2 and 50 characters")
    private String name;

    @NotNull(message = "must not be null")
    @NotBlank(message = "must not be blank")
    @Email(message = "Invalid email format")
    private String email;
}