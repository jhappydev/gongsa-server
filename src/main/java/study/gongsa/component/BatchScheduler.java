package study.gongsa.component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import study.gongsa.domain.MemberWeeklyTimeInfo;
import study.gongsa.service.FirebaseCloudMessageService;
import study.gongsa.service.GroupMemberService;
import study.gongsa.service.StudyGroupService;
import study.gongsa.service.UserService;

import java.sql.Timestamp;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Component
@Slf4j
@RequiredArgsConstructor
public class BatchScheduler {
    private final GroupMemberService groupMemberService;
    private final StudyGroupService studyGroupService;
    private final UserService userService;
    private final FirebaseCloudMessageService firebaseCloudMessageService;

    @Async
    @Scheduled(cron = "0 0 1 * * ?", zone   = "Asia/Seoul") // 매일 오전 1시에
    public void deleteExpiredUnauthenticatedUser() {
        log.info("deleteExpiredUnauthenticatedUser() 실행");
        userService.deleteExpiredUnauthenticatedUser();
        log.info("deleteExpiredUnauthenticatedUser() 종료");
    }

    @Async
    @Scheduled(cron = "0 0 1 * * ?", zone   = "Asia/Seoul") // 매일 오전 1시에
    public void deleteExpiredStudyGroup() {
        log.info("deleteExpiredStudyGroup() 실행");
        studyGroupService.deleteExpiredGroup();
        log.info("deleteExpiredStudyGroup() 종료");
    }

    @Async
    @Transactional
    @Scheduled(cron = "0 0 1 * * MON", zone   = "Asia/Seoul") // 매주 월요일 오전 1시에
    public void addPenaltyAndWidthDrawGroupMember() {
        log.info("addPenaltyAndWidthDrawGroupMember() 실행");

        List<MemberWeeklyTimeInfo> memberToStudyLess = groupMemberService.updatePenalty();
        List<Integer> memberUIDsToWithDraw = memberToStudyLess.stream()
                .filter(info -> (info.getIsPenalty()) && (info.getMaxPenalty() < (info.getCurrentPenalty()+1))) // 최대 벌점 초과
                .map(MemberWeeklyTimeInfo::getGroupMemberUID)
                .collect(Collectors.toList());

        // membersToWithdraw 퇴장 & push 알림 & 레벨 다운
        memberToStudyLess.stream().forEach(memberInfo -> {
            int userUID = memberInfo.getUserUID();
            String fcmToken = userService.getDeviceToken(userUID);
            String groupName = memberInfo.getGroupName();
            String studyHour = memberInfo.getStudyHour();
            int maxPenalty = memberInfo.getMaxPenalty();
            int currentPenalty = memberInfo.getCurrentPenalty();
            if(memberInfo.getIsPenalty()){
                //벌점 받는 멤버들
                if(memberInfo.getMaxPenalty() < (memberInfo.getCurrentPenalty()+1)){
                    // 강퇴당하는 멤버
                    userService.downLevel(userUID);
                    groupMemberService.removeForced(memberUIDsToWithDraw);
                    firebaseCloudMessageService.sendMessageTo(fcmToken, "["+groupName+"] 알림", "지난 주에 " + studyHour + "시간 공부해서 주 목표 공부시간을 채우지 못해 퇴장되었습니다. (벌점: " + currentPenalty + "/" + maxPenalty + ")");
                }else{
                    // 벌점만 받는 멤버
                    firebaseCloudMessageService.sendMessageTo(fcmToken, "["+groupName+"] 알림", "지난 주에 " + studyHour + "시간 공부해서 주 목표 공부시간을 채우지 못해 벌점을 받았습니다. (벌점: " + currentPenalty + "/" + maxPenalty + ")");
                }
            }else{
                //단순 시간 못채운 멤버
                firebaseCloudMessageService.sendMessageTo(fcmToken, "["+groupName+"] 알림", "지난 주에 " + studyHour + "시간 공부해서 주 목표 공부시간을 채우지 못했습니다.");
            }
        });
        log.info("퇴장할 멤버: {}", memberUIDsToWithDraw);

        log.info("addPenaltyAndWidthDrawGroupMember() 종료");
    }
}
