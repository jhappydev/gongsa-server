package study.gongsa.repository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import study.gongsa.domain.LastStudyTimeInfo;

import java.util.List;

@Repository
public class JdbcTemplateStudyMemberRepository implements StudyMemberRepository{
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public JdbcTemplateStudyMemberRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void remove(int groupUID, int userUID, int groupMemberUID) {
        String sql = "DELETE FROM StudyMember WHERE groupUID = ? AND userUID = ? AND groupMemberUID = ?";
        jdbcTemplate.update(sql, groupUID, userUID, groupMemberUID);
    }

    @Override
    public List<LastStudyTimeInfo> findLastStudyTime(int groupUID){
        String sql = "SELECT distinctrow U.UID as userUID, U.imgPath, GM.UID as groupMemberUID, SM.studyStatus, IFNULL(TIME(SM.studyTime), TIME(0)) as studyTime " +
                "FROM GroupMember GM INNER JOIN StudyMember SM on GM.UID = SM.groupMemberUID " +
                "INNER JOIN User U on GM.userUID = U.UID " +
                "WHERE (SM.UID, SM.createdAt) in (select UID, max(createdAt) from StudyMember group by UID) " +
                "AND SM.studyStatus NOT LIKE ('inactive') AND GM.groupUID = ?";
        List<LastStudyTimeInfo> result = jdbcTemplate.query(sql, lastStudyTimeInfoRowMapper(), groupUID);
        return result;
    }

    private RowMapper<LastStudyTimeInfo> lastStudyTimeInfoRowMapper() { // Question, Answer join
        return (rs, rowNum) -> {
            LastStudyTimeInfo lastStudyTimeInfo = new LastStudyTimeInfo();
            lastStudyTimeInfo.setUserUID(rs.getInt("userUID"));
            lastStudyTimeInfo.setGroupMemberUID(rs.getInt("groupMemberUID"));
            lastStudyTimeInfo.setImgPath(rs.getString("imgPath"));
            lastStudyTimeInfo.setStudyStatus(rs.getString("studyStatus"));
            lastStudyTimeInfo.setStudyTime(rs.getTime("studyTime"));
            return lastStudyTimeInfo;
        };
    }
}
