package study.gongsa.repository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.stereotype.Repository;
import study.gongsa.domain.User;
import study.gongsa.domain.UserAuth;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class JdbcTemplateUserAuthRepository implements UserAuthRepository{
    private final JdbcTemplate jdbcTemplate;
    private SimpleJdbcInsert insertIntoUserAuth;

    @Autowired
    public JdbcTemplateUserAuthRepository(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        insertIntoUserAuth = new SimpleJdbcInsert(jdbcTemplate).withTableName("UserAuth").usingGeneratedKeyColumns("UID");
    }

    @Override
    public Number save(UserAuth userAuth) {
        final Map<String, Object> parameters = setParameter(userAuth);
        return insertIntoUserAuth.executeAndReturnKey(parameters);
    }

    @Override
    public Optional<UserAuth> findByUID(int UID){
        List<UserAuth> result = jdbcTemplate.query("SELECT * FROM UserAuth WHERE UID = ?", userAuthRowMapper(), UID);
        return result.stream().findAny();
    };

    private RowMapper<UserAuth> userAuthRowMapper() {
        return (rs, rowNum) -> {
            UserAuth userAuth = new UserAuth();
            userAuth.setUID(rs.getInt("UID"));
            userAuth.setUserUID(rs.getInt("userUID"));
            userAuth.setRefreshToken(rs.getString("refreshToken"));
            userAuth.setCreatedAt(rs.getTimestamp("createdAt"));
            userAuth.setUpdatedAt(rs.getTimestamp("updatedAt"));
            return userAuth;
        };
    }

    private HashMap<String, Object> setParameter(UserAuth userAuth) {
        HashMap<String, Object> hashMap = new HashMap<String, Object>();
        hashMap.put("UID", userAuth.getUID());
        hashMap.put("userUID",userAuth.getUserUID());
        hashMap.put("refreshToken",userAuth.getRefreshToken());
        hashMap.put("createdAt",userAuth.getCreatedAt());
        hashMap.put("updatedAt",userAuth.getUpdatedAt());
        return hashMap;
    }
}
