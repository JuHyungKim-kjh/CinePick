package com.example.cinepick.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.webmvc.error.DefaultErrorAttributes;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.WebRequest;

import java.util.Map;
import java.util.UUID;

/**
 * 오류 화면에 <b>짧은 참조 코드</b>를 하나 얹고, 같은 코드를 서버 로그에도 남긴다.
 *
 * <p>스택트레이스를 사용자에게 보여줄 수는 없지만, 그렇다고 "오류가 발생했습니다"만 남기면
 * 문의가 들어와도 어느 요청이었는지 찾을 방법이 없다. 사용자가 1:1 문의에 이 여섯 글자만
 * 적어 주면 로그에서 해당 요청을 바로 집을 수 있다. 내부 정보는 하나도 새지 않는다.
 *
 * <p>{@code @ControllerAdvice} 가 아니라 {@link DefaultErrorAttributes} 를 갈아끼운 이유는,
 * 컨트롤러까지 오지 않고 <b>필터 단계에서 끝나는 오류</b>(인증 실패, 잘못된 요청 등)도
 * 여기는 반드시 지나가기 때문이다.
 */
@Component
public class ErrorRefAttributes extends DefaultErrorAttributes {

    /** 같은 요청에서 두 번 매기지 않도록 request 에 담아 둔다 */
    private static final String REF_ATTRIBUTE = "cinepick.errorRef";

    @Override
    public Map<String, Object> getErrorAttributes(WebRequest webRequest, ErrorAttributeOptions options) {
        Map<String, Object> attributes = super.getErrorAttributes(webRequest, options);

        /*
         * 스택트레이스와 예외 메시지는 <b>어떤 경우에도</b> 응답에 싣지 않는다.
         *
         * application.yaml 의 server.error.include-* 만으로는 부족했다 — devtools 가 개발 환경에서
         * 이 값들을 always 로 되돌려 놓아, 실측으로 404 JSON 응답에 필터 체인 전체가 담긴
         * 80줄짜리 스택트레이스가 그대로 나갔다. 설정에 기대지 않고 여기서 확실히 지운다.
         * 진단에 필요한 정보는 아래 ref 로 서버 로그에서 찾는다.
         */
        attributes.remove("trace");
        attributes.remove("message");
        attributes.remove("exception");

        String ref = resolveRef(webRequest);
        attributes.put("ref", ref);

        /*
         * 요청 메서드. 5xx 화면이 '다시 시도' 버튼을 GET 일 때만 내보내는 데 쓴다
         * (POST 였다면 재전송 경고가 떠서 상황을 더 헷갈리게 만든다).
         *
         * 화면에서 ${#httpServletRequest.method} 로 직접 읽을 수 없다 —
         * <b>Thymeleaf 3.1 에서 #request·#httpServletRequest 가 제거됐다.</b>
         * 그대로 쓰면 오류 화면이 렌더 도중 또 터진다.
         */
        attributes.put("method", resolveMethod(webRequest));

        Object status = attributes.get("status");
        Object path = attributes.get("path");
        Throwable error = getError(webRequest);

        // 5xx 만 로그에 남긴다. 404 는 오탈자·크롤러 때문에 흔하게 발생해 로그를 덮어버린다
        if (status instanceof Integer code && code >= 500) {
            System.out.println("[오류 " + code + "] ref=" + ref + " path=" + path
                    + (error != null ? " (" + error.getClass().getSimpleName() + ": " + error.getMessage() + ")" : ""));
            if (error != null) {
                error.printStackTrace();
            }
        }

        return attributes;
    }

    /**
     * 오류 dispatch 중에도 원래 요청의 메서드는 그대로 남아 있다.
     * 실제로 넘어오는 것은 ServletWebRequest 이지만, 아니더라도 GET 으로 보아 화면이 깨지지 않게 한다.
     */
    private String resolveMethod(WebRequest webRequest) {
        if (webRequest instanceof NativeWebRequest nativeRequest) {
            HttpServletRequest request = nativeRequest.getNativeRequest(HttpServletRequest.class);
            if (request != null) return request.getMethod();
        }
        return "GET";
    }

    private String resolveRef(WebRequest webRequest) {
        Object existing = webRequest.getAttribute(REF_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
        if (existing instanceof String s) return s;

        String ref = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        webRequest.setAttribute(REF_ATTRIBUTE, ref, RequestAttributes.SCOPE_REQUEST);
        return ref;
    }
}
