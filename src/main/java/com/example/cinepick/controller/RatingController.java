package com.example.cinepick.controller;

import com.example.cinepick.domain.Member;
import com.example.cinepick.dto.request.RatingReqDto;
import com.example.cinepick.repository.MemberRepository;
import com.example.cinepick.service.RatingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * 평점 저장 엔드포인트.
 * 행 하나만 갱신하면 되므로 페이지를 다시 그리지 않고 JSON 으로 답한다.
 */
@Controller
@RequestMapping("/ratings")
@RequiredArgsConstructor
public class RatingController {

    private final RatingService ratingService;
    private final MemberRepository memberRepository;

    @PostMapping("/save")
    @ResponseBody
    public ResponseEntity<RatingService.SaveResult> save(RatingReqDto reqDto, Authentication authentication) {
        Member member = currentMember(authentication);
        if (member == null) {
            return ResponseEntity.status(401).build();
        }

        RatingService.SaveResult result = ratingService.save(member, reqDto);
        // 평점 기회를 이미 썼거나 점수가 범위를 벗어난 경우.
        // 화면이 메시지를 그대로 보여줄 수 있도록 본문은 항상 싣는다
        return result.ok() ? ResponseEntity.ok(result) : ResponseEntity.badRequest().body(result);
    }

    private Member currentMember(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) return null;
        return memberRepository.findByEmail(authentication.getName()).orElse(null);
    }
}
