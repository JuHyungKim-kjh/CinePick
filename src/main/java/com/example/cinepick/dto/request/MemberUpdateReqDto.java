package com.example.cinepick.dto.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MemberUpdateReqDto {
    private String nickname;
    private String password;        // 비워두면 기존 비밀번호 유지
    private String passwordConfirm;
}