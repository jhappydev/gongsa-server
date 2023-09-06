package study.gongsa.controller;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.google.gson.Gson;
import com.jayway.jsonpath.JsonPath;
import io.swagger.annotations.ApiModelProperty;
import org.json.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.FileSystemUtils;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.filter.CharacterEncodingFilter;
import study.gongsa.domain.StudyGroup;
import study.gongsa.domain.User;
import study.gongsa.domain.UserAuth;
import study.gongsa.dto.*;
import study.gongsa.repository.UserAuthRepository;
import study.gongsa.repository.UserRepository;
import study.gongsa.support.exception.IllegalStateExceptionWithLocation;
import study.gongsa.support.jwt.JwtTokenProvider;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Timestamp;
import java.util.Date;
import java.util.Optional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Transactional // 각 테스트 종료 후 rollback
@AutoConfigureMockMvc // MockMvc를 빈으로 등록
@SpringBootTest // 통합 테스트이므로
@Slf4j
class UserControllerTest {

    private static String baseURL = "/api/user";
    private Integer userUID;
    private String accessToken;
    private String refreshToken;

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private UserAuthRepository userAuthRepository;
    @Autowired
    private JwtTokenProvider jwtTokenProvider;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilter(new CharacterEncodingFilter("UTF-8", true)) //한글 설정
                .build();
        
        // 테스트 위한 데이터
        User user = User.builder()
                .email("gong40sa04@gmail.com")
                .passwd(passwordEncoder.encode("12345678"))
                .nickname("통합테스트")
                .authCode("00000a")
                .build();
        userUID = userRepository.save(user).intValue();
        log.debug("테스트 유저 > {}",user);

