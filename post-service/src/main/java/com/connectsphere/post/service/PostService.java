package com.connectsphere.post.service;

import com.connectsphere.post.entity.Post;
import java.util.List;
import java.util.Map;

public interface PostService {
    Post createPost(Long userId, String username, String content, String mediaUrl, Post.Visibility visibility);
    Post editPost(Long postId, String content, String mediaUrl, Post.Visibility visibility);
    Post getById(Long postId);
    List<Post> getByUser(Long userId);
    List<Post> getPublicFeed();
    List<Post> getFeedForUsers(List<Long> userIds);
    List<Post> search(String keyword);
    Post updateVisibility(Long postId, Post.Visibility visibility);
    void deletePost(Long postId);
    void softDeletePost(Long postId);
    void incrementLikes(Long postId);
    void decrementLikes(Long postId);
    void incrementComments(Long postId);
    long countByUser(Long userId);
    List<Post> getAllPosts();
    void reportPost(Long postId, String reason);
    List<Post> getReportedPosts();
    void clearReport(Long postId);
    Map<String, Object> getAnalytics();
    void boostPost(Long postId);
}
