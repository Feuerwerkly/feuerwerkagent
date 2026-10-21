package com.example.feuerwerkagent;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableConfigurationProperties
@EnableAsync
@MapperScan("com.example.feuerwerkagent.mapper")
public class FeuerwerkagentApplication {

    public static void main(String[] args) {
        SpringApplication.run(FeuerwerkagentApplication.class, args);
    }

}
