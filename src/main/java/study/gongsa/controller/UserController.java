package study.gongsa.controller;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import study.gongsa.domain.UserAuth;
import study.gongsa.dto.*;
import study.gongsa.domain.User;
import study.gongsa.service.UserAuthService;
import study.gongsa.service.UserService;
import study.gongsa.support.exception.IllegalStateExceptionWithLocation;
import study.gongsa.support.jwt.JwtTokenProvider;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;

import static java.util.Objects.isNull;

@RestController
@CrossOrigin("*")
@Api(value="User")
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final UserAuthService userAuthService;
    private final JwtTokenProvider jwtTokenProvider;

    @ApiOperation(value="회원가입")
    @ApiResponses({
            @ApiResponse(code=201, message="회원가입 완료"),
            @ApiResponse(code=400, message="이메일 혹은 닉네임이 중복된 경우")
    })
    @PostMapping("/join")
    @Transactional
    public ResponseEntity join(@RequestBody @Valid JoinRequest req){
        User user = User.builder()
                .email(req.getEmail())
                .passwd(req.getPasswd())
                .nickname(req.getNickname())
                .build();
        userService.join(user);

        DefaultResponse response = new DefaultResponse();
        return new ResponseEntity(response, HttpStatus.CREATED);
    }

    @ApiOperation(value="디바이스 토큰 저장")
    @ApiResponses({
            @ApiResponse(code=200, message = "토큰 저장 완료"),
            @ApiResponse(code=401, message="로그인을 하지 않았을 경우(header에 Authorization이 없을 경우)"),
            @ApiResponse(code=403, message="토큰 에러(토큰이 만료되었을 경우 등), 가입하지 않은 그룹일 경우")
    })
    @PatchMapping("/device-token")
    @Transactional
    public ResponseEntity saveDeviceToken(@RequestBody @Valid DeviceTokenRequest req, HttpServletRequest request){
        int userUID = (int) request.getAttribute("userUID");
        User user = User.builder()
                .deviceToken(req.getDeviceToken())
                .UID(userUID)
                .build();
        userService.changeDeviceToken(user);

        DefaultResponse response = new DefaultResponse();
        return new ResponseEntity(response, HttpStatus.OK);
    }

    @ApiOperation(value="인증번호 생성 및 메일 전송")
    @ApiResponses({
            @ApiResponse(code=200, message="인증번호 생성 및 메일 전송 완료"),
            @ApiResponse(code=400, message="이미 인증된 사용자인 경우, 가입되지 않은 이메일인 경우")
    })
    @PatchMapping("/mail/join")
    @Transactional
    public ResponseEntity sendMail(@RequestBody @Valid MailRequest req){
        userService.sendJoinMail(req.getEmail());
        DefaultResponse response = new DefaultResponse();
        return new ResponseEntity(response, HttpStatus.OK);
    }

    @ApiOperation(value="인증코드 검증")
    @ApiResponses({
            @ApiResponse(code=200, message="인증번호 검증 완료"),
            @ApiResponse(code=400, message="가입되지 않은 이메일인 경우, 잘못되거나 만료된 인증코드인 경우")
    })
    @PatchMapping("/code")
    @Transactional
    public ResponseEntity verifyAuthCode(@RequestBody @Valid CodeRequest req){
        userService.verifyAuthCode(req.getEmail(), req.getAuthCode());
        DefaultResponse response = new DefaultResponse();
        return new ResponseEntity(response, HttpStatus.OK);
    }

    @ApiOperation(value="비밀번호 찾기 - 이메일 전송")
    @ApiResponses({
            @ApiResponse(code=200, message="비밀번호 변경 완료"),
            @ApiResponse(code=400, message="가입되지 않은 이메일인 경우")
    })
    @PatchMapping("/mail/passwd")
    @Transactional
    public ResponseEntity changePasswd(@RequestBody @Valid MailRequest req){
        userService.sendChangePasswdMail(req.getEmail());
        DefaultResponse response = new DefaultResponse();
        return new ResponseEntity(response, HttpStatus.OK);
    }

    @ApiOperation(value="비밀번호 변경")
    @ApiResponses({
            @ApiResponse(code=200, message="비밀번호 변경 완료"),
            @ApiResponse(code=400, message="비밀번호가 올바르지 않을 경우, 비밀번호가 이전과 동일할 경우"),
            @ApiResponse(code=401, message="로그인을 하지 않았을 경우(header에 Authorization이 없을 경우)"),
            @ApiResponse(code=403, message="토큰 에러(토큰이 만료되었을 경우 등)")
    })
    @PatchMapping("/passwd")
    @Transactional
    public ResponseEntity changePasswd(@RequestBody @Valid ChangePasswdRequest req, HttpServletRequest request){
        int userUID = (int) request.getAttribute("userUID");

        userService.changePasswd(userUID, req.getCurrentPasswd(), req.getNextPasswd());
        DefaultResponse response = new DefaultResponse();
        return new ResponseEntity(response, HttpStatus.OK);
    }

    @ApiOperation(value="로그인")
    @ApiResponses({
            @ApiResponse(code=200, message="로그인 완료"),
            @ApiResponse(code=401, message="로그인 정보 불일치 에러"),
    })
    @PostMapping("/login")
    @Transactional
    public ResponseEntity login(@RequestBody @Valid LoginRequest req){
        User user = User.builder()
                .email(req.getEmail())
                .passwd(req.getPasswd())
                .build();
        int userUID = userService.login(user).intValue();
        String refreshToken = jwtTokenProvider.makeRefreshToken(userUID);

        UserAuth userAuth = UserAuth.builder()
                .userUID(userUID)
                .refreshToken(refreshToken)
                .build();
        int userAuthUID = userAuthService.save(userAuth).intValue();
        String accessToken = jwtTokenProvider.makeAccessToken(userUID, userAuthUID);
        DefaultResponse response = new DefaultResponse(new LoginResponse(accessToken, refreshToken));
        return new ResponseEntity(response, HttpStatus.OK);
    }

    @ApiOperation(value="access token 갱신")
    @ApiResponses({
            @ApiResponse(code=200, message="access token 재발급 완료"),
            @ApiResponse(code=400, message="올바르지 않은 refreshToken"),
            @ApiResponse(code=401, message="로그인을 하지 않았을 경우(header에 Authorization이 없을 경우)"),
            @ApiResponse(code=403, message="토큰 에러(토큰이 만료되었을 경우 등)")
    })
    @PostMapping("/login/refresh")
    @Transactional
    public ResponseEntity refresh(@RequestBody @Valid RefreshRequest req, HttpServletRequest request){
        int userUID = (int) request.getAttribute("userUID");
        int userAuthUID = (int) request.getAttribute("userAuthUID");
        String refreshToken = req.getRefreshToken();

        try{
            jwtTokenProvider.verifyToken(refreshToken);
        }catch(Exception e){
            throw new IllegalStateExceptionWithLocation(HttpStatus.BAD_REQUEST,"refreshToken","올바르지 않은 refresh token입니다.");
        }

        userAuthService.checkRefreshToken(userAuthUID, refreshToken);
        String accessToken = jwtTokenProvider.makeAccessToken(userUID, userAuthUID);

        DefaultResponse response = new DefaultResponse(new RefreshResponse(accessToken));
        return new ResponseEntity(response, HttpStatus.CREATED);
    }

    @ApiOperation(value="마이페이지-유저 정보 조회")
    @ApiResponses({
            @ApiResponse(code=200, message="마이페이지 유저 정보 반환"),
            @ApiResponse(code=401, message="로그인 정보 불일치 에러"),
    })
    @GetMapping("/mypage")
    public ResponseEntity getUserInfo(HttpServletRequest request){
        int userUID = (int) request.getAttribute("userUID");
        MyPageUserResponse.Info userMypageInfo = userService.getUserMyPageInfo(userUID);

        DefaultResponse response = new DefaultResponse(userMypageInfo);
        return new ResponseEntity(response, HttpStatus.OK);
    }

    @ApiOperation(value="환경 설정-유저 정보 조회")
    @ApiResponses({
            @ApiResponse(code=200, message="환경 설정 유저 정보 반환"),
            @ApiResponse(code=401, message="로그인 정보 불일치 에러"),
    })
    @GetMapping("")
    public ResponseEntity getUserSettingInfo(HttpServletRequest request){
        int userUID = (int) request.getAttribute("userUID");
        MyPageUserResponse.Setting userSettingInfo = userService.getUserSettingInfo(userUID);

        DefaultResponse response = new DefaultResponse(userSettingInfo);
        return new ResponseEntity(response, HttpStatus.OK);
    }

    @ApiOperation(value="환경 설정-유저 정보 변경")
    @ApiResponses({
            @ApiResponse(code=200, message="환경 설정 유저 정보 변경 성공"),
            @ApiResponse(code=401, message="로그인 정보 불일치 에러"),
    })
    @PatchMapping(path="",consumes = {"multipart/form-data"})
    @Transactional
    public ResponseEntity changeUserSettingInfo(@RequestPart("json") @Valid ChangeUserInfoRequest req,
                                                @RequestPart(value = "image", required = false) MultipartFile image,
                                                HttpServletRequest request){
        int userUID = (int) request.getAttribute("userUID");

        //비밀번호 null이면 미변경
        if(!isNull(req.getPasswd())){
            userService.changePasswd(userUID, req.getPasswd());
        }

        //닉네임, 이미지 변경
        userService.changeUserSettingInfo(userUID, req.getNickname(), image, req.getChangeImage());

        DefaultResponse response = new DefaultResponse();
        return new ResponseEntity(response, HttpStatus.OK);
    }
}
