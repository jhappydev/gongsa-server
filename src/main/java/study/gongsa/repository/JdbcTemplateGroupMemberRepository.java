package study.gongsa.repository;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.stereotype.Repository;
import study.gongsa.domain.*;

import javax.sql.DataSource;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Repository
@Slf4j
public class JdbcTemplateGroupMemberRepository implements GroupMemberRepository{

    private final JdbcTemplate jdbcTemplate;
    private SimpleJdbcInsert insertIntoGroupMember;

    @Autowired
    public JdbcTemplateGroupMemberRepository(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        insertIntoGroupMember = new SimpleJdbcInsert(jdbcTemplate).withTableName("GroupMember").usingGeneratedKeyColumns("UID");
    }

    @Override
    public Number save(GroupMember groupMember) {
        final Map<String, Object> parameters = setParameter(groupMember);
        return insertIntoGroupMember.executeAndReturnKey(parameters);
    }

    @Override
    public Optional<GroupMember> findByGroupUIDUserUID(int groupUID, int userUID){
        List<GroupMember> result = jdbcTemplate.query("SELECT * FROM GroupMember WHERE groupUID = ? and userUID = ?", groupMemberRowMapper(), groupUID, userUID);
        return result.stream().findAny();
    }

    @Override
    public void remove(int uid) {
        String sql = "DELETE FROM GroupMember WHERE UID = ?";
        jdbcTemplate.update(sql, uid);
    }

    @Override
    public void removeForced(List<Integer> groupMemberUIDs) {
        String inSql = String.join(",", Collections.nCopies(groupMemberUIDs.size(), "?"));
        String query = String.format("DELETE FROM GroupMember WHERE UID in (%s)", inSql);
        jdbcTemplate.update(query, groupMemberUIDs.toArray());
    }

    @Override
    public Optional<GroupMember> findRandUID(int groupUID){
        String sql = "SELECT * "
                    + "FROM GroupMember "
                    + "WHERE groupUID = ? "
                    + "ORDER BY RAND() "
                    + "LIMIT 1";
        List<GroupMember> result = jdbcTemplate.query(sql, groupMemberRowMapper(), groupUID);
        return result.stream().findAny();
    }

    @Override
    public void updateNewReader(int uid){
        String sql = "UPDATE GroupMember SET isLeader = 1, updatedAt=now() WHERE UID = ?";
        jdbcTemplate.update(sql, uid);
    }

    @Override
    public List<GroupMemberUserInfo> findMemberInfo(int groupUID){
        String with = "WITH MemberInfo AS ( " +
                "SELECT gm.UID AS groupMemberUID, u.UID AS userUID, u.nickname AS nickname, u.imgPath AS imgPath " +
                "FROM GroupMember gm " +
                "INNER JOIN User u ON gm.userUID = u.UID " +
                "WHERE gm.groupUID = ?) ";
        String subQuery1 = "SELECT DENSE_RANK() OVER (ORDER BY IFNULL(SUM(sm.studyTime),0) DESC) AS ranking, " +
                "IFNULL(TIME(SUM(sm.studyTime)),TIME(0)) AS totalStudyTime, mi.userUID " +
                "FROM MemberInfo mi " +
                "LEFT JOIN StudyMember sm ON sm.groupMemberUID = mi.groupMemberUID " +
                "GROUP BY mi.groupMemberUID ";
        String subQuery2 = "SELECT IFNULL(sm.studyStatus, 'inactive') AS studyStatus, MAX(sm.updatedAt) AS updatedAt, " +
                "mi.userUID, mi.nickname, mi.imgPath " +
                "FROM MemberInfo mi " +
                "LEFT JOIN StudyMember sm ON sm.groupMemberUID = mi.groupMemberUID " +
                "GROUP BY mi.userUID ";

        String query = with + "SELECT * FROM ( " + subQuery1 + ") AS studyTimeTable  " +
                "INNER JOIN ( "+ subQuery2 +") AS studyStatusTable ON studyTimeTable.userUID = studyStatusTable.userUID "+
                "ORDER BY studyTimeTable.ranking ";

        List<GroupMemberUserInfo> result = jdbcTemplate.query(query, groupMemberUserInfoRowMapper(), groupUID);
        return result.stream().collect(Collectors.toList());
    }

