package com.connectsphere.post.repository;

import com.connectsphere.post.entity.Post;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PostRepository extends JpaRepository<Post, Long> {
    List<Post> findByUserIdOrderByCreatedAtDesc(Long userId);
    List<Post> findByVisibilityOrderByCreatedAtDesc(Post.Visibility visibility);
    List<Post> findByUserIdInOrderByCreatedAtDesc(List<Long> userIds);
    List<Post> findByContentContainingIgnoreCaseAndVisibility(String keyword, Post.Visibility visibility);
    long countByUserId(Long userId);
    List<Post> findAllByOrderByCreatedAtDesc();
    List<Post> findByReportedTrueOrderByCreatedAtDesc();
    long countByVisibility(Post.Visibility visibility);
    long countByReportedTrue();
    List<Post> findByUserIdAndDeletedFalseOrderByCreatedAtDesc(Long userId);
    List<Post> findByVisibilityAndDeletedFalseOrderByCreatedAtDesc(Post.Visibility visibility);
    List<Post> findByUserIdInAndDeletedFalseOrderByCreatedAtDesc(List<Long> userIds);
    List<Post> findByContentContainingIgnoreCaseAndVisibilityAndDeletedFalse(String keyword, Post.Visibility visibility);
    long countByDeletedFalse();
    long countByVisibilityAndDeletedFalse(Post.Visibility visibility);
    long countByReportedTrueAndDeletedFalse();
}
