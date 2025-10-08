package top.iceclean.chatspace.user.mapper;


import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import top.iceclean.chatspace.infrastructure.po.User;

/**
 * @author : Ice'Clean
 * @date : 2022-05-25
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
}
