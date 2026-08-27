package com.example.cinepick.dto.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SignUpReqDto {
    private String email;
    private String nickname;
    private String password;
    private String passwordConfirm;
}
