package study.gongsa.repository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.stereotype.Repository;
import study.gongsa.domain.Category;
import study.gongsa.domain.User;
import study.gongsa.domain.UserAuth;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class JdbcTemplateCategoryRepository implements CategoryRepository{
    private final JdbcTemplate jdbcTemplate;
    private SimpleJdbcInsert insertIntoUserAuth;

    @Autowired
    public JdbcTemplateCategoryRepository(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @Override
    public List<Category> findAll(){
        List<Category> result = jdbcTemplate.query("SELECT * FROM Category", categoryRowMapper());
        return result;
    };

    @Override
    public Optional<Category> findByUID(int uid){
        List<Category> result = jdbcTemplate.query("SELECT * FROM Category WHERE UID = ?", categoryRowMapper(), uid);
        return result.stream().findAny();
    };

    @Override
    public List<Category> findByGroupUID(int groupUID) {
        String sql = "SELECT c.UID, c.name "
                + "FROM GroupCategory a "
                + "JOIN StudyGroup b ON a.groupUID = b.UID "
                + "JOIN Category c ON a.categoryUID = c.UID "
                + "WHERE groupUID = ?";
        return jdbcTemplate.query(sql, categoryRowMapper(), groupUID);
    }

    private RowMapper<Category> categoryRowMapper() {
        return (rs, rowNum) -> {
            Category category = new Category();
            category.setUID(rs.getInt("UID"));
            category.setName(rs.getString("name"));
            return category;
        };
    }

    private HashMap<String, Object> setParameter(Category category) {
        HashMap<String, Object> hashMap = new HashMap<String, Object>();
        hashMap.put("UID", category.getUID());
        hashMap.put("name",category.getName());
        return hashMap;
    }
}
