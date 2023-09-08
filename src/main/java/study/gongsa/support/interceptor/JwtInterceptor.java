package study.gongsa.support.interceptor;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import study.gongsa.service.UserAuthService;
import study.gongsa.service.UserService;
import study.gongsa.support.exception.IllegalStateExceptionWithLocation;
import study.gongsa.support.jwt.JwtTokenProvider;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtInterceptor implements HandlerInterceptor {
    private final JwtTokenProvider jwtTokenProvider;
    private final UserService userService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String headerToken = null;
        try{
            headerToken = request.getHeader("Authorization");
            if (headerToken == null)
                throw new NullPointerException();

            Claims data = jwtTokenProvider.checkValid(headerToken);
            int userUID = (int)data.get("userUID");
            int userAuthUID = (int)data.get("userAuthUID");
            if(!userService.isAuth(userUID))
                throw new IllegalArgumentException("notAuth");
            request.setAttribute("userUID", userUID);
            request.setAttribute("userAuthUID", userAuthUID);

            return true;
        } catch(ExpiredJwtException e){
            Boolean isRefresh = ("POST".equals(request.getMethod())) && ("/api/user/login/refresh".equals(request.getRequestURI()));
            if(isRefresh){
                // decode
                request.setAttribute("userUID", e.getClaims().get("userUID"));
                request.setAttribute("userAuthUID", e.getClaims().get("userAuthUID"));
                return true;
            }
            throw new IllegalStateExceptionWithLocation(HttpStatus.UNAUTHORIZED,"auth", "로그인 후 이용해주세요.");
        } catch(IllegalArgumentException e){
            log.info("{}: {}",e.getClass().getName(), e.getMessage());
            if(e.getMessage().equals("notAuth"))
                throw new IllegalStateExceptionWithLocation(HttpStatus.FORBIDDEN,"auth", "이메일 인증 후 이용해주세요.");
            else
                throw new IllegalStateExceptionWithLocation(HttpStatus.UNAUTHORIZED,"auth", "로그인 후 이용해주세요.");
        } catch (Exception e){
            log.info("{}: {}",e.getClass().getName(), e.getMessage());
            throw new IllegalStateExceptionWithLocation(HttpStatus.UNAUTHORIZED,"auth", "로그인 후 이용해주세요.");
        }
    }
}
