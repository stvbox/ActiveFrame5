package ru.intertrust.cm.core.dao.impl;

import java.util.HashMap;
import java.util.Map;

import javax.sql.DataSource;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import ru.intertrust.cm.core.business.api.dto.AuthenticationInfo;
import ru.intertrust.cm.core.dao.api.AuthenticationDAO;

/**
 * Реализация DAO для работы с системным объектом AuthenticationInfo.
 * @author atsvetkov
 *
 */
public class AuthenticationDAOImpl implements AuthenticationDAO {

    private NamedParameterJdbcTemplate jdbcTemplate;

    public void setDataSource(DataSource dataSource) {
        this.jdbcTemplate = new NamedParameterJdbcTemplate(dataSource);
    }

    @Override
    public int insertAuthenticationInfo(AuthenticationInfo authenticationInfo) {
        String query = "insert into authentication_info (id, user_uid, password) values (:id, :user_uid, :password)";
        Map<String, Object> paramMap = new HashMap<String, Object>();
        paramMap.put("id", authenticationInfo.getId());        
        paramMap.put("user_uid", authenticationInfo.getUserUid());
        paramMap.put("password", authenticationInfo.getPassword());
        
        return jdbcTemplate.update(query, paramMap);
    }

    @Override
    public boolean existsAuthenticationInfo(String userUid) {
        String query = "select count(*) from authentication_info ai where ai.user_uid=:user_uid";
        Map<String, Object> paramMap = new HashMap<String, Object>();
        paramMap.put("user_uid", userUid);
        int total = jdbcTemplate.queryForInt(query, paramMap);
        return total > 0;
    }
    
}
