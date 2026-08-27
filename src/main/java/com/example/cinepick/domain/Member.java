package com.example.cinepick.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter // 💡 이 어노테이션이 추가되어야 set...() 에러가 사라집니다.
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // 데이터베이스 기본키 (PK)

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false, unique = true)
    private String nickname;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String role = "ROLE_USER";

    @Column(columnDefinition = "boolean default false")
    private boolean surveyPromptDismissed = false;
}