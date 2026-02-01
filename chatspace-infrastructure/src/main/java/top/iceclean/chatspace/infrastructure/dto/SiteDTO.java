package top.iceclean.chatspace.infrastructure.dto;

import com.alibaba.fastjson.JSON;
import lombok.Data;

/**
 * 用户位置对象
 * @author : Ice'Clean
 * @date : 2022-09-30
 */
@Data
public class SiteDTO {
    private Integer userId;
    private Integer spaceId;
    private Integer x;
    private Integer y;
    private Integer vision;
    private Integer exist;

    public static SiteDTO parse(String data) {
        return JSON.parseObject(data, SiteDTO.class);
    }
}
