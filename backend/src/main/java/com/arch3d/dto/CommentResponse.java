package com.arch3d.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommentResponse {
    private Integer id;
    private Integer modelId;
    private Integer userId;
    private String username;
    private String userAvatar;
    private Integer parentId;
    private String content;
    private Integer likeCount;
    private Boolean isLiked;
    private Boolean hasReplies;
    private List<CommentResponse> replies;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
