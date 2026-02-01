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
     * 由位置变化引起的位置信息更新
     * @param siteDTO 新位置信息请求对象
     */
    void updateSite(SiteDTO siteDTO);

    /**
     * 由区域变化引起的位置信息更新
     * @param zoneIds 旧->新区域 ID
     */
    void updateSite(int[] zoneIds);

    /**
     * 用户退出空间
     * 将当前用户从其所处的空间上抹除
     */
    void exitSpace(SiteVO siteVO);
}
