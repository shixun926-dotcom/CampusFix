package com.campusfix;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * CampusFix 启动类。
 *
 * @author CampusFix
 */
@SpringBootApplication
@MapperScan("com.campusfix.**.mapper")
@EnableScheduling
@EnableAsync
public class CampusFixApplication {

    public static void main(String[] args) {
        SpringApplication.run(CampusFixApplication.class, args);
    }
}