        refreshToken = jwtTokenProvider.makeRefreshToken(userUID);
        Integer userAuthUID = userAuthRepository.save(UserAuth.builder()
                        .userUID(userUID)
                        .refreshToken(refreshToken)
                .build()).intValue();
        accessToken = jwtTokenProvider.makeAccessToken(userUID, userAuthUID);
    }

    @AfterEach
    void tearDown() throws Exception {
        Path root = Paths.get("image");
        String filePath = root.getFileName() + "/u"+userUID+".jpg";
        File file = new File(filePath);

        if (file.exists()) {
            file.delete();
        }
    }

    @Test
    void 회원가입_성공() throws Exception {
        // given
        JoinRequest joinRequest = new JoinRequest("gong40sa04_@gmail.com", "12345678", "통합테스트2");

        // when
        ResultActions resultActions = mockMvc.perform(post(baseURL+"/join")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(joinRequest))
                        .accept(MediaType.APPLICATION_JSON))
                .andDo(print());

        // then
        resultActions
                .andExpect(status().isCreated());
    }

    @Test
    void 회원가입_실패_닉네임중복() throws Exception {
        // given
        JoinRequest joinRequest = new JoinRequest("gong40sa04_@gmail.com", "12345678", "통합테스트");

        // when
        ResultActions resultActions = mockMvc.perform(post(baseURL+"/join")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(joinRequest))
                        .accept(MediaType.APPLICATION_JSON))
                .andDo(print());

        // then
        resultActions
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.location").value("nickname"));
    }

    @Test
    void 회원가입_실패_이메일중복() throws Exception {
        // given
        JoinRequest joinRequest = new JoinRequest("gong40sa04@gmail.com", "12345678", "통합테스트2");

        // when
        ResultActions resultActions = mockMvc.perform(post(baseURL+"/join")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(joinRequest))
                        .accept(MediaType.APPLICATION_JSON))
                .andDo(print());

        // then
        resultActions
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.location").value("email"));
    }

    @Test
    void 이메일전송_성공() throws Exception {
        // given
        MailRequest mailRequest = new MailRequest("gong40sa04@gmail.com");

        // when
        ResultActions resultActions = mockMvc.perform(patch(baseURL+"/mail/join")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(mailRequest))
                        .accept(MediaType.APPLICATION_JSON))
                .andDo(print());
        // then
        resultActions.andExpect(status().isOk());
    }

    @Test
    void 이메일전송_실패_미가입자() throws Exception {
        // given
        MailRequest mailRequest = new MailRequest("gong40sa04_@gmail.com");

        // when
        ResultActions resultActions = mockMvc.perform(patch(baseURL+"/mail/join")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(mailRequest))
                        .accept(MediaType.APPLICATION_JSON))
                .andDo(print());
        // then
        resultActions.andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.msg").value("가입되지 않은 이메일입니다."));
    }

    @Test
    void 이메일전송_실패_인증완료자() throws Exception {
        // given
        userRepository.updateIsAuth(true, new Timestamp(new Date().getTime()), userUID);
        MailRequest mailRequest = new MailRequest("gong40sa04@gmail.com");

        // when
        ResultActions resultActions = mockMvc.perform(patch(baseURL+"/mail/join")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(mailRequest))
                        .accept(MediaType.APPLICATION_JSON))
                .andDo(print());

        // then
        resultActions.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.msg").value("이미 인증된 사용자입니다."));
    }

    @Test
    void 이메일인증번호검증_성공() throws Exception {
        // given
        CodeRequest codeRequest = new CodeRequest("gong40sa04@gmail.com","00000a");

        // when
        ResultActions resultActions = mockMvc.perform(patch(baseURL+"/code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(codeRequest))
                        .accept(MediaType.APPLICATION_JSON))
                .andDo(print());

        // then
        resultActions.andExpect(status().isOk());
    }

    @Test
    void 이메일인증번호검증_실패_미가입자() throws Exception {
        // given
        CodeRequest codeRequest = new CodeRequest("gong40sa04_@gmail.com","000000");

        // when
        ResultActions resultActions = mockMvc.perform(patch(baseURL+"/code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(codeRequest))
                        .accept(MediaType.APPLICATION_JSON))
                .andDo(print());

        // then
        resultActions.andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.location").value("email"));
    }

    @Test
    void 이메일인증번호검증_실패_잘못된인증코드() throws Exception {
        // given
        CodeRequest codeRequest = new CodeRequest("gong40sa04@gmail.com","00000b");

        // when
        ResultActions resultActions = mockMvc.perform(patch(baseURL+"/code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(codeRequest))
                        .accept(MediaType.APPLICATION_JSON))
                .andDo(print());

        // then
        resultActions.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.location").value("incorrect"));
    }

    @Test
    void 이메일인증번호검증_실패_만료된인증코드() throws Exception {
        // given
        userRepository.updateIsAuth(false, new Timestamp(new Date().getTime()-(24 * 60 * 60 * 1000)), userUID);
        CodeRequest codeRequest = new CodeRequest("gong40sa04@gmail.com","00000a");

        // when
        ResultActions resultActions = mockMvc.perform(patch(baseURL+"/code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(codeRequest))
                        .accept(MediaType.APPLICATION_JSON))
                .andDo(print());

        // then
        resultActions.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.location").value("expiration"));
    }

    @Test
    void 임시비밀번호생성전송_성공() throws Exception {
        // given
        MailRequest mailRequest = new MailRequest("gong40sa04@gmail.com");

        // when
        ResultActions resultActions = mockMvc.perform(patch(baseURL+"/mail/passwd")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(mailRequest))
                        .accept(MediaType.APPLICATION_JSON))
                .andDo(print());

        // then
        resultActions.andExpect(status().isOk());
    }

    @Test
    void 임시비밀번호생성전송_실패_미가입자() throws Exception {
        // given
        MailRequest mailRequest = new MailRequest("gong40sa04_@gmail.com");

        // when
        ResultActions resultActions = mockMvc.perform(patch(baseURL+"/mail/passwd")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(mailRequest))
                        .accept(MediaType.APPLICATION_JSON))
                .andDo(print());

        // then
        resultActions.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.location").value("email"));
    }

    @Test
    void 로그인_성공() throws Exception {
        // given
        LoginRequest loginRequest = new LoginRequest("gong40sa04@gmail.com", "12345678");

        // when
        ResultActions resultActions = mockMvc.perform(post(baseURL+"/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest))
                        .accept(MediaType.APPLICATION_JSON))
                .andDo(print());

        // then
        MvcResult mvcResult = resultActions.andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").exists())
                .andExpect(jsonPath("$.data.refreshToken").exists())
                .andReturn();
    }

    @Test
    void 로그인_실패_미가입자() throws Exception {
        // given
        LoginRequest loginRequest = new LoginRequest("gong40sa04_@gmail.com", "12345678");

        // when
        ResultActions resultActions = mockMvc.perform(post(baseURL+"/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest))
                        .accept(MediaType.APPLICATION_JSON))
                .andDo(print());

        // then
        resultActions.andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.location").value("email"));
    }

    @Test
    void 로그인_실패_비밀번호불일치() throws Exception {
        // given
        LoginRequest loginRequest = new LoginRequest("gong40sa04@gmail.com", "123456789");

        // when
        ResultActions resultActions = mockMvc.perform(post(baseURL+"/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest))
                        .accept(MediaType.APPLICATION_JSON))
                .andDo(print());

        // then
        resultActions.andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.location").value("passwd"));
    }

    @Test
    void 로그인연장_성공() throws Exception {
        // given
        userRepository.updateIsAuth(true, new Timestamp(new Date().getTime()), userUID);
        RefreshRequest refreshRequest = new RefreshRequest(refreshToken);

        // when
        ResultActions resultActions = mockMvc.perform(post(baseURL+"/login/refresh")
                        .header("Authorization", "Bearer "+accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshRequest))
                        .accept(MediaType.APPLICATION_JSON))
                .andDo(print());

        // then
        resultActions.andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.accessToken").exists());
    }

    @Test
    void 로그인재연장_성공() throws Exception {
        // first given
        userRepository.updateIsAuth(true, new Timestamp(new Date().getTime()), userUID);
        RefreshRequest refreshRequest = new RefreshRequest(refreshToken);

        // when
        ResultActions resultActions = mockMvc.perform(post(baseURL+"/login/refresh")
                        .header("Authorization", "Bearer "+accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshRequest))
                        .accept(MediaType.APPLICATION_JSON))
                .andDo(print());

        // then
        MvcResult mvcResult = resultActions.andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.accessToken").exists())
                .andReturn();

        JSONObject jsonObject = new JSONObject(mvcResult.getResponse().getContentAsString());
        accessToken = jsonObject.getJSONObject("data").getString("accessToken");

        // second given
        userRepository.updateIsAuth(true, new Timestamp(new Date().getTime()), userUID);
        RefreshRequest refreshRequest2 = new RefreshRequest(refreshToken);

        // when
        ResultActions resultActions2 = mockMvc.perform(post(baseURL+"/login/refresh")
                        .header("Authorization", "Bearer "+accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshRequest))
                        .accept(MediaType.APPLICATION_JSON))
                .andDo(print());

        // then
        resultActions2.andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.accessToken").exists());
    }

    @Test
    void 로그인연장_실패_refreshToken불일치() throws Exception {
        // given
        userRepository.updateIsAuth(true, new Timestamp(new Date().getTime()), userUID);
        RefreshRequest refreshRequest = new RefreshRequest(refreshToken+"_fail");

        // when
        ResultActions resultActions = mockMvc.perform(post(baseURL+"/login/refresh")
                        .header("Authorization", "Bearer "+accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshRequest))
                        .accept(MediaType.APPLICATION_JSON))
                .andDo(print());

        // then
        resultActions.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.location").value("refreshToken"));
    }

    @Test
    void 비정상accessToken_로그인필요() throws Exception {
        // given
        RefreshRequest refreshRequest = new RefreshRequest(refreshToken);

        // when
        ResultActions resultActions = mockMvc.perform(post(baseURL+"/login/refresh")
                        .header("Authorization", "Bearer "+accessToken+"_fail")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshRequest))
                        .accept(MediaType.APPLICATION_JSON))
                .andDo(print());

        // then
        resultActions.andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.location").value("auth"));
    }

    @Test
    void 비정상accessToken_미인증자() throws Exception {
        // given
        RefreshRequest refreshRequest = new RefreshRequest(refreshToken);

        // when
        ResultActions resultActions = mockMvc.perform(post(baseURL+"/login/refresh")
                        .header("Authorization", "Bearer "+accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshRequest))
                        .accept(MediaType.APPLICATION_JSON))
                .andDo(print());

        // then
        resultActions.andExpect(status().isForbidden())
                .andExpect(jsonPath("$.location").value("auth"));
    }

    @Test
    void 마이페이지_유저정보조회_성공() throws Exception {
        // given
        userRepository.updateIsAuth(true, new Timestamp(new Date().getTime()), userUID);

        // when
        ResultActions resultActions = mockMvc.perform(get(baseURL+"/mypage")
                        .header("Authorization", "Bearer "+accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON))
                .andDo(print());

        // then
        resultActions.andExpect(status().isOk())
                .andExpect(jsonPath("$.data.imgPath").exists())
                .andExpect(jsonPath("$.data.nickname").exists())
                .andExpect(jsonPath("$.data.totalStudyTime").exists())
                .andExpect(jsonPath("$.data.level").exists())
                .andExpect(jsonPath("$.data.percentage").exists());
    }

    @Test
    void 환경설정_유저정보조회_성공() throws Exception {
        // given
        userRepository.updateIsAuth(true, new Timestamp(new Date().getTime()), userUID);

        // when
        ResultActions resultActions = mockMvc.perform(get(baseURL)
                        .header("Authorization", "Bearer "+accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON))
                .andDo(print());

        // then
        resultActions.andExpect(status().isOk())
                .andExpect(jsonPath("$.data.imgPath").exists())
                .andExpect(jsonPath("$.data.nickname").exists());
    }

    @Test
    void 환경설정_유저정보변경_성공_이미지미변경() throws Exception {
        // given
        userRepository.updateIsAuth(true, new Timestamp(new Date().getTime()), userUID);

        ChangeUserInfoRequest changeUserInfoRequest = new ChangeUserInfoRequest("통합테스트_","123456789",false);
        MockMultipartFile json = new MockMultipartFile("json","json","application/json",
                objectMapper.writeValueAsString(changeUserInfoRequest).getBytes());

        // when
        ResultActions resultActions = mockMvc.perform(multipart(HttpMethod.PATCH,baseURL)
                        .file(json)
                        .header("Authorization", "Bearer "+accessToken))
                .andDo(print());

        // then
        resultActions.andExpect(status().isOk());
    }

    @Test
    void 환경설정_유저정보변경_성공_이미지변경() throws Exception{
        // given
        userRepository.updateIsAuth(true, new Timestamp(new Date().getTime()), userUID);

        Path root = Paths.get("image");
        String filePath = root.getFileName() + "/t1.jpg";
        FileInputStream fileInputStream = new FileInputStream(filePath);

        ChangeUserInfoRequest changeUserInfoRequest = new ChangeUserInfoRequest("통합테스트_","123456789",true);
        MockMultipartFile json = new MockMultipartFile("json","json","application/json",
                objectMapper.writeValueAsString(changeUserInfoRequest).getBytes());
        MockMultipartFile image = new MockMultipartFile("image", "r1.jpg", "img", fileInputStream);

        // when
        ResultActions resultActions = mockMvc.perform(multipart(HttpMethod.PATCH,baseURL)
                        .file(json)
                        .file(image)
                        .header("Authorization", "Bearer "+accessToken))
                .andDo(print());

        // then
        resultActions.andExpect(status().isOk());
    }

    @Test
    void 환경설정_유저정보변경_성공_랜덤이미지변경() throws Exception{
        // given
        userRepository.updateIsAuth(true, new Timestamp(new Date().getTime()), userUID);

        ChangeUserInfoRequest changeUserInfoRequest = new ChangeUserInfoRequest("통합테스트_","123456789",true);
        MockMultipartFile json = new MockMultipartFile("json","json","application/json",
                objectMapper.writeValueAsString(changeUserInfoRequest).getBytes());

        // when
        ResultActions resultActions = mockMvc.perform(multipart(HttpMethod.PATCH,baseURL)
                        .file(json)
                        .header("Authorization", "Bearer "+accessToken))
                .andDo(print());

        // then
        resultActions.andExpect(status().isOk());
    }

    @Test
    void 환경설정_유저정보변경_성공_비밀번호미변경() throws Exception{
        // given
        userRepository.updateIsAuth(true, new Timestamp(new Date().getTime()), userUID);

        ChangeUserInfoRequest changeUserInfoRequest = ChangeUserInfoRequest.builder()
                .nickname("통합테스트_")
                .changeImage(false)
                .build();

        MockMultipartFile json = new MockMultipartFile("json","json","application/json",
                objectMapper.writeValueAsString(changeUserInfoRequest).getBytes());

        // when
        ResultActions resultActions = mockMvc.perform(multipart(HttpMethod.PATCH,baseURL)
                        .file(json)
                        .header("Authorization", "Bearer "+accessToken))
                .andDo(print());

        // then
        resultActions.andExpect(status().isOk());
    }

    @Test
    void 환경설정_유저정보변경_실패_닉네임중복() throws Exception{
        // given
        userRepository.updateIsAuth(true, new Timestamp(new Date().getTime()), userUID);
        User user = User.builder()
                .email("gong40sa04_@gmail.com")
                .passwd(passwordEncoder.encode("12345678"))
                .nickname("통합테스트_")
                .level(1)
                .isAuth(true)
                .authCode("00000a")
                .imgPath("t1.jpg")
                .createdAt(new Timestamp(new Date().getTime()))
                .updatedAt(new Timestamp(new Date().getTime()))
                .build();
        userRepository.save(user).intValue();

        ChangeUserInfoRequest changeUserInfoRequest = new ChangeUserInfoRequest("통합테스트_","123456789",true);
        MockMultipartFile json = new MockMultipartFile("json","json","application/json",
                objectMapper.writeValueAsString(changeUserInfoRequest).getBytes());

        // when
        ResultActions resultActions = mockMvc.perform(multipart(HttpMethod.PATCH,baseURL)
                        .file(json)
                        .header("Authorization", "Bearer "+accessToken))
                .andDo(print());

        // then
        resultActions.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.location").value("nickname"));
    }

    @Test
    void 디바이스토큰변경_성공() throws Exception {
        // given
        userRepository.updateIsAuth(true, new Timestamp(new Date().getTime()), userUID);
        DeviceTokenRequest deviceTokenRequest = new DeviceTokenRequest("abcde");

        // when
        ResultActions resultActions = mockMvc.perform(patch(baseURL+"/device-token")
                        .header("Authorization", "Bearer "+accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(deviceTokenRequest))
                        .accept(MediaType.APPLICATION_JSON))
                .andDo(print());

        // then
        resultActions
                .andExpect(status().isOk());
    }
}