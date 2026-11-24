package com.gatewayapp.demo.config;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;


@Configuration
public class DataSourceConfig {

    @Bean
    @Primary
    DataSource dataSource(@Value("${spring.datasource.url}") String url,
                          @Value("${spring.datasource.username}") String user,
                          @Value("${spring.datasource.password}") String pass) {
        return DataSourceBuilder.create().url(url).username(user).password(pass).build();
    }

    @Bean
    DataSource readonlyDataSource(@Value("${app.readonly-db.url}") String url,
                                  @Value("${app.readonly-db.username}") String user,
                                  @Value("${app.readonly-db.password}") String pass) {
        return DataSourceBuilder.create().url(url).username(user).password(pass).build();
    }

    // Define BOTH JdbcTemplates: if only the read-only one existed, Boot would not create the
    // main one, and your audit aspect would end up writing with the read-only login and fail.
    @Bean
    @Primary
    JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean
    JdbcTemplate readonlyJdbc(@Qualifier("readonlyDataSource") DataSource ds) {
        return new JdbcTemplate(ds);
    }

    @Bean
    PlatformTransactionManager readonlyTx(@Qualifier("readonlyDataSource") DataSource ds) {
        return new DataSourceTransactionManager(ds);
    }
}