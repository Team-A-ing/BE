package com.readb.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SignupRequest(
        @NotBlank @Email String email,
        @NotBlank @Pattern(
                regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[@$!%*#?&])[A-Za-z\\d@$!%*#?&]{8,}$",
                message = "비밀번호는 8자 이상, 영문·숫자·특수문자를 포함해야 합니다."
        ) String password,
        @NotBlank @Size(min = 2, max = 20) String name,
        @NotBlank @Pattern(regexp = "LEADER|MEMBER") String role,
        @Size(max = 50) String jobTitle
) {}
