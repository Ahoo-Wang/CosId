package me.ahoo.cosid.example.jdbc.repository;

import me.ahoo.cosid.example.jdbc.entity.User;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** UserRepository.
 *
 * @author : Rocher Kong
 */
@Mapper
public interface UserRepository {
    void insert(User order);

    User getById(@Param("userId") long userId);

    List<User> query();

    List<User> getByIds(@Param("ids") List<Long> ids);
}
