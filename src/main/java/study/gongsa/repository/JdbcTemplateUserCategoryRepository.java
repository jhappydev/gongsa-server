package study.gongsa.repository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.stereotype.Repository;
import study.gongsa.domain.UserCategory;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
public class JdbcTemplateUserCategoryRepository implements UserCategoryRepository {
    private final JdbcTemplate jdbcTemplate;

    private SimpleJdbcInsert insertIntoUserCategory;

    @Autowired
    public JdbcTemplateUserCategoryRepository(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        insertIntoUserCategory = new SimpleJdbcInsert(jdbcTemplate).withTableName("UserCategory").usingGeneratedKeyColumns("UID");
    }

    @Override
    public void save(UserCategory userCategory) {
        final Map<String, Object> parameters = setParameter(userCategory);
        insertIntoUserCategory.execute(parameters);
    }

    @Override
    public List<UserCategory> findByUserUID(int userUID) {
        return jdbcTemplate.query("SELECT * FROM UserCategory WHERE userUID = ? ORDER BY categoryUID", userCategoryRowMapper(), userUID);
    }

    @Override
    public void remove(int uid) {
        String sql = "DELETE FROM UserCategory WHERE UID = ?";
        jdbcTemplate.update(sql, uid);
    }

    private HashMap<String, Object> setParameter(UserCategory userCategory) {
        HashMap<String, Object> hashMap = new HashMap<String, Object>();
        hashMap.put("UID", userCategory.getUID());
        hashMap.put("userUID",userCategory.getUserUID());
        hashMap.put("categoryUID",userCategory.getCategoryUID());
        hashMap.put("createdAt",userCategory.getCreatedAt());
        hashMap.put("updatedAt",userCategory.getUpdatedAt());
        return hashMap;
    }

    private RowMapper<UserCategory> userCategoryRowMapper() {
        return (rs, rowNum) -> {
            UserCategory userCategory = new UserCategory();
            userCategory.setUID(rs.getInt("UID"));
            userCategory.setUserUID(rs.getInt("userUID"));
            userCategory.setCategoryUID(rs.getInt("categoryUID"));
            userCategory.setCreatedAt(rs.getTimestamp("createdAt"));
            userCategory.setUpdatedAt(rs.getTimestamp("updatedAt"));

            return userCategory;
        };
    }
}
