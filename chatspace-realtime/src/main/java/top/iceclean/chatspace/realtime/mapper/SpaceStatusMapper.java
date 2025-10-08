package top.iceclean.chatspace.realtime.mapper;

import org.apache.ibatis.annotations.*;
import top.iceclean.chatspace.realtime.share.SpacePoint;

import java.awt.*;
import java.util.Set;

/**
 * 对空间状态数据的操作
 * @author : Ice'Clean
 * @date : 2022-10-27
 */
@Mapper
public interface SpaceStatusMapper {

    /**
     * 批量将用户状态添加到指定表指定位置中去
     * @param table 目标的表
     * @param values 批量插入的多个值
     */
    @Insert("insert into ${table} (x, y, user_id) values ${values}")
    void addStatus(@Param("table") String table,
                   @Param("values") String values);

    /**
     * 批量将用户状态从指定表指定位置中移除
     * @param table 目标的表
     * @param values 批量删除的多个值
     */
    @Delete("delete from ${table} where (x, y, user_id) in (${values})")
    void removeStatus(@Param("table") String table,
                      @Param("values") String values);

    /**
     * 销毁用户在指定表中的状态
     * @param table 指定表
     * @param userId 指定用户
     */
    @Delete("delete from ${table} where user_id = #{userId}")
    void destroyStatus(@Param("table") String table,
                       @Param("userId") int userId);

    /**
     * 获取指定表中指定位置的所有用户
     * @param table 目标的表
     * @param points 批量查询的多个点
     * @return 该位置下所有的用户状态
     */
    @Select("select user_id from ${table} where (x, y) in (${points})")
    Set<Integer> getStatus(@Param("table") String table,
                   @Param("points") String points);

    /**
     * 获取用户当前的位置
     * @param spaceId 空间 ID
     * @param userId 用户 ID
     * @return 用户所在的位置
     */
    @Select("select x, y from t_site${spaceId} where user_id = #{userId}")
    SpacePoint getSite(@Param("spaceId") int spaceId,
                       @Param("userId") int userId);

    /**
     * 判断空间中某个位置是否有用户的存在感
     * @param spaceId 指定空间
     * @param value 用户坐标以及 ID
     * @return 存在则返回用户 ID，否则为 null
     */
    @Select("select user_id from t_exist${spaceId} where (x, y, user_id) = ${value}")
    Integer isExist(@Param("spaceId") int spaceId,
                    @Param("value") String value);

    /**
     * 批量将用户状态添加到指定表指定位置中去
     * @param spaceId 目标空间
     * @param values 批量插入的多个值
     */
    @Insert("insert into t_zone${spaceId} (x, y, zone_id) values ${values}")
    void addZone(@Param("spaceId") int spaceId,
                   @Param("values") String values);

    /**
     * 获取空间中某点可能存在的区域
     * @param spaceId 空间 ID
     * @param point 指定点
     * @return 如果存在区域，则返回其 ID，否则为 null
     */
    @Select("select zone_id from t_zone${spaceId} where (x, y) = ${point}")
    Integer getZone(@Param("spaceId") int spaceId,
                    @Param("point") String point);
}
