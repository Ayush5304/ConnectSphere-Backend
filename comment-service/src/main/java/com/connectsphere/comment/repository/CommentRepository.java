package com.connectsphere.comment.repository;

import com.connectsphere.comment.entity.Comment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, Long> {
    List<Comment> findByPostIdAndParentCommentIdIsNullOrderByCreatedAtAsc(Long postId);
    List<Comment> findByParentCommentIdOrderByCreatedAtAsc(Long parentCommentId);
    List<Comment> findByPostIdAndParentCommentIdIsNullAndDeletedFalseOrderByCreatedAtAsc(Long postId);
    List<Comment> findByParentCommentIdAndDeletedFalseOrderByCreatedAtAsc(Long parentCommentId);
    long countByPostIdAndDeletedFalse(Long postId);
}
