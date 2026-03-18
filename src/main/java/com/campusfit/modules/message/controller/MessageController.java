package com.campusfit.modules.message.controller;

import com.campusfit.common.api.ApiResponse;
import com.campusfit.modules.message.service.MessageService;
import com.campusfit.modules.message.vo.MessageItemVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/messages")
public class MessageController {

    private final MessageService messageService;

    public MessageController(MessageService messageService) {
        this.messageService = messageService;
    }

    @GetMapping
    public ApiResponse<List<MessageItemVO>> list() {
        return ApiResponse.success(messageService.listMessages());
    }

    @GetMapping("/unread-count")
    public ApiResponse<Integer> unreadCount() {
        return ApiResponse.success(messageService.countUnread());
    }

    @PostMapping("/{messageId}/read")
    public ApiResponse<Boolean> markRead(@PathVariable String messageId) {
        return ApiResponse.success("\u5df2\u6807\u8bb0\u4e3a\u5df2\u8bfb", messageService.markRead(messageId));
    }

    @PostMapping("/read-all")
    public ApiResponse<Integer> markAllRead() {
        return ApiResponse.success("\u5df2\u5b8c\u6210\u5168\u90e8\u5df2\u8bfb", messageService.markAllRead());
    }
}
