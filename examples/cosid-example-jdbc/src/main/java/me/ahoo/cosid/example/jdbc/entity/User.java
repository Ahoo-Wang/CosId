package me.ahoo.cosid.example.jdbc.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户表.
 *
 * @author : Rocher Kong
 */
@Data
public class User {
    private Long id;
    private Integer age;
    private String name;
    private LocalDateTime createTime;
}
