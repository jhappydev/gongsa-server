package study.gongsa.controller;

import io.swagger.annotations.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import study.gongsa.domain.*;
import study.gongsa.dto.*;
import study.gongsa.service.AnswerService;
import study.gongsa.service.QuestionService;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.util.ArrayList;
import java.util.List;

@RestController
@CrossOrigin("*")
@Api(value="Question")
@RequestMapping("/api/question")
@RequiredArgsConstructor
public class QuestionController {
    private final QuestionService questionService;
    private final AnswerService answerService;

    @ApiOperation(value="나의 질문 모아보기")
    @ApiResponses({
            @ApiResponse(code=200, message="조회 성공"),
            @ApiResponse(code=401, message="로그인을 하지 않았을 경우(header에 Authorization이 없을 경우)"),
            @ApiResponse(code=403, message="토큰 에러(토큰이 만료되었을 경우 등)")
    })
    @GetMapping("/my-question")
    public ResponseEntity findMyQuestion(HttpServletRequest request){
        int userUID = (int) request.getAttribute("userUID");
        List<QuestionInfo> questionList = questionService.findMyQuestion(userUID);
        DefaultResponse response = new DefaultResponse(new GetQuestionResponse(questionList));
        return new ResponseEntity(response, HttpStatus.OK);
    }

    @ApiOperation(value="스터디그룹 질문 모아보기")
    @ApiResponses({
            @ApiResponse(code=200, message="조회 성공"),
            @ApiResponse(code=401, message="로그인을 하지 않았을 경우(header에 Authorization이 없을 경우)"),
            @ApiResponse(code=403, message="토큰 에러(토큰이 만료되었을 경우 등)")
    })
    @GetMapping("/group-question/{groupUID}")
    public ResponseEntity findGroupQuestion(@PathVariable("groupUID") int groupUID, HttpServletRequest request){
        int userUID = (int) request.getAttribute("userUID");
        List<QuestionInfo> questionList = questionService.findGroupQuestion(userUID, groupUID);
        DefaultResponse response = new DefaultResponse(new GetQuestionResponse(questionList));
        return new ResponseEntity(response, HttpStatus.OK);
    }

    @ApiOperation(value="질문 상세보기")
    @ApiResponses({
            @ApiResponse(code=200, message="조회 성공"),
            @ApiResponse(code=401, message="로그인을 하지 않았을 경우(header에 Authorization이 없을 경우)"),
            @ApiResponse(code=403, message="토큰 에러(토큰이 만료되었을 경우 등)")
    })
    @GetMapping("/{questionUID}")
    public ResponseEntity findOne(@PathVariable("questionUID") int questionUID){
        Question question = questionService.findOne(questionUID);
        List<AnswerInfo> answerList = answerService.findAnswerByQuestionUID(questionUID);
        DefaultResponse response = new DefaultResponse(new GetQuestionInfoResponse(question, answerList));
        return new ResponseEntity(response, HttpStatus.OK);
    }

    @ApiOperation(value="질문글 등록")
    @ApiResponses({
            @ApiResponse(code=201, message="등록 완료"),
            @ApiResponse(code=400, message="가입되지 않은 그룹인 경우"),
            @ApiResponse(code=401, message="로그인을 하지 않았을 경우(header에 Authorization이 없을 경우)"),
            @ApiResponse(code=403, message="토큰 에러(토큰이 만료되었을 경우 등)")
    })
    @PostMapping("")
    @Transactional
    public ResponseEntity save(@RequestBody @Valid MakeQuestionDTO.Request req, HttpServletRequest request){
        int userUID = (int) request.getAttribute("userUID");
        int questionUID = questionService.makeQuestion(userUID, req.getGroupUID(), req.getTitle(), req.getContent());

        DefaultResponse response = new DefaultResponse(MakeQuestionDTO.Response
                .builder()
                .questionUID(questionUID)
                .build());
        return new ResponseEntity(response, HttpStatus.CREATED);
    }
}

