package me.ahoo.cosid.example.jdbc.controller;

import me.ahoo.cosid.example.jdbc.entity.User;
import me.ahoo.cosid.example.jdbc.repository.UserRepository;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * User Controller.
 *
 * @author : Rocher Kong
 */
@RestController
@RequestMapping("user")
public class UserController {
    private final UserRepository userRepository;

    public UserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping("save")
    public void save() {
        User user = new User();
        user.setAge(33);
        user.setName("Rocher");
        user.setCreateTime(LocalDateTime.now());
        userRepository.insert(user);
    }
}
