package study.gongsa.controller;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import study.gongsa.domain.UserCategory;
import study.gongsa.dto.DefaultResponse;
import study.gongsa.dto.UserCategoryDTO;
import study.gongsa.dto.UserCategoryRequest;
import study.gongsa.service.UserCategoryService;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.util.ArrayList;
import java.util.List;

@RestController
@CrossOrigin("*")
@Api(value="UserCategory")
@RequestMapping("/api/user-category")
@RequiredArgsConstructor
public class UserCategoryController {
    private final UserCategoryService userCategoryService;

    @ApiOperation(value="사용자 카테고리 등록")
    @ApiResponses({
            @ApiResponse(code=201, message="등록 완료"),
            @ApiResponse(code=401, message="로그인을 하지 않았을 경우(header에 Authorization이 없을 경우)"),
            @ApiResponse(code=403, message="토큰 에러(토큰이 만료되었을 경우 등)")
    })
    @PutMapping("")
    public ResponseEntity save(@RequestBody @Valid UserCategoryRequest req, HttpServletRequest request){
        int userUID = (int) request.getAttribute("userUID");
        userCategoryService.remove(userUID);
        ArrayList<Integer> categoryUIDs = req.getCategoryUIDs();
        for(int i=0; i<categoryUIDs.size(); i++) {
            userCategoryService.save(new UserCategory(userUID, categoryUIDs.get(i)));
        }

        DefaultResponse response = new DefaultResponse();
        return new ResponseEntity(response, HttpStatus.CREATED);
    }

    @ApiOperation(value="사용자 카테고리 조회")
    @ApiResponses({
            @ApiResponse(code=201, message="등록 완료"),
            @ApiResponse(code=401, message="로그인을 하지 않았을 경우(header에 Authorization이 없을 경우)"),
            @ApiResponse(code=403, message="토큰 에러(토큰이 만료되었을 경우 등)")
    })
    @GetMapping("")
    public ResponseEntity findAll(HttpServletRequest request){
        int userUID = (int) request.getAttribute("userUID");
        List<UserCategoryDTO> userCategoryDTOS = userCategoryService.findAll(userUID);
        DefaultResponse response = new DefaultResponse(userCategoryDTOS);
        return new ResponseEntity(response, HttpStatus.OK);
    }
}
