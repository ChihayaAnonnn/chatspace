package top.iceclean.chatspace.realtime.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import top.iceclean.chatspace.infrastructure.po.Site;

/**
 * 位置数据的操作
 * @author : Ice'Clean
 * @date : 2022-10-28
 */
@Mapper
public interface SiteMapper extends BaseMapper<Site> {
}
