package study.gongsa.controller;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import study.gongsa.dto.DefaultResponse;
import study.gongsa.dto.MakeAnswerDTO;
import study.gongsa.dto.MakeQuestionDTO;
import study.gongsa.dto.UpdateAnswerDTO;
import study.gongsa.service.AnswerService;
import study.gongsa.service.QuestionService;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;

@RestController
@CrossOrigin("*")
@Api(value="Answer")
@RequestMapping("/api/answer")
@Slf4j
@RequiredArgsConstructor
public class AnswerController {
    private final AnswerService answerService;

    @ApiOperation(value="답변글 등록")
    @ApiResponses({
            @ApiResponse(code=201, message="등록 완료"),
            @ApiResponse(code=400, message="가입되지 않은 그룹인 경우"),
            @ApiResponse(code=401, message="로그인을 하지 않았을 경우(header에 Authorization이 없을 경우)"),
            @ApiResponse(code=403, message="토큰 에러(토큰이 만료되었을 경우 등)")
    })
    @PostMapping("")
    @Transactional
    public ResponseEntity save(@RequestBody @Valid MakeAnswerDTO.Request req, HttpServletRequest request){
        int userUID = (int) request.getAttribute("userUID");
        answerService.makeAnswer(userUID, req.getQuestionUID(), req.getContent());

        DefaultResponse response = new DefaultResponse(MakeAnswerDTO.Response
                .builder()
                .questionUID(req.getQuestionUID())
                .build());
        return new ResponseEntity(response, HttpStatus.CREATED);
    }

    @ApiOperation(value="답변글 수정")
    @ApiResponses({
            @ApiResponse(code=200, message="수정 완료"),
            @ApiResponse(code=400, message="존재하지 않는 답변인 경우"),
            @ApiResponse(code=401, message="로그인을 하지 않았을 경우(header에 Authorization이 없을 경우)"),
            @ApiResponse(code=403, message="토큰 에러(토큰이 만료되었을 경우 등)")
    })
    @PatchMapping("")
    @Transactional
    public ResponseEntity update(@RequestBody @Valid UpdateAnswerDTO.Request req, HttpServletRequest request){
        int userUID = (int) request.getAttribute("userUID");
        int questionUID = answerService.getQuestionUIDByAnswerUID(userUID, req.getAnswerUID());
        answerService.updateAnswer(req.getAnswerUID(), req.getContent());

        DefaultResponse response = new DefaultResponse(MakeAnswerDTO.Response
                .builder()
                .questionUID(questionUID)
                .build());
        return new ResponseEntity(response, HttpStatus.OK);
    }

    @ApiOperation(value="답변글 삭제")
    @ApiResponses({
            @ApiResponse(code=200, message="수정 완료"),
            @ApiResponse(code=400, message="존재하지 않는 답변인 경우"),
            @ApiResponse(code=401, message="로그인을 하지 않았을 경우(header에 Authorization이 없을 경우)"),
            @ApiResponse(code=403, message="토큰 에러(토큰이 만료되었을 경우 등)")
    })
    @DeleteMapping("/{answerUID}")
    @Transactional
    public ResponseEntity delete(@PathVariable("answerUID") int answerUID, HttpServletRequest request){
        int userUID = (int) request.getAttribute("userUID");
        answerService.deleteAnswer(answerUID, userUID);

        DefaultResponse response = new DefaultResponse();
        return new ResponseEntity(response, HttpStatus.NO_CONTENT);
    }
}
