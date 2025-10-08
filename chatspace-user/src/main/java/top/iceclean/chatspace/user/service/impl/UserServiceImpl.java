package top.iceclean.chatspace.user.service.impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import top.iceclean.chatspace.cache.UserCache;
import top.iceclean.chatspace.infrastructure.constant.FileConst;
import top.iceclean.chatspace.infrastructure.constant.ResponseStatusEnum;
import top.iceclean.chatspace.infrastructure.dto.UserDTO;
import top.iceclean.chatspace.infrastructure.po.User;
import top.iceclean.chatspace.infrastructure.pojo.GlobalException;
import top.iceclean.chatspace.infrastructure.pojo.Response;
import top.iceclean.chatspace.user.pojo.UserAuthority;
import top.iceclean.chatspace.infrastructure.utils.*;
import top.iceclean.chatspace.infrastructure.vo.UserVO;
import top.iceclean.chatspace.user.mapper.UserMapper;
import top.iceclean.chatspace.user.service.UserService;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * @author : Ice'Clean
 * @date : 2022-05-25
 */
@Slf4j
@Service
public class UserServiceImpl implements UserService, UserDetailsService {
    /** 用户映射 */
    private final UserMapper userMapper;
    /** 用户缓存 */
    private final UserCache userCache;
    /** 权限管理 */
    private final AuthenticationManager authenticationManager;

    @Autowired
    public UserServiceImpl(UserMapper userMapper, UserCache userCache, AuthenticationManager authenticationManager) {
        this.userMapper = userMapper;
        this.userCache = userCache;
        this.authenticationManager = authenticationManager;
    }

    @Override
    public Response login(String userName, String userPass) {
        // AuthenticationManager authenticate 进行用户认证
        UsernamePasswordAuthenticationToken authenticationToken =
                new UsernamePasswordAuthenticationToken(userName, userPass);
        Authentication authenticate = authenticationManager.authenticate(authenticationToken);

        // 如果认证没通过，返回失败
        if (Objects.isNull(authenticate)) {
            return new Response().setStatus(ResponseStatusEnum.LOGIN_FAILED)
                    .setMsg("用户名或密码错误！")
                    .addData("success", false);
        }

        // 获取认证信息
        UserAuthority userAuthority = (UserAuthority) authenticate.getPrincipal();
        User user = userAuthority.getUser();
        String userId = user.getId().toString();
        // 使用 userId 生成一个 token
        String token = JwtUtils.createJWT(userId);

        // 将 token、用户的信息和权限写入登录缓存
        userCache.cachedLogin(user, token, JSON.toJSONString(userAuthority));

        // 返回 token 和用户信息
        return new Response().setStatus(ResponseStatusEnum.OK)
                .setMsg("登录成功")
                .addData("success", true)
                .addData("token", token)
                .addData("user", userCache.wrapUser(user));
    }

    @Override
    @Transactional
    public Response register(UserDTO userDTO) {
        // 先查询缓存中的验证码，为空或不匹配则无效
        String captcha = userCache.getCaptcha(userDTO.getUserName());
        String expect = userDTO.getEmail() + userDTO.getCode();
        if (!Objects.equals(captcha, expect)) {
            log.info("用户验证码校验失败：{} != {}", captcha, expect);
            return new Response(ResponseStatusEnum.CODE_INVALID);
        }

        // 验证码有效则执行注册
        userDTO.setUserPass(Md5Utils.encode(userDTO.getUserPass()));
        userMapper.insert(new User(userDTO));
        // 成功则查询用户的详细信息并返回
        User user = getUserByUserName(userDTO.getUserName());
        return new Response(ResponseStatusEnum.OK)
                .setMsg("注册成功")
                .setData(userCache.wrapUser(user));
    }

