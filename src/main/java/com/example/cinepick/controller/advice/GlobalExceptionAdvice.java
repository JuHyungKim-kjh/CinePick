package com.example.cinepick.controller.advice;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.io.IOException;

/**
 * 도메인 예외를 알맞은 HTTP 상태로 내려 준다.
 *
 * <p><b>고친 것</b>: {@code MovieService.getMovieDetail()} 은 없는 영화에
 * {@link IllegalArgumentException} 을 던지는데, 핸들러가 없어 그대로 <b>500</b> 으로 나갔다.
 * 사용자가 없는 주소를 연 것인데 서버 잘못으로 표시됐고, 검색엔진도 없는 페이지를 살아 있는
 * 것으로 취급했다. 이 프로젝트에서 {@code IllegalArgumentException} 은 전부
 * "찾을 수 없다"는 뜻으로만 쓰이므로({@code CsService.getNotice} 도 같다) 404 로 내린다.
 *
 * <p>예외를 직접 화면으로 바꾸지 않고 {@link HttpServletResponse#sendError(int)} 를 부르는 이유는,
 * 그래야 <b>오류 처리 경로가 한 갈래로 모이기</b> 때문이다 — 필터에서 끝나는 오류든 컨트롤러에서
 * 난 오류든 모두 {@code templates/error/*} 를 지나가고, {@code ErrorRefAttributes} 의 참조 코드도
 * 똑같이 붙는다.
 */
@ControllerAdvice
public class GlobalExceptionAdvice {

    @ExceptionHandler(IllegalArgumentException.class)
    public void handleNotFound(IllegalArgumentException e, HttpServletResponse response) throws IOException {
        response.sendError(HttpStatus.NOT_FOUND.value(), e.getMessage());
    }
}
