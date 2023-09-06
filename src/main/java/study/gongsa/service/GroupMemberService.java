package study.gongsa.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import study.gongsa.domain.GroupMember;
import study.gongsa.domain.GroupMemberUserInfo;
import study.gongsa.domain.MemberWeeklyTimeInfo;
import study.gongsa.dto.GroupMemberResponse;
import study.gongsa.repository.GroupMemberRepository;
import study.gongsa.repository.StudyGroupRepository;
import study.gongsa.repository.StudyMemberRepository;
import study.gongsa.support.exception.IllegalStateExceptionWithLocation;

import java.sql.Timestamp;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class GroupMemberService {
    private final GroupMemberRepository groupMemberRepository;
    private final StudyGroupRepository studyGroupRepository;

    public void checkAlreadyRegister(int groupUID, int userUID) {
        Optional<GroupMember> groupMember = groupMemberRepository.findByGroupUIDUserUID(groupUID, userUID);
        if (groupMember.isPresent()){
            throw new IllegalStateExceptionWithLocation(HttpStatus.BAD_REQUEST, "groupUID","이미 가입된 그룹입니다.");
        }
    }

    public GroupMember findOne(int groupUID, int userUID) {
        Optional<GroupMember> groupMember = groupMemberRepository.findByGroupUIDUserUID(groupUID, userUID);
        if (groupMember.isEmpty()){
            throw new IllegalStateExceptionWithLocation(HttpStatus.FORBIDDEN, "groupUID","가입되지 않은 그룹입니다.");
        }

        return groupMember.get();
    }

    public void remove(GroupMember groupMember){
        int groupUID = groupMember.getGroupUID();
        int groupMemberUID = groupMember.getUID();
        boolean isLeader = groupMember.getIsLeader();

        groupMemberRepository.remove(groupMemberUID);

        if (isLeader) {
            Optional<GroupMember> randomGroupMember = groupMemberRepository.findRandUID(groupUID);
            if (!randomGroupMember.isEmpty()){
                groupMemberRepository.updateNewReader(randomGroupMember.get().getUID());
            }
        }
    }

    public void removeForced(List<Integer> groupMemberUIDs){
        groupMemberRepository.removeForced(groupMemberUIDs);
    }

    public void makeStudyGroupMember(int groupUID, int userUID, boolean isLeader) {
        GroupMember groupMember = GroupMember.builder()
                .userUID(userUID)
                .groupUID(groupUID)
                .isLeader(isLeader)
                .build();

        groupMemberRepository.save(groupMember);
    }

    public void checkCurrentGroupMemberCnt(int groupUID) {
        Optional<Map<String, Integer>> memberCntInfo = studyGroupRepository.findMemberCntInfoByGroupUID(groupUID);
        if(memberCntInfo.isEmpty()){
            throw new IllegalStateExceptionWithLocation(HttpStatus.BAD_REQUEST, "groupUID", "존재하지 않는 그룹입니다.");
        }
        if(memberCntInfo.get().get("memberCnt").equals(memberCntInfo.get().get("maxMember"))){
            throw new IllegalStateExceptionWithLocation(HttpStatus.BAD_REQUEST, "groupMember", "그룹 인원이 다 찼습니다.");
        }
    }

    public int findCurrentGroupMemberCnt(int groupUID) {
        Optional<Map<String, Integer>> memberCntInfo = studyGroupRepository.findMemberCntInfoByGroupUID(groupUID);
        return memberCntInfo.get().get("memberCnt");
    }

    public List<GroupMemberResponse.Member> getMembers(int groupUID){
        List<GroupMemberUserInfo> memberInfoList = groupMemberRepository.findMemberInfo(groupUID);

        List<GroupMemberResponse.Member> members = new ArrayList<>();
        for(GroupMemberUserInfo memberInfo : memberInfoList){
            members.add(GroupMemberResponse.Member.convertToMember(memberInfo));
        }
        return members;
    }

    public List<MemberWeeklyTimeInfo> updatePenalty() {
        List<MemberWeeklyTimeInfo> memberToStudyLess = getMemberToStudyLess();

        groupMemberRepository.updatePenalty(memberToStudyLess.stream()
                .filter(info -> info.getIsPenalty()) // 벌점 기준 존재 그룹만
                .map(MemberWeeklyTimeInfo::getGroupMemberUID)
                .collect(Collectors.toList()));

        return memberToStudyLess; // 벌점 받은 멤버들
    }

    public List<MemberWeeklyTimeInfo> getMemberToStudyLess(){
        return groupMemberRepository.getMemberWeeklyStudyTimeInfo()
                .stream()
                .filter(info -> info.getAddPenalty())
                .collect(Collectors.toList());
    }
}
