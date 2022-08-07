package study.gongsa.repository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.stereotype.Repository;
import study.gongsa.domain.User;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class JdbcTemplateUserRepository implements UserRepository {
    private final JdbcTemplate jdbcTemplate;
    private SimpleJdbcInsert insertIntoUser;

    @Autowired
    public JdbcTemplateUserRepository(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        insertIntoUser = new SimpleJdbcInsert(jdbcTemplate).withTableName("User").usingGeneratedKeyColumns("UID");
    }

    @Override
    public Number save(User user) {
        final Map<String, Object> parameters = setParameter(user);
        return insertIntoUser.executeAndReturnKey(parameters);
    }

    @Override
    public Optional<User> findByUID(int uid){
        List<User> result = jdbcTemplate.query("select * from User where UID = ?", userRowMapper(), uid);
        return result.stream().findAny();
    };

    @Override
    public Optional<User> findByEmail(String email){
        List<User> result = jdbcTemplate.query("select * from User where email = ?", userRowMapper(), email);
        return result.stream().findAny();
    }
    @Override
    public Optional<User> findByNickname(String nickname){
        List<User> result = jdbcTemplate.query("select * from User where nickname = ?", userRowMapper(), nickname);
        return result.stream().findAny();
    }

    private RowMapper<User> userRowMapper() {
        return (rs, rowNum) -> {
            User user = new User();
            user.setUID(rs.getInt("UID"));
            user.setEmail(rs.getString("email"));
            user.setPasswd(rs.getString("passwd"));
            user.setNickname(rs.getString("nickname"));
            user.setLevel(rs.getInt("level"));
            user.setImgPath(rs.getString("imgPath"));
            user.setAuthCode(rs.getString("authCode"));

            return user;
        };
    }
    private HashMap<String, Object> setParameter(User user) {
        HashMap<String, Object> hashMap = new HashMap<String, Object>();
        hashMap.put("UID",user.getUID());
        hashMap.put("email",user.getEmail());
        hashMap.put("passwd",user.getPasswd());
        hashMap.put("nickname",user.getNickname());
        hashMap.put("level",user.getLevel());
        hashMap.put("imgPath",user.getImgPath());
        hashMap.put("authCode",user.getAuthCode());
        hashMap.put("isAuth",user.getIsAuth());
        hashMap.put("createdAt",user.getCreatedAt());
        hashMap.put("updatedAt",user.getUpdatedAt());
        return hashMap;
    }

}
