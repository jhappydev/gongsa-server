package study.gongsa.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import study.gongsa.domain.User;
import study.gongsa.domain.UserAuth;
import study.gongsa.repository.UserAuthRepository;
import study.gongsa.support.exception.IllegalStateExceptionWithLocation;

import java.util.Optional;

@Service
@RequiredArgsConstructor

public class UserAuthService {
    private final UserAuthRepository userAuthRepository;

    public Number save(UserAuth userAuth){
        return userAuthRepository.save(userAuth);
    }

    public void checkRefreshToken(int userAuthUID, String refreshToken){
        Optional<UserAuth> userAuthByUserUID = userAuthRepository.findByUID(userAuthUID);
        if(userAuthByUserUID.isEmpty())
            throw new IllegalStateExceptionWithLocation(HttpStatus.UNAUTHORIZED, "auth","로그인 후 이용해주세요.");
        if(!refreshToken.equals(userAuthByUserUID.get().getRefreshToken())){ //일치X
            throw new IllegalStateExceptionWithLocation(HttpStatus.BAD_REQUEST, "refreshToken", "올바르지 않은 refresh token입니다.");
        }
    }
}
