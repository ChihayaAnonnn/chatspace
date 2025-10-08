package top.iceclean.chatspace.user.controller;

import org.springframework.web.bind.annotation.*;
import top.iceclean.chatspace.infrastructure.po.User;
import top.iceclean.chatspace.user.service.UserService;

import java.util.List;

/**
 * 内部调用的用户服务
 * @author : Ice'Clean
 * @date : 2022-10-07
 */
@RestController
public class UserInnerController {

    private final UserService userService;

    public UserInnerController(UserService userService) {
        this.userService = userService;
    }

    /**
     * 根据用户 ID 列表获取所有用户
     * @param userIdList 用户 ID 列表
     * @return 用户集合
     */
    @PostMapping("/list")
    public List<User> getUserList(@RequestBody List<Integer> userIdList) {
        return userService.getUserList(userIdList);
    }

    /**
     * 通过用户 ID 获取用户对象
     * @param userId 用户 ID
     * @return 用户对象
     */
    @GetMapping("/id")
    public User getUserById(@RequestParam int userId) {
        return userService.getUserById(userId);
    }
}
