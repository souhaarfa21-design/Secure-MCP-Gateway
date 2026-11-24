package com.gatewayapp.demo.tools;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class ReadOnlyQueryService {

    private final JdbcTemplate readonlyJdbc;

    public ReadOnlyQueryService(@Qualifier("readonlyJdbc") JdbcTemplate readonlyJdbc) {
        this.readonlyJdbc = readonlyJdbc;
    }

    @Transactional(transactionManager = "readonlyTx", readOnly = true)
    public List<Map<String, Object>> query(String username, String sql) {
        readonlyJdbc.query("SELECT set_config('app.user', ?, true)", rs -> { }, username);
        return readonlyJdbc.queryForList(sql);
    }
}