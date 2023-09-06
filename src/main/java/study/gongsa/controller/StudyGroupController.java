package study.gongsa.controller;

import io.swagger.annotations.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import study.gongsa.domain.Category;
import study.gongsa.domain.LastStudyTimeInfo;
import study.gongsa.domain.StudyGroup;
import study.gongsa.dto.*;
import study.gongsa.service.CategoryService;
import study.gongsa.service.GroupMemberService;
import study.gongsa.service.StudyGroupService;
import study.gongsa.service.StudyMemberService;
import study.gongsa.support.exception.IllegalStateExceptionWithLocation;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;


@RestController
@CrossOrigin("*")
@Api(value="StudyGroup")
@RequestMapping("/api/study-group")
@RequiredArgsConstructor
public class StudyGroupController {
    private final StudyGroupService studyGroupService;
    private final GroupMemberService groupMemberService;
    private final StudyMemberService studyMemberService;
    private final CategoryService categoryService;

    @ApiOperation(value="스터디 그룹 정보 조회 (UID로 조회)")
    @ApiResponses({
            @ApiResponse(code=200, message="조회 성공"),
            @ApiResponse(code=400, message="존재하지 않는 그룹일 경우"),
            @ApiResponse(code=401, message="로그인을 하지 않았을 경우(header에 Authorization이 없을 경우)"),
            @ApiResponse(code=403, message="토큰 에러(토큰이 만료되었을 경우 등)")
    })
    @ApiImplicitParams({
            @ApiImplicitParam(name = "groupUID", value = "스터디그룹 UID", required = true, dataType = "int", paramType = "path", defaultValue = ""),
    })
    @GetMapping("/{groupUID}")
    public ResponseEntity findOneByUID(@PathVariable("groupUID") int groupUID){
        StudyGroup studyGroupInfo = studyGroupService.findOneByUID(groupUID);
        int currentMember = groupMemberService.findCurrentGroupMemberCnt(groupUID);
        List<Category> categories = categoryService.getStudyGroupCategory(groupUID);
        DefaultResponse response = new DefaultResponse(new GetStudyGroupInfoResponse(studyGroupInfo, categories, currentMember));
        return new ResponseEntity(response, HttpStatus.OK);
    }

    @ApiOperation(value="스터디 그룹 정보 조회 (code로 조회)")
    @ApiResponses({
            @ApiResponse(code=200, message="조회 성공"),
            @ApiResponse(code=400, message="존재하지 않는 그룹일 경우"),
            @ApiResponse(code=401, message="로그인을 하지 않았을 경우(header에 Authorization이 없을 경우)"),
            @ApiResponse(code=403, message="토큰 에러(토큰이 만료되었을 경우 등)")
    })
    @ApiImplicitParams({
            @ApiImplicitParam(name = "code", value = "스터디그룹 코드", required = true, dataType = "Striing", paramType = "path", defaultValue = ""),
    })
    @GetMapping("/code/{code}")
    public ResponseEntity findOneByCode(@PathVariable("code") String code){
        StudyGroup studyGroupInfo = studyGroupService.findOneByCode(code);
        int currentMember = groupMemberService.findCurrentGroupMemberCnt(studyGroupInfo.getUID());
        List<Category> categories = categoryService.getStudyGroupCategory(studyGroupInfo.getUID());
        DefaultResponse response = new DefaultResponse(new GetStudyGroupInfoResponse(studyGroupInfo, categories, currentMember));
        return new ResponseEntity(response, HttpStatus.OK);
    }

