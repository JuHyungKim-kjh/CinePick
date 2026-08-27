package com.example.cinepick.controller;

import com.example.cinepick.domain.Member;
import com.example.cinepick.dto.response.ReservationResDto;
import com.example.cinepick.repository.MemberRepository;
import com.example.cinepick.service.ReservationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * 예매 중계 이력을 기록하고 확정짓는 엔드포인트.
 * 화면 전환 없이 팝업에서 처리해야 해서 모두 JSON 으로 응답한다.
 */
@Controller
@RequestMapping("/reservations")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;
    private final MemberRepository memberRepository;

    /**
     * 팝업 응답 결과.
     * next 에 다음 미확인 건을 실으면 화면이 새로고침 없이 내용만 바꿔 이어서 물어본다.
     */
    public record PopupResponse(boolean ok, ReservationResDto next, long remaining) {
    }

    /**
     * 상세페이지에서 예매사 링크를 눌렀을 때 호출된다.
     * 사용자는 이미 예매 사이트로 이동한 뒤라 실패해도 화면에서 할 수 있는 일이 없다.
     * 그래서 오류 화면을 띄우지 않고 조용히 200 으로 답한다.
     */
    @PostMapping("/click")
    @ResponseBody
    public ResponseEntity<Void> recordClick(@RequestParam String movieCd,
                                            @RequestParam String site,
                                            Authentication authentication) {
        Member member = currentMember(authentication);
        if (member != null) {
            reservationService.recordClick(member, movieCd, site);
        }
        return ResponseEntity.ok().build();
    }

    /**
     * 탭으로 돌아왔을 때 물어볼 미확인 건이 있는지 확인한다.
     * 예매 사이트는 새 탭에서 열려 CinePick 화면이 살아 있으므로, 화면을 다시 그리지 않고도
     * 팝업을 띄울 수 있도록 이 경로를 둔다. 클릭 직후의 건은 유예에 걸려 나오지 않는다.
     */
    @GetMapping("/pending")
    @ResponseBody
    public ResponseEntity<PopupResponse> pending(Authentication authentication) {
        Member member = currentMember(authentication);
        if (member == null) {
            return ResponseEntity.status(401).build();
        }

        return ResponseEntity.ok(new PopupResponse(
                true,
                reservationService.findPendingForReturn(member.getId()).orElse(null),
                reservationService.countPending(member.getId())
        ));
    }

    /** "실제로 예매했어요" */
    @PostMapping("/{id}/confirm")
    @ResponseBody
    public ResponseEntity<PopupResponse> confirm(@PathVariable Long id, Authentication authentication) {
        return decide(id, true, authentication);
    }

    /** "예매까지 가지 않았어요" */
    @PostMapping("/{id}/cancel")
    @ResponseBody
    public ResponseEntity<PopupResponse> cancel(@PathVariable Long id, Authentication authentication) {
        return decide(id, false, authentication);
    }

    private ResponseEntity<PopupResponse> decide(Long id, boolean booked, Authentication authentication) {
        Member member = currentMember(authentication);
        if (member == null) {
            return ResponseEntity.status(401).build();
        }

        // 남의 예매이거나 이미 확정된 건이면 false 가 돌아온다
        boolean decided = reservationService.decide(member.getId(), id, booked);
        if (!decided) {
            return ResponseEntity.status(403).build();
        }

        return ResponseEntity.ok(new PopupResponse(
                true,
                reservationService.findOldestPending(member.getId()).orElse(null),
                reservationService.countPending(member.getId())
        ));
    }

    private Member currentMember(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) return null;
        return memberRepository.findByEmail(authentication.getName()).orElse(null);
    }
}
