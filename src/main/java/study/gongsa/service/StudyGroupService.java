package study.gongsa.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import study.gongsa.domain.GroupCategory;
import study.gongsa.domain.StudyGroup;
import study.gongsa.repository.GroupCategoryRepository;
import study.gongsa.repository.StudyGroupRepository;
import study.gongsa.support.mail.CodeGenerator;
import study.gongsa.support.exception.IllegalStateExceptionWithLocation;

import java.sql.Timestamp;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static java.util.Objects.isNull;

@Service
@Slf4j
@RequiredArgsConstructor
public class StudyGroupService {
    private final StudyGroupRepository studyGroupRepository;
    private final GroupCategoryRepository groupCategoryRepository;
    private final CodeGenerator codeGenerator;
    private final ImageService imageService;

    public List<StudyGroup> findAll(List<Integer> categoryUIDs, String word, Boolean isCam, String align){
        return studyGroupRepository.findAll(categoryUIDs, word, isCam, align);
    }

    public List<StudyGroup> findSameCategoryAllByUID(int uid){
        List<StudyGroup> studyGroupList = studyGroupRepository.findSameCategoryAllByUID(uid);
        if (studyGroupList.isEmpty())
            return studyGroupRepository.findAll(null, "", null, "random");
        else
            return studyGroupList;
    }

    public List<StudyGroup> findSameCategoryAllByUserUID(int userUID){
        List<StudyGroup> studyGroupList = studyGroupRepository.findSameCategoryAllByUserUID(userUID);
        if (studyGroupList.isEmpty())
            return studyGroupRepository.findAll(null, "", null, "random");
        else
            return studyGroupList;
    }

    public StudyGroup findOneByUID(int groupUID){
        Optional<StudyGroup> studyGroup = studyGroupRepository.findByUID(groupUID);
        if (studyGroup.isEmpty()){
            throw new IllegalStateExceptionWithLocation(HttpStatus.BAD_REQUEST, null,"존재하지 않은 그룹입니다.");
        }
        return studyGroup.get();
    }

    public StudyGroup findOneByCode(String code){
        Optional<StudyGroup> studyGroup = studyGroupRepository.findByCode(code);
        if (studyGroup.isEmpty()){
            throw new IllegalStateExceptionWithLocation(HttpStatus.BAD_REQUEST, null,"존재하지 않은 그룹입니다.");
        }
        return studyGroup.get();
    }

    public void checkPossibleMinStudyHourByUsersUID(int userUID, int addedMinStudyHour){
        Optional<Integer> userSumMinStudyHour = studyGroupRepository.findSumMinStudyHourByUserUID(userUID);
        int currentUserSumMinStudyHour = 0;
        if(userSumMinStudyHour.isPresent()) currentUserSumMinStudyHour = userSumMinStudyHour.get();
        if(currentUserSumMinStudyHour+addedMinStudyHour > 80){
            throw new IllegalStateExceptionWithLocation(HttpStatus.BAD_REQUEST,"minStudyHour","가입할 수 있는 최소 공부 시간(80시간)을 초과했습니다.");
        }
    }

    public int makeStudyGroup(StudyGroup studyGroup, int[] groupCategories) {
        // StudyGroup
        // make code
        String studyGroupCode = codeGenerator.generateRandomString(4)
                +"-"+codeGenerator.generateRandomString(4)
                +"-"+codeGenerator.generateRandomString(4);
        studyGroup.setCode(studyGroupCode);

        // createdAt, updatedAt
        Timestamp currentTime = new Timestamp(new Date().getTime());
        studyGroup.setCreatedAt(currentTime);
        studyGroup.setUpdatedAt(studyGroup.getCreatedAt());
        int groupUID = studyGroupRepository.save(studyGroup).intValue();

        // GroupCategory
        GroupCategory groupCategory = new GroupCategory(groupUID);
        groupCategory.setCreatedAt(currentTime);
        groupCategory.setUpdatedAt(groupCategory.getCreatedAt());
        for (int categoryUID : groupCategories){
            groupCategory.setCategoryUID(categoryUID);
            groupCategoryRepository.save(groupCategory);
        }
        return groupUID;
    }

    public int getMinStudyHourByGroupUID(int groupUID) {
        Optional<Integer> minStudyHour = studyGroupRepository.findMinStudyHourByGroupUID(groupUID);
        if(minStudyHour.isEmpty()) throw new IllegalStateExceptionWithLocation(HttpStatus.BAD_REQUEST, "groupUID", "존재하지 않는 그룹입니다.");
        return minStudyHour.get();
    }

    public void saveGroupImage (int uid, MultipartFile image){
        String fileName = "g" + uid + ".jpg"; //groupImage rename

        if( !isNull(image) && !image.isEmpty() ){ // 받은 이미지 저장
            imageService.save(image, fileName);
        }else{ //이미지 없으면 랜덤 이미지 지정
            fileName = "r"+codeGenerator.generateRandomNumber(1)+".jpg";
        }

        studyGroupRepository.updateImgPath(uid, fileName);
    }

    public int getMaxMember(int groupUID){
        Optional<Integer> maxMember = studyGroupRepository.findMaxMember(groupUID);
        if(maxMember.isEmpty()) throw new IllegalStateExceptionWithLocation(HttpStatus.BAD_REQUEST, "groupUID", "존재하지 않는 그룹입니다.");
        return maxMember.get();
    }

    public List<StudyGroup> findMyStudyGroup(int userUID){
        return studyGroupRepository.findMyStudyGroup(userUID);
    }

    public void deleteExpiredGroup(){
        studyGroupRepository.removeExpiredGroup();
    }
}