    @ApiOperation(value="스터디 그룹 조회")
    @ApiResponses({
            @ApiResponse(code=200, message="조회 성공"),
            @ApiResponse(code=401, message="로그인을 하지 않았을 경우(header에 Authorization이 없을 경우)"),
            @ApiResponse(code=403, message="토큰 에러(토큰이 만료되었을 경우 등)")
    })
    @ApiImplicitParams({
            @ApiImplicitParam(name = "categoryUIDs", value = "카테고리 UID 배열", required = false, dataType = "array", paramType = "query", defaultValue = ""),
            @ApiImplicitParam(name = "word", value = "검색어/코드", required = false, dataType = "string", paramType = "query", defaultValue = ""),
            @ApiImplicitParam(name = "isCam", value = "캠 유무", required = false, dataType = "boolean", paramType = "query", defaultValue = ""),
            @ApiImplicitParam(name = "align", value = "정렬 기준", required = false, dataType = "string", paramType = "query", defaultValue = "latest"),
    })
    @GetMapping("/search")
    public ResponseEntity findAll(@RequestParam(required = false) List<Integer> categoryUIDs,
                                  @RequestParam(required = false, defaultValue = "") String word,
                                  @RequestParam(required = false) Boolean isCam,
                                  @RequestParam(required = false, defaultValue = "latest") String align){
        List<StudyGroup> studyGroupList = studyGroupService.findAll(categoryUIDs, word, isCam, align);
        DefaultResponse response = new DefaultResponse(new SearchStudyGroupReponse(studyGroupList));
        return new ResponseEntity(response, HttpStatus.OK);
    }

    @ApiOperation(value="추천 스터디 그룹 조회 - 메인페이지/종료 임박했을 때 추천 페이지")
    @ApiResponses({
            @ApiResponse(code=200, message="추천 리스트 조회 성공"),
            @ApiResponse(code=401, message="로그인을 하지 않았을 경우(header에 Authorization이 없을 경우)"),
            @ApiResponse(code=403, message="가입되지 않은 그룹일 경우, 토큰 에러(토큰이 만료되었을 경우 등)")
    })
    @ApiImplicitParams({
            @ApiImplicitParam(name = "groupUID", value = "스터디룸 UID(type이 expire일 때만 입력)", required = false, dataType = "int", paramType = "query", example = "0"),
            @ApiImplicitParam(name = "type", value = "타입(main/expire)", required = true, dataType = "string", paramType = "query", defaultValue = "main")
    })
    @GetMapping("/recommend")
    public ResponseEntity findRecommendAll(@RequestParam(required = false) Integer groupUID,
                                           @RequestParam(required = true, defaultValue = "main") String type,
                                           HttpServletRequest request){
        int userUID = (int) request.getAttribute("userUID");
        List<StudyGroup> studyGroupList;
        if(type.equals("main"))
            studyGroupList = studyGroupService.findSameCategoryAllByUserUID(userUID);
        else {
            if(groupUID == null || !(type.equals("main") || type.equals("expire")))
                throw new IllegalStateExceptionWithLocation(HttpStatus.BAD_REQUEST, null,"파라미터(groupUID, type)를 다시 확인해주세요.");

            groupMemberService.findOne(groupUID, userUID);
            studyGroupList = studyGroupService.findSameCategoryAllByUID(groupUID);
        }
        DefaultResponse response = new DefaultResponse(new SearchStudyGroupReponse(studyGroupList));
        return new ResponseEntity(response, HttpStatus.OK);
    }

    @ApiOperation(value="나의 스터디 그룹 랭킹 조회")
    @ApiResponses({
            @ApiResponse(code=200, message="추천 리스트 조회 성공"),
            @ApiResponse(code=401, message="로그인을 하지 않았을 경우(header에 Authorization이 없을 경우)"),
            @ApiResponse(code=403, message="가입되지 않은 그룹일 경우, 토큰 에러(토큰이 만료되었을 경우 등)")
    })
    @GetMapping("/my-ranking")
    public ResponseEntity findMyStudyGroupRank(HttpServletRequest request){
        int userUID = (int) request.getAttribute("userUID");
        List<StudyGroup> groupList = studyGroupService.findMyStudyGroup(userUID);
        List<GetMyStudyGroupRankResponse.GroupRank> groupRankList = new ArrayList<GetMyStudyGroupRankResponse.GroupRank>();

        for(StudyGroup studyGroup : groupList){
            int groupUID = studyGroup.getUID();
            List<GroupMemberResponse.Member> memberList = groupMemberService.getMembers(groupUID);
            GetMyStudyGroupRankResponse.GroupRank groupRank = new GetMyStudyGroupRankResponse.GroupRank();
            groupRank.setGroupUID(groupUID);
            groupRank.setName(studyGroup.getName());
            groupRank.setMembers(memberList);
            groupRankList.add(groupRank);
        }

        DefaultResponse response = new DefaultResponse(new GetMyStudyGroupRankResponse(groupRankList));
        return new ResponseEntity(response, HttpStatus.OK);
    }

