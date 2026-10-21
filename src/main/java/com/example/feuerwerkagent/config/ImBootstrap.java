package com.example.feuerwerkagent.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 即时通讯（IM）数据表初始化：IF NOT EXISTS，幂等
 * im_friendship 好友 / im_friend_request 好友申请 / im_group 群 /
 * im_group_member 群成员 / im_message 消息 / im_conversation 会话读位
 */
@Slf4j
@Order(10)
@Component
public class ImBootstrap implements ApplicationRunner {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS im_friendship (" +
                "id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                "user_id BIGINT NOT NULL," +
                "friend_id BIGINT NOT NULL," +
                "remark VARCHAR(50)," +
                "create_time DATETIME DEFAULT CURRENT_TIMESTAMP," +
                "UNIQUE KEY uk_user_friend (user_id, friend_id)," +
                "KEY idx_friend (friend_id)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS im_friend_request (" +
                "id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                "from_user_id BIGINT NOT NULL," +
                "to_user_id BIGINT NOT NULL," +
                "message VARCHAR(200)," +
                "status TINYINT NOT NULL DEFAULT 0," +
                "create_time DATETIME DEFAULT CURRENT_TIMESTAMP," +
                "handle_time DATETIME," +
                "KEY idx_to (to_user_id, status)," +
                "KEY idx_from (from_user_id, status)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS im_group (" +
                "id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                "name VARCHAR(50) NOT NULL," +
                "owner_id BIGINT NOT NULL," +
                "notice VARCHAR(500)," +
                "create_time DATETIME DEFAULT CURRENT_TIMESTAMP," +
                "update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP) " +
                "ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS im_group_member (" +
                "id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                "group_id BIGINT NOT NULL," +
                "user_id BIGINT NOT NULL," +
                "role VARCHAR(20) NOT NULL DEFAULT 'MEMBER'," +
                "join_time DATETIME DEFAULT CURRENT_TIMESTAMP," +
                "UNIQUE KEY uk_group_user (group_id, user_id)," +
                "KEY idx_user (user_id)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS im_message (" +
                "id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                "peer_type VARCHAR(10) NOT NULL," +
                "from_user_id BIGINT NOT NULL," +
                "to_user_id BIGINT," +
                "group_id BIGINT," +
                "content_type VARCHAR(10) NOT NULL DEFAULT 'TEXT'," +
                "content TEXT," +
                "create_time DATETIME DEFAULT CURRENT_TIMESTAMP," +
                "KEY idx_single (peer_type, from_user_id, to_user_id)," +
                "KEY idx_group (peer_type, group_id)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS im_conversation (" +
                "id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                "user_id BIGINT NOT NULL," +
                "peer_type VARCHAR(10) NOT NULL," +
                "peer_id BIGINT NOT NULL," +
                "last_read_message_id BIGINT NOT NULL DEFAULT 0," +
                "update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                "UNIQUE KEY uk_user_peer (user_id, peer_type, peer_id)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

        log.info("即时通讯(IM)数据表已就绪");
    }
}
