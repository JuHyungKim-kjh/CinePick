package com.example.cinepick.controller.advice;

import com.example.cinepick.domain.Member;
import com.example.cinepick.dto.response.ReservationResDto;
import com.example.cinepick.repository.MemberRepository;
import com.example.cinepick.service.ReservationService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.Optional;

/**
 * 미확인 예매를 모든 화면의 모델에 실어 보낸다.
 * 확인 팝업이 layout.html 에 달려 있어 어느 페이지에서든 떠야 하므로, 페이지마다 고치는 대신 여기서 넣는다.
 */
@ControllerAdvice
@RequiredArgsConstructor
public class PendingReservationAdvice {

    private final ReservationService reservationService;
    private final MemberRepository memberRepository;

    @ModelAttribute
    public void addPendingReservation(Authentication authentication,
                                      HttpServletRequest request,
                                      Model model) {
        // 팝업은 화면을 새로 그릴 때만 의미가 있다. ajax POST 는 모델을 쓰지 않으므로 건너뛴다
        if (!"GET".equals(request.getMethod())) return;

        Member member = currentMember(authentication);
        if (member == null) return; // 게스트는 예매 이력이 생길 수 없으므로 DB를 치지 않습니다

        // 미확인 건이 없어도 팝업 껍데기는 깔아둔다.
        // 예매 사이트에 다녀와 이 탭으로 돌아왔을 때 스크립트가 내용만 채워 띄울 수 있어야 한다
        model.addAttribute("reservationPopupEnabled", true);

        Optional<ReservationResDto> pending = reservationService.findOldestPending(member.getId());
        if (pending.isEmpty()) return;

        model.addAttribute("pendingReservation", pending.get());
        model.addAttribute("pendingReservationCount", reservationService.countPending(member.getId()));
    }

    private Member currentMember(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return null;
        }
        return memberRepository.findByEmail(authentication.getName()).orElse(null);
    }
}
