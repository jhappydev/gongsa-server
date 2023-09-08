package study.gongsa.repository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.stereotype.Repository;
import study.gongsa.domain.StudyGroup;
import study.gongsa.domain.User;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class JdbcTemplateStudyGroupRepository implements StudyGroupRepository{
    private final JdbcTemplate jdbcTemplate;
    private SimpleJdbcInsert insertIntoStudyGroupAuth;

    @Autowired
    public JdbcTemplateStudyGroupRepository(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        insertIntoStudyGroupAuth = new SimpleJdbcInsert(jdbcTemplate).withTableName("StudyGroup").usingGeneratedKeyColumns("UID");
    }

    @Override
    public Number save(StudyGroup studyGroup) {
        final Map<String, Object> parameters = setParameter(studyGroup);
        Number UID = insertIntoStudyGroupAuth.executeAndReturnKey(parameters);
        updateMinStudyHour(UID.intValue(), studyGroup.getMinStudyHour());
        return UID;
    }

    @Override
    public List<StudyGroup> findAll(List<Integer> categoryUIDs, String word, Boolean isCam, String align) {
        String sql = "select * "
                    +"from StudyGroup a "
                    +"join GroupCategory b on a.UID = b.groupUID "
                    +"where a.UID >= 1 and a.isPrivate = false ";
        if(isCam != null)
            sql += "and isCam = " + isCam + " ";
        if(word.length() != 0)
            sql += "and (a.code LIKE \"%" + word + "%\" or a.name LIKE \"" + word + "%\")";
        if(categoryUIDs != null){
            sql += "and b.categoryUID in (";
            for(int i=0; i<categoryUIDs.size(); i++){
                sql += categoryUIDs.get(i).toString();
                if(i != categoryUIDs.size()-1)
                    sql += ",";
            }
            sql += ")";
        }

        sql += "group by b.groupUID ";

        switch(align){
            case "latest":
                sql += "order by a.createdAt desc";
                break;
            case "expire":
                sql += "order by a.expiredAt";
                break;
            case "random":
                sql += "order by rand()";
        }

        return jdbcTemplate.query(sql, studyGroupRowMapper());
    }

    @Override
    public List<StudyGroup> findSameCategoryAllByUID(int uid) {
        String sql = "select * "
                    +"from StudyGroup a "
                    +"join GroupCategory b on a.UID = b.groupUID "
                    +"where categoryUID in (select categoryUID from GroupCategory where groupUID = ?) and a.isPrivate = false "
                    +"group by b.groupUID "
                    +"order by rand()";
        return jdbcTemplate.query(sql, studyGroupRowMapper(), uid);
    }

    @Override
    public List<StudyGroup> findSameCategoryAllByUserUID(int userUID) {
        String sql = "select * "
                +"from StudyGroup a "
                +"join GroupCategory b on a.UID = b.groupUID "
                +"where categoryUID in (select categoryUID from UserCategory where userUID = ?) and a.isPrivate = false "
                +"group by b.groupUID "
                +"order by rand()";
        return jdbcTemplate.query(sql, studyGroupRowMapper(), userUID);
    }

    @Override
    public Optional<Integer> findSumMinStudyHourByUserUID(int userUID){
        String sql = "SELECT sum(hour(minStudyHour)) as sumMinStudyHour FROM StudyGroup a "
                + "INNER JOIN GroupMember b "
                + "ON a.UID = b.groupUID "
                + "WHERE b.userUID = ?";

        List<Integer> result = jdbcTemplate.query(sql, (rs, rowNum) -> Integer.valueOf(rs.getInt("sumMinStudyHour")), userUID);
        return result.stream().findAny();
    }

    @Override
    public Optional<Integer> findMinStudyHourByGroupUID(int UID){
        String sql = "SELECT hour(minStudyHour) as minStudyHour FROM StudyGroup WHERE UID= ?";
        List<Integer> result = jdbcTemplate.query(sql, (rs, rowNum) -> Integer.valueOf(rs.getInt("minStudyHour")), UID);
        return result.stream().findAny();
    }

    @Override
    public Optional<Map<String, Integer>> findMemberCntInfoByGroupUID(int UID){
        String sql = "SELECT a.maxMember as maxMember, count(*) as memberCnt " +
                "FROM StudyGroup a " +
                "INNER JOIN GroupMember b ON a.UID = b.groupUID " +
                "WHERE a.UID = ? " +
                "GROUP BY a.UID";

        Optional<Map<String, Integer>> memberCntInfo = jdbcTemplate.query(sql, (rs, rowNum) -> Map.of(
                "maxMember", rs.getInt("maxMember"),
                "memberCnt", rs.getInt("memberCnt")
        ), UID).stream().findAny();

        return memberCntInfo;
    }

    @Override
    public Optional<StudyGroup> findByUID(int uid) {
        List<StudyGroup> result = jdbcTemplate.query("select * from StudyGroup where UID = ?", studyGroupRowMapper(), uid);
        return result.stream().findAny();
    }

    @Override
    public Optional<StudyGroup> findByCode(String code) {
        List<StudyGroup> result = jdbcTemplate.query("select * from StudyGroup where code = ?", studyGroupRowMapper(), code);
        return result.stream().findAny();
    }

    @Override
    public void updateImgPath(int UID, String imgPath){
        String sql = "UPDATE StudyGroup SET imgPath = ?, updatedAt=now() WHERE UID = ?";
        jdbcTemplate.update(sql, imgPath, UID);
    }

    @Override
    public Optional<Integer> findMaxMember(int UID){
        String sql = "SELECT maxMember FROM StudyGroup WHERE UID = ? ";
        List<Integer> result = jdbcTemplate.query(sql, (rs, rowNum) -> Integer.valueOf(rs.getInt("maxMember")), UID);
        return result.stream().findAny();
    }

    @Override
    public List<StudyGroup> findMyStudyGroup(int userUID) {
        String sql = "SELECT * "
                + "FROM StudyGroup a "
                + "JOIN GroupMember b ON a.UID = b.groupUID "
                + "WHERE b.userUID = ?";
        return jdbcTemplate.query(sql, studyGroupRowMapper(), userUID);
    }

    @Override
    public void removeExpiredGroup() {
        String sql = "DELETE FROM StudyGroup "
                + "WHERE DATE_FORMAT(expiredAt,'%Y-%m-%d') < DATE_FORMAT(NOW(),'%Y-%m-%d')";
        jdbcTemplate.update(sql);
    }

    public void updateMinStudyHour(int UID, String minStudyHour){
        String sql = "UPDATE StudyGroup SET minStudyHour = TIME(?), updatedAt=now() WHERE UID = ?";
        jdbcTemplate.update(sql, minStudyHour, UID);
    }

    private RowMapper<StudyGroup> studyGroupRowMapper() {
        return (rs, rowNum) -> {
            StudyGroup studyGroup = new StudyGroup();
            studyGroup.setUID(rs.getInt("UID"));
            studyGroup.setName(rs.getString("name"));
            studyGroup.setCode(rs.getString("code"));
            studyGroup.setMaxTodayStudy(rs.getInt("maxTodayStudy"));
            studyGroup.setMaxMember(rs.getInt("maxMember"));
            studyGroup.setMaxPenalty(rs.getInt("maxPenalty"));
            studyGroup.setIsPrivate(rs.getBoolean("isPrivate"));
            studyGroup.setIsCam(rs.getBoolean("isCam"));
            studyGroup.setIsPenalty(rs.getBoolean("isPenalty"));
            studyGroup.setMinStudyHour(rs.getString("minStudyHour"));
            studyGroup.setImgPath(rs.getString("imgPath"));
            studyGroup.setExpiredAt(rs.getDate("expiredAt"));
            studyGroup.setCreatedAt(rs.getTimestamp("createdAt"));

            return studyGroup;
        };
    }

    private HashMap<String, Object> setParameter(StudyGroup studyGroup) {
        HashMap<String, Object> hashMap = new HashMap<String, Object>();
        hashMap.put("UID",studyGroup.getUID());
        hashMap.put("name",studyGroup.getName());
        hashMap.put("code",studyGroup.getCode());
        hashMap.put("maxTodayStudy",studyGroup.getMaxTodayStudy());
        hashMap.put("maxMember",studyGroup.getMaxMember());
        hashMap.put("maxPenalty",studyGroup.getMaxPenalty());
        hashMap.put("isPrivate",studyGroup.getIsPrivate());
        hashMap.put("isCam",studyGroup.getIsCam());
        hashMap.put("isPenalty",studyGroup.getIsPenalty());
        hashMap.put("minStudyHour", studyGroup.getMinStudyHour());
        hashMap.put("imgPath", studyGroup.getImgPath());
        hashMap.put("expiredAt", studyGroup.getExpiredAt());
        hashMap.put("createdAt",studyGroup.getCreatedAt());
        hashMap.put("updatedAt",studyGroup.getUpdatedAt());
        return hashMap;
    }
}