    @Override
    public Response uploadAvatar(MultipartFile avatar) {
        // 将用户查询出来
        User user = getCurrentUser();

        // 上传头像（以用户ID-当前时间-头像大小命名）
        String extentName = avatar.getOriginalFilename();
        extentName = Objects.requireNonNull(extentName).substring(extentName.lastIndexOf("."));
        String avatarName = String.format("%d_%s_%d%s", user.getId(), DateUtils.getTimeCompact(),
                avatar.getSize(), extentName);
        FileConst.Code code = FileUtils.upload(avatar, FileConst.AVATAR_PATH, avatarName);

        // 上传成功时修改头像
        if (code == FileConst.Code.UPLOAD_SUCCESS) {
            user.setAvatar("../" + FileConst.AVATAR_PATH + avatarName);
            userMapper.updateById(user);
            return new Response(ResponseStatusEnum.OK)
                    .setMsg(code.getMsg())
                    .addData("avatarName", avatarName);
        }

        // 上传失败时返回错误信息
        return new Response(ResponseStatusEnum.INTERNAL_SERVER_ERROR).setMsg(code.getMsg());
    }

    @Override
    public Response updateInfo(UserDTO userDTO) {
        // 拿到用户信息
        User user = getCurrentUser();

        // 修改性别
        if (userDTO.getSex() != null) {
            user.setSex(userDTO.getSex());
        }

        // 修改昵称
        if (userDTO.getNickName() != null) {
            user.setNickName(userDTO.getNickName());
        }

        // 执行修改并返回结果
        if (userMapper.updateById(user) == 1) {
            // TODO 刷新缓存中的用户
            return new Response(ResponseStatusEnum.OK).setMsg("更新信息成功");
        }
        return new Response(ResponseStatusEnum.DATABASE_ERROR);
    }

    @Override
    public Response updatePassword(String oldPassword, String newPassword) {
        // 拿到用户
        User user = getCurrentUser();

        // 匹配旧密码
        if (!user.getUserPass().equals(Md5Utils.encode(oldPassword))) {
            return new Response(ResponseStatusEnum.PASSWORD_INVALID);
        }

        // 匹配成功，则给予修改
        user.setUserPass(Md5Utils.encode(newPassword));
        if (userMapper.updateById(user) == 1) {
            return new Response(ResponseStatusEnum.OK).setMsg("修改密码成功");
        }
        return new Response(ResponseStatusEnum.DATABASE_ERROR);
    }

    Pattern pattern = Pattern.compile("^\\d+$");
    @Override
    public Response searchUser(String key) {
        // 判断是否为数值以启用 ID 的精确搜索
        boolean searchId = pattern.matcher(key).matches();
        // 判断是否为邮箱以启用邮箱的精确搜索
        boolean searchEmail = key.contains("@");
        // 进行模糊搜素，并转化为响应对象
        List<UserVO> collect = userMapper.selectList(new LambdaQueryWrapper<User>()
                .isNull(User::getDeleteTime)
                .eq(searchId, User::getId, key)
                .eq(searchEmail, User::getEmail, key)
                .or(searchId||searchEmail).like(User::getUserName, key)
                .or().like(User::getNickName, key))
                .stream().map(userCache::wrapUser).collect(Collectors.toList());
        // 最后返回搜索出来的用户列表
        return new Response(ResponseStatusEnum.OK)
                .addData("total", collect.size())
                .addData("result", collect);
    }

    @Override
    public User getCurrentUser() {
        // 拿到用户上下文并返回
        return ((UserAuthority) SecurityContextHolder.getContext().getAuthentication().getPrincipal()).getUser();
    }

    @Override
    public User getUserById(int userId) {
        return userMapper.selectById(userId);
    }

    @Override
    public User getUserByUserName(String userName) {
        return userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUserName, userName));
    }

    @Override
    public List<User> getUserList(List<Integer> userIdList) {
        return userMapper.selectList(new LambdaQueryWrapper<User>()
                .isNull(User::getDeleteTime).in(User::getId, userIdList));
    }

    @Override
    public UserDetails loadUserByUsername(String userName) throws UsernameNotFoundException {
        // 查询用户信息
        User user = getUserByUserName(userName);

        //如果没有查询到用户就抛出异常，认证失败
        if(Objects.isNull(user)){
            throw new GlobalException(ResponseStatusEnum.AUTHENTICATION_ERROR, "用户名或密码错误");
        }

        // 封装用户信息
        List<String> permissions = new ArrayList<>();
        permissions.add("User");
        return new UserAuthority(user, permissions);
    }
}
