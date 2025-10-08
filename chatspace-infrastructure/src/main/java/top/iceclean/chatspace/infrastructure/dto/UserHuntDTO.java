package top.iceclean.chatspace.infrastructure.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户夺宝消息
 * @author : Ice'Clean
 * @date : 2023-01-07
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserHuntDTO {
    /** 用户 ID */
    private Integer userId;
    /** 夺宝 ID */
    private Integer huntId;
}
