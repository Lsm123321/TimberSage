package com.arch3d.service;

import com.arch3d.dto.ChatFeedbackRequest;
import com.arch3d.dto.ChatStreamMeta;
import com.arch3d.entity.AiChatHistory;
import org.springframework.data.domain.Page;

import java.io.IOException;
import java.util.function.Consumer;

public interface ChatService {
    void streamChat(String question, Integer modelId, String sessionId, Integer userId, 
                   Consumer<String> onChunk, Consumer<ChatStreamMeta> onComplete, Consumer<Exception> onError) throws IOException;
    void saveChatHistory(String sessionId, Integer userId, Integer modelId, String question, String answer);
    void saveChatFeedback(Integer chatId, ChatFeedbackRequest feedback, Integer userId);
    Page<AiChatHistory> getUserChatHistory(Integer userId, int page, int pageSize);
    AiChatHistory updateChatNote(Integer chatId, Integer userId, String chatNote);
    AiChatHistory generateChatNote(Integer chatId, Integer userId);
    void deleteChatHistory(Integer chatId, Integer userId);
    boolean checkRateLimit(Integer userId);
}