    @Override
    public List<MemberWeeklyTimeInfo> getMemberWeeklyStudyTimeInfo() {
        String groupMemberInfoQuery = "SELECT sg.UID as groupUID, sg.name as groupName, gm.UID as groupMemberUID, gm.userUID, sg.minStudyHour, sg.isPenalty, sg.maxPenalty, gm.penaltyCnt " +
                "FROM StudyGroup sg " +
                "INNER JOIN GroupMember gm " +
                "ON sg.UID = gm.groupUID " +
                "WHERE TIMESTAMPDIFF(DAY, sg.createdAt , now()) >= 7 " +
                "AND TIMESTAMPDIFF(DAY, gm.createdAt , now()) >= 7 ";
        String studyInfoQuery = "SELECT sm.UID as studyMemberUID, groupMemberUID, TIME(SUM(studyTime)) as studyHour " +
                "FROM StudyMember sm " +
                "WHERE TIMESTAMPDIFF(DAY, updatedAt, now()) < 7 " +
                "AND TIMESTAMPDIFF(HOUR, updatedAt, now()) > 1 " +
                "GROUP BY groupMemberUID ";

        String query = "SELECT GroupMemberInfo.userUID, GroupMemberInfo.groupMemberUID, GroupMemberInfo.groupUID, GroupMemberInfo.groupName, GroupMemberInfo.minStudyHour, IFNULL(StudyInfo.studyHour, TIME(0)) as studyHour, " +
                "GroupMemberInfo.isPenalty, GroupMemberInfo.maxPenalty, GroupMemberInfo.PenaltyCnt as currentPenalty, " +
                "IF(TIMEDIFF(GroupMemberInfo.minStudyHour, StudyInfo.studyHour)>=TIME(0),FALSE,TRUE) as addPenalty " +
                "FROM ("+ groupMemberInfoQuery +") GroupMemberInfo " +
                "LEFT JOIN ( " + studyInfoQuery + ") StudyInfo " +
                "ON GroupMemberInfo.groupMemberUID = StudyInfo.groupMemberUID ";

        List<MemberWeeklyTimeInfo> result = jdbcTemplate.query(query, memberWeeklyTimeInfoRowMapper());
        return result.stream().collect(Collectors.toList());
    }

    @Override
    public void updatePenalty(List<Integer> UIDs) {
        String inSql = String.join(",", Collections.nCopies(UIDs.size(), "?"));
        String sql = "UPDATE GroupMember SET penaltyCnt=(penaltyCnt+1), updatedAt= now() WHERE UID IN (%s)";

        jdbcTemplate.update(
                String.format(sql, inSql),
                UIDs.toArray());
    }

    private RowMapper<GroupMember> groupMemberRowMapper() {
        return (rs, rowNum) -> {
            GroupMember groupMember = new GroupMember();
            groupMember.setUID(rs.getInt("UID"));
            groupMember.setGroupUID(rs.getInt("groupUID"));
            groupMember.setUserUID(rs.getInt("userUID"));
            groupMember.setPenaltyCnt(rs.getInt("penaltyCnt"));
            groupMember.setReportCnt(rs.getInt("reportCnt"));
            groupMember.setIsLeader(rs.getBoolean("isLeader"));
            groupMember.setCreatedAt(rs.getTimestamp("createdAt"));
            groupMember.setUpdatedAt(rs.getTimestamp("updatedAt"));
            return groupMember;
        };
    }

    private RowMapper<GroupMemberUserInfo> groupMemberUserInfoRowMapper() {
        return (rs, rowNum) -> {
            GroupMemberUserInfo groupMemberUserInfo = new GroupMemberUserInfo();
            groupMemberUserInfo.setUserUID(rs.getInt("userUID"));
            groupMemberUserInfo.setImgPath(rs.getString("imgPath"));
            groupMemberUserInfo.setNickname(rs.getString("nickname"));
            groupMemberUserInfo.setRanking(rs.getInt("ranking"));
            groupMemberUserInfo.setStudyStatus(rs.getString("studyStatus"));
            groupMemberUserInfo.setTotalStudyTime(rs.getTime("totalStudyTime"));

            return groupMemberUserInfo;
        };
    }

    private RowMapper<MemberWeeklyTimeInfo> memberWeeklyTimeInfoRowMapper() {
        return (rs, rowNum) -> {
            MemberWeeklyTimeInfo memberWeeklyTimeInfo = new MemberWeeklyTimeInfo();
            memberWeeklyTimeInfo.setUserUID(rs.getInt("userUID"));
            memberWeeklyTimeInfo.setGroupMemberUID(rs.getInt("groupMemberUID"));

            memberWeeklyTimeInfo.setGroupUID(rs.getInt("groupUID"));
            memberWeeklyTimeInfo.setGroupName(rs.getString("groupName"));
            memberWeeklyTimeInfo.setMinStudyHour(rs.getString("minStudyHour"));
            memberWeeklyTimeInfo.setIsPenalty(rs.getBoolean("isPenalty"));
            memberWeeklyTimeInfo.setMaxPenalty(rs.getInt("maxPenalty"));

            memberWeeklyTimeInfo.setStudyHour(rs.getString("studyHour"));
            memberWeeklyTimeInfo.setCurrentPenalty(rs.getInt("currentPenalty"));
            memberWeeklyTimeInfo.setAddPenalty(rs.getBoolean("addPenalty"));

            return memberWeeklyTimeInfo;
        };
    }

    private HashMap<String, Object> setParameter(GroupMember groupMember) {
        HashMap<String, Object> hashMap = new HashMap<String, Object>();
        hashMap.put("UID", groupMember.getUID());
        hashMap.put("groupUID",groupMember.getGroupUID());
        hashMap.put("userUID",groupMember.getUserUID());
        hashMap.put("penaltyCnt",groupMember.getPenaltyCnt());
        hashMap.put("reportCnt",groupMember.getReportCnt());
        hashMap.put("isLeader",groupMember.getIsLeader());
        hashMap.put("createdAt",groupMember.getCreatedAt());
        hashMap.put("updatedAt",groupMember.getUpdatedAt());
        return hashMap;
    }
}
