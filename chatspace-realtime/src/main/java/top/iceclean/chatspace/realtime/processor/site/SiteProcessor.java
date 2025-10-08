package top.iceclean.chatspace.realtime.processor.site;

import top.iceclean.chatspace.infrastructure.dto.SiteDTO;
import top.iceclean.chatspace.infrastructure.po.Site;
import top.iceclean.chatspace.infrastructure.vo.SiteVO;

/**
 * 地理位置处理器
 * @author : Ice'Clean
 * @date : 2022-11-24
 */
public interface SiteProcessor {

    /**
     * 用户进入空间
     * 为用户初始化空间信息
     */
    void enterSpace();

    /**
     * 更新位置信息
     * @param siteDTO 新位置信息请求对象
     */
    void updateSite(SiteDTO siteDTO);

    /**
     * 更新用户视野
     */
    void updateVision();

    /**
     * 用户退出空间
     * 将当前用户从其所处的空间上抹除
     */
    void exitSpace();

    /**
     * 获取用户的虚拟坐标
     * @param spaceId 所处空间 ID
     * @param userId 用户 ID
     * @return 虚拟位置
     */
    Site getUserSite(int spaceId, int userId);
}
