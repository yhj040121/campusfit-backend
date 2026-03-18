package com.campusfit.modules.message.service;

import com.campusfit.modules.message.vo.MessageItemVO;

import java.util.List;

public interface MessageService {

    List<MessageItemVO> listMessages();

    int countUnread();

    boolean markRead(String messageId);

    int markAllRead();
}
