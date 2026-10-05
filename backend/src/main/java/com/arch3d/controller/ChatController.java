package com.arch3d.controller;

import com.arch3d.dto.ApiResponse;
import com.arch3d.dto.ChatFeedbackRequest;
import com.arch3d.dto.PageDTO;
import com.arch3d.entity.AiChatHistory;
import com.arch3d.security.UserPrincipal;
import com.arch3d.service.ChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "AI聊天", description = "AI智能问答接口")
public class ChatController {

    private final ChatService chatService;

    /**
     * AI 聊天的 SSE 流式接口。前端用 fetch 读 response.body 边收边渲染，
     * 后端按事件名推送：message（回答增量）、meta（引用/置信度等）、complete、error。
     * 逐 token 输出必须走流式，不然用户要干等整个回答生成完。
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "AI聊天流式接口", description = "流式获取AI回答")
    public SseEmitter streamChat(
            @RequestParam("question") String question,
            @RequestParam(value = "modelId", required = false) Integer modelId,
            @RequestParam(value = "sessionId", required = false) String sessionId,
            @RequestParam(value = "record", defaultValue = "false") boolean record,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {

        // 5 分钟没动静就断开，别让连接一直挂着
        SseEmitter emitter = new SseEmitter(300000L);
        Integer userId = userPrincipal != null ? userPrincipal.getId() : null;
        String finalSessionId = sessionId != null ? sessionId : UUID.randomUUID().toString();

        // 调大模型是阻塞的，得放新线程里跑。先推个 ping 让连接尽快建立起来，用户能早点看到响应
        try {
            emitter.send(SseEmitter.event()
                .name("ping")
                .data("ok")
                .id(UUID.randomUUID().toString()));
        } catch (IOException ignored) {
            // 客户端可能已经断了，预热失败不影响后面推流
        }

        // streamChat 会一直阻塞读到流结束，所以单独开个线程推流，别把请求线程占死
        new Thread(() -> {
            StringBuilder answerBuffer = new StringBuilder();
            try {
                chatService.streamChat(question, modelId, finalSessionId, userId,
                    chunk -> {
                        answerBuffer.append(chunk);
                        try {
                            emitter.send(SseEmitter.event()
                                .name("message")
                                .data(chunk)
                                .id(UUID.randomUUID().toString()));
                        } catch (IOException e) {
                            log.error("Error sending chunk", e);
                            emitter.complete();
                        }
                    },
                    meta -> {
                        try {
                            // 只有模型页的智能聊天（record=true）才写进个人中心的问答历史
                            if (record && userId != null && modelId != null) {
                                chatService.saveChatHistory(finalSessionId, userId, modelId, question, answerBuffer.toString());
                            }
                            // meta 带一些结构化信息（引用链接、置信度）
                            emitter.send(SseEmitter.event()
                                .name("meta")
                                .data(meta)
                                .id(UUID.randomUUID().toString()));
                            emitter.send(SseEmitter.event()
                                .name("complete")
                                .data("done")
                                .id(UUID.randomUUID().toString()));
                            emitter.complete();
                        } catch (IOException e) {
                            log.error("Error sending complete event", e);
                            emitter.complete();
                        }
                    },
                    error -> {
                        try {
                            emitter.send(SseEmitter.event()
                                .name("error")
                                .data(error.getMessage())
                                .id(UUID.randomUUID().toString()));
                            emitter.complete();
                        } catch (IOException e) {
                            log.error("Error sending error event", e);
                            emitter.complete();
                        }
                    }
                );
            } catch (IOException e) {
                log.error("Error starting chat stream", e);
                try {
                    emitter.send(SseEmitter.event()
                        .name("error")
                        .data("启动聊天失败: " + e.getMessage())
                        .id(UUID.randomUUID().toString()));
                    emitter.complete();
                } catch (IOException ex) {
                    log.error("Error sending error event", ex);
                    emitter.complete();
                }
            }
        }, "chat-stream-" + finalSessionId).start();

        return emitter;
    }

    @PostMapping("/feedback/{id}")
    @Operation(summary = "评价聊天", description = "评价AI回答是否有用")
    public void saveFeedback(
            @PathVariable Integer id,
            @Valid @RequestBody ChatFeedbackRequest feedback,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        chatService.saveChatFeedback(id, feedback, userPrincipal.getId());
    }

    @GetMapping("/history")
    @Operation(summary = "获取我的AI问答历史", description = "分页获取当前用户的AI问答历史")
    public ApiResponse<PageDTO<Map<String, Object>>> getMyChatHistory(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        var historyPage = chatService.getUserChatHistory(userPrincipal.getId(), page, pageSize);
        List<Map<String, Object>> records = historyPage.getContent().stream()
            .map(this::mapChatHistory)
            .toList();
        PageDTO<Map<String, Object>> pageDTO = PageDTO.<Map<String, Object>>builder()
            .total(historyPage.getTotalElements())
            .page(page)
            .pageSize(pageSize)
            .records(records)
            .build();
        return ApiResponse.success(pageDTO);
    }

    @PutMapping("/history/{id}/note")
    @Operation(summary = "更新问答备注", description = "更新当前用户某条AI问答的备注")
    public ApiResponse<Map<String, Object>> updateChatNote(
            @PathVariable Integer id,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        String note = body != null ? body.getOrDefault("chatNote", "") : "";
        AiChatHistory updated = chatService.updateChatNote(id, userPrincipal.getId(), note);
        return ApiResponse.success(mapChatHistory(updated));
    }

    @PostMapping("/history/{id}/note/generate")
    @Operation(summary = "生成问答笔记", description = "根据问答内容自动生成结构化笔记")
    public ApiResponse<Map<String, Object>> generateChatNote(
            @PathVariable Integer id,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        AiChatHistory updated = chatService.generateChatNote(id, userPrincipal.getId());
        return ApiResponse.success(mapChatHistory(updated));
    }

    @DeleteMapping("/history/{id}")
    @Operation(summary = "删除问答历史", description = "删除当前用户某条AI问答记录")
    public ApiResponse<Void> deleteChatHistory(
            @PathVariable Integer id,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        chatService.deleteChatHistory(id, userPrincipal.getId());
        return ApiResponse.success("删除成功", null);
    }

    private Map<String, Object> mapChatHistory(AiChatHistory item) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", item.getId());
        row.put("sessionId", item.getSessionId());
        row.put("modelId", item.getModel() != null ? item.getModel().getId() : null);
        row.put("modelName", item.getModel() != null ? item.getModel().getModelName() : null);
        row.put("sourceType", "model-chat");
        row.put("sourceLabel", "模型聊天");
        row.put("userQuestion", item.getUserQuestion());
        row.put("aiAnswer", item.getAiAnswer());
        row.put("aiModel", item.getAiModel());
        row.put("tokensUsed", item.getTokensUsed());
        row.put("helpful", item.getHelpful());
        row.put("chatNote", item.getChatNote());
        row.put("createdAt", item.getCreatedAt());
        return row;
    }
}
