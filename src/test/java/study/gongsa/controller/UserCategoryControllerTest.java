package study.gongsa.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.filter.CharacterEncodingFilter;
import study.gongsa.domain.*;
import study.gongsa.dto.MakeAnswerDTO;
import study.gongsa.dto.UserCategoryRequest;
import study.gongsa.repository.*;
import study.gongsa.support.jwt.JwtTokenProvider;

import java.sql.Date;
import java.sql.Time;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
@AutoConfigureMockMvc
@SpringBootTest
@Slf4j
class UserCategoryControllerTest {
    private static String baseURL = "/api/user-category";

    private Integer userUID;

    private String accessToken;

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
    private UserCategoryRepository userCategoryRepository;

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
        // 리퀘스트 보내는 유저
        User user = User.builder()
                .email("gong40sa04@gmail.com")
                .passwd(passwordEncoder.encode("12345678"))
                .nickname("통합테스트")
                .authCode("00000a")
                .build();
        user.setIsAuth(true);
        userUID = userRepository.save(user).intValue();

        Integer userAuthUID = userAuthRepository.save(UserAuth.builder()
                .userUID(userUID)
                .refreshToken(jwtTokenProvider.makeRefreshToken(userUID))
                .build()).intValue();
        accessToken = jwtTokenProvider.makeAccessToken(userUID, userAuthUID);
        UserCategory userCategory1 = UserCategory.builder()
                .userUID(userUID)
                .categoryUID(1)
                .build();
        UserCategory userCategory2 = UserCategory.builder()
                .userUID(userUID)
                .categoryUID(2)
                .build();
        UserCategory userCategory3 = UserCategory.builder()
                .userUID(userUID)
                .categoryUID(3)
                .build();
        userCategoryRepository.save(userCategory1);
        userCategoryRepository.save(userCategory2);
        userCategoryRepository.save(userCategory3);
    }
    @Test
    void 사용자_카테고리등록_성공() throws Exception {
        // given
        ArrayList<Integer> categoryUIDs = new ArrayList<>(Arrays.asList(5,6,7));
        UserCategoryRequest userCategoryRequest = UserCategoryRequest.builder()
                .categoryUIDs(categoryUIDs)
                .build();

        // when
        ResultActions resultActions = mockMvc.perform(put(baseURL)
                        .header("Authorization", "Bearer "+accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(userCategoryRequest))
                        .accept(MediaType.APPLICATION_JSON))
                .andDo(print());

        // then
        resultActions
                .andExpect(status().isCreated());
        List<UserCategory> userCategories = userCategoryRepository.findByUserUID(userUID);
        assertThat(userCategories.size()).isEqualTo(categoryUIDs.size());
    }

    @Test
    void 사용자_카테고리조회_성공() throws Exception {
        // given
        // when
        ResultActions resultActions = mockMvc.perform(get(baseURL)
                        .header("Authorization", "Bearer "+accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON))
                .andDo(print());

        // then
        resultActions
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].userCategoryUID").exists())
                .andExpect(jsonPath("$.data[0].categoryUID").exists());
    }
}