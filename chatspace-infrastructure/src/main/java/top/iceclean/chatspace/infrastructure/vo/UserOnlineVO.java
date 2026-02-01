package top.iceclean.chatspace.infrastructure.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户上线状态改变响应对象
 * @author : Ice'Clean
 * @date : 2022-06-08
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserOnlineVO {
    /** 用户 ID */
    private Integer userId;
    /** 上线状态 */
    private Boolean online;
}
