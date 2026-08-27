package com.example.cinepick.service;

import com.example.cinepick.domain.Member;
import com.example.cinepick.dto.request.SignUpReqDto;
import com.example.cinepick.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class LoginService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    /** 비밀번호 최소 길이. 화면 안내 문구와 어긋나지 않게 여기서 정한다 */
    public static final int PASSWORD_MIN = 8;

    /** 닉네임 길이. Member.nickname 은 unique 라 중복도 함께 본다 */
    private static final int NICKNAME_MIN = 2;
    private static final int NICKNAME_MAX = 12;

    // 형식만 거르는 최소한의 검사. 실제 수신 가능 여부는 메일을 보내봐야 알 수 있고,
    // 지금은 인증 메일을 보내지 않으므로 여기까지만 한다
    private static final Pattern EMAIL = Pattern.compile("^[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+$");

    /**
     * 회원 가입.
     *
     * 검증에 걸리면 예외 대신 안내 문구를 돌려준다. 폼을 덜 채운 것은 프로그램의 오류가 아니라
     * 사용자가 처한 상황이고, 컨트롤러가 같은 화면에 메시지만 얹어 다시 보여주면 된다
     * ({@link InquiryService#create}·{@link CsAdminService} 와 같은 방식).
     *
     * @return null 이면 가입 성공, 값이 있으면 화면에 띄울 실패 사유
     */
    @Transactional
    public String registerMember(SignUpReqDto reqDto) {
        String email = trim(reqDto.getEmail());
        String nickname = trim(reqDto.getNickname());
        String password = reqDto.getPassword() == null ? "" : reqDto.getPassword();

        if (email.isEmpty() || nickname.isEmpty() || password.isEmpty()) {
            return "모든 항목을 입력해 주세요.";
        }
        if (!EMAIL.matcher(email).matches()) {
            return "이메일 주소 형식이 올바르지 않아요.";
        }
        if (nickname.length() < NICKNAME_MIN || nickname.length() > NICKNAME_MAX) {
            return "닉네임은 " + NICKNAME_MIN + "~" + NICKNAME_MAX + "자로 입력해 주세요.";
        }
        if (password.length() < PASSWORD_MIN) {
            return "비밀번호는 " + PASSWORD_MIN + "자 이상이어야 해요.";
        }
        if (!password.equals(reqDto.getPasswordConfirm())) {
            return "비밀번호가 서로 일치하지 않아요.";
        }
        if (memberRepository.findByEmail(email).isPresent()) {
            return "이미 가입된 이메일이에요.";
        }
        // 닉네임에 unique 제약이 걸려 있어, 여기서 막지 않으면 DB 예외가 컨트롤러까지 올라가
        // 안내 대신 오류 화면이 뜬다
        if (memberRepository.existsByNickname(nickname)) {
            return "이미 사용 중인 닉네임이에요.";
        }

        Member newMember = new Member();
        newMember.setEmail(email);
        newMember.setNickname(nickname);
        // 암호화된 비밀번호만 저장한다
        newMember.setPassword(passwordEncoder.encode(password));

        memberRepository.save(newMember);
        return null;
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
