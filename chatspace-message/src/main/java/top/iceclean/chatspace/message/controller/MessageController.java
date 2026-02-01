package top.iceclean.chatspace.message.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.iceclean.chatspace.infrastructure.constant.SessionType;
import top.iceclean.chatspace.message.service.MessageService;

/**
 * 消息服务
 * 这里的消息服务，url 使用的是 session 前缀，因为获取的资源是 session 资源
 * @author : Ice'Clean
 * @date : 2022-10-10
 */
@RestController
@RequestMapping("/session")
public class MessageController {

    private final MessageService messageService;

    public MessageController(MessageService messageService) {
        this.messageService = messageService;
    }

    /**
     * 获取用户在某个会话的历史消息
     * @param friendId 好友 ID
     * @param page 聊天记录的页数
     * @return 历史消息列表
     */
    @GetMapping("/friend/history")
    public Object getFriendHistory(int friendId, int page) {
        return messageService.getChatHistory(SessionType.FRIEND, friendId, page);
    }
}
