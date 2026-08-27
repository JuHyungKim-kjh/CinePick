package com.example.cinepick.config;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 비밀 설정이 채워졌는지 <b>DB 에 붙기 전에</b> 확인하고, 비었으면 읽을 수 있는 메시지로 세운다.
 *
 * 이 검사가 없으면 실패가 엉뚱한 곳에서 드러난다. 값이 비면 스프링은 플레이스홀더를 글자
 * 그대로 넘겨버려서 {@code Access denied for user '${DB_USERNAME}'} 같은 메시지가 나오고,
 * 그 뒤로 Hibernate 의 "Unable to determine Dialect" 스택트레이스가 200줄 딸려 나온다.
 * 처음 보는 사람은 설정 하나가 비었다는 사실에 닿기까지 한참 걸린다.
 *
 * {@link EnvironmentPostProcessor} 인 이유는 이것이 <b>빈이 만들어지기도 전에</b> 돌기 때문이다.
 * 일반 컴포넌트로 두면 데이터소스가 먼저 붙어보고 실패해 이미 늦는다.
 * 등록은 {@code META-INF/spring.factories} 에서 한다.
 */
public class RequiredSettingsCheck implements EnvironmentPostProcessor {

    /** 설정 키 → 배포 환경에서 그 값을 넣어줄 환경변수 이름 */
    private static final Map<String, String> REQUIRED = new LinkedHashMap<>();

    static {
        REQUIRED.put("spring.datasource.username", "DB_USERNAME");
        REQUIRED.put("spring.datasource.password", "DB_PASSWORD");
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        List<String> missing = new ArrayList<>();

        REQUIRED.forEach((key, envName) -> {
            String value;
            try {
                value = environment.getProperty(key);
            } catch (RuntimeException e) {
                // 채워줄 환경변수가 없으면 getProperty 가 PlaceholderResolutionException 을 던진다.
                // 그것 자체가 "비어 있다"는 뜻이므로 여기서 받아 아래 안내로 바꿔준다
                value = null;
            }
            if (value == null || value.isBlank() || value.startsWith("${")) {
                missing.add("  - " + key + "  (환경변수 " + envName + ")");
            }
        });

        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                    "\n\n필수 설정이 비어 있어 서버를 시작할 수 없습니다:\n"
                            + String.join("\n", missing)
                            + "\n\n로컬에서 실행한다면 config/application-local.yaml.example 을"
                            + " config/application-local.yaml 로 복사한 뒤 값을 채우세요."
                            + "\n배포 환경이라면 위 환경변수를 주입하세요.\n");
        }
    }
}