    @ApiOperation(value="나의 스터디 그룹 조회")
    @ApiResponses({
            @ApiResponse(code=200, message="추천 리스트 조회 성공"),
            @ApiResponse(code=401, message="로그인을 하지 않았을 경우(header에 Authorization이 없을 경우)"),
            @ApiResponse(code=403, message="가입되지 않은 그룹일 경우, 토큰 에러(토큰이 만료되었을 경우 등)")
    })
    @GetMapping("/my-group")
    public ResponseEntity findMyStudyGroup(HttpServletRequest request){
        int userUID = (int) request.getAttribute("userUID");
        List<StudyGroup> groupList = studyGroupService.findMyStudyGroup(userUID);

        DefaultResponse response = new DefaultResponse(new SearchStudyGroupReponse(groupList));
        return new ResponseEntity(response, HttpStatus.OK);
    }

    @ApiOperation(value="스터디 그룹 생성")
    @ApiResponses({
            @ApiResponse(code=201, message="스터디 그룹 생성"),
            @ApiResponse(code=400, message="하루 최대 공부시간 초과 / 이미 가입된 그룹 / 존재하지 않는 그룹"),
            @ApiResponse(code=401, message="로그인을 하지 않았을 경우(header에 Authorization이 없을 경우)"),
            @ApiResponse(code=403, message="토큰 에러(토큰이 만료되었을 경우 등)")
    })
    @PostMapping("")
    public ResponseEntity makeStudyGroup(@RequestPart("json") @Valid MakeStudyGroupRequest req,
                                         @RequestPart(value = "image", required = false) MultipartFile image, HttpServletRequest request){
        int userUID = (int) request.getAttribute("userUID");
        //userUID가 가입 가능한 최대 시간 구하기, 비교
        studyGroupService.checkPossibleMinStudyHourByUsersUID(userUID, req.getMinStudyHour());
        //카테고리UID가 올바른지 체크
        categoryService.checkValidCategoryUID(req.getCategoryUIDs());

        //그룹, 카테고리 생성
        StudyGroup studyGroup = MakeStudyGroupRequest.convertToStudyGroup(req);

        int groupUID = studyGroupService.makeStudyGroup(studyGroup, req.getCategoryUIDs());

        //방장 생성
        groupMemberService.makeStudyGroupMember(groupUID, userUID, true);

        //이미지 저장
        studyGroupService.saveGroupImage(groupUID, image);

        //그룹 UID return
        HashMap<String, Integer> makeStudyGroupResponse = new HashMap<>();
        makeStudyGroupResponse.put("groupUID", groupUID);
        DefaultResponse response = new DefaultResponse(makeStudyGroupResponse);
        return new ResponseEntity(response, HttpStatus.CREATED);
    }

    @ApiOperation(value="스터디 그룹 (현재) 공부 시간 및 정보 조회, 스터디 화면 정보 조회")
    @ApiResponses({
            @ApiResponse(code=200, message="스터디 화면 출력 정보 조회"),
            @ApiResponse(code=401, message="로그인을 하지 않았을 경우(header에 Authorization이 없을 경우)"),
            //@ApiResponse(code=403, message="가입되지 않은 그룹일 경우, 토큰 에러(토큰이 만료되었을 경우 등)")
    })
    @GetMapping("/{groupUID}/study-info")
    public ResponseEntity findStudyInfo(@PathVariable("groupUID") int groupUID){
        List<LastStudyTimeInfo> lastStudyTime = studyMemberService.findLastStudyTime(groupUID);
        List<LastStudyTimeInfoDTO> lastStudyTimeInfoDTOS = lastStudyTime.stream().map(t -> LastStudyTimeInfoDTO.convertTo(t)).collect(Collectors.toList());
        DefaultResponse response = new DefaultResponse(lastStudyTimeInfoDTOS);
        return new ResponseEntity(response, HttpStatus.OK);
    }
}
