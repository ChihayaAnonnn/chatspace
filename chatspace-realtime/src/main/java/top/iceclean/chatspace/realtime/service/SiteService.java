package top.iceclean.chatspace.realtime.service;

import top.iceclean.chatspace.infrastructure.po.Site;
import top.iceclean.chatspace.infrastructure.vo.SiteVO;

/**
 * @author : Ice'Clean
 * @date : 2022-10-28
 */
public interface SiteService {

    /**
     * 获取用户的位置信息
     * @param userId 用户 ID
     * @return 位置信息
     */
    Site getUserSite(int userId);

    /**
     * 通过用户 ID 获取位置响应
     * @param userId 用户 ID
     * @return 位置响应
     */
    SiteVO getSiteVO(int userId);

    /**
     * 更新用户的位置信息
     * @param siteVO 位置信息
     */
    void updateUserSite(SiteVO siteVO);

    /**
     * 更新用户的位置信息
     * @param site 位置信息
     */
    void updateUserSite(Site site);
}
