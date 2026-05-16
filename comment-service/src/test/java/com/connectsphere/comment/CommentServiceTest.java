package com.connectsphere.comment;

import com.connectsphere.comment.entity.Comment;
import com.connectsphere.comment.exception.BadRequestException;
import com.connectsphere.comment.exception.ResourceNotFoundException;
import com.connectsphere.comment.repository.CommentRepository;
import com.connectsphere.comment.service.CommentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * CommentServiceTest — Unit tests for CommentService.
 * Uses Mockito to mock all dependencies.
 */
@ExtendWith(MockitoExtension.class)
class CommentServiceTest {

    @Mock CommentRepository commentRepository;
    @Mock RabbitTemplate rabbitTemplate;
    @Mock RestTemplate restTemplate;
    @InjectMocks CommentService commentService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(commentService, "postServiceUrl", "http://localhost:8082");
        ReflectionTestUtils.setField(commentService, "frontendUrl", "http://localhost:3000");
    }

    /* ── addComment() tests ────────────────────────────────────────── */

    @Test
    void addComment_success_savesAndReturns() {
        Comment saved = new Comment();
        saved.setCommentId(1L);
        saved.setContent("Nice post!");
        when(commentRepository.save(any())).thenReturn(saved);

        Comment result = commentService.addComment(1L, 2L, "testuser", "Nice post!", null);

        assertNotNull(result);
        assertEquals("Nice post!", result.getContent());
        verify(commentRepository).save(any(Comment.class));
    }

    @Test
    void addComment_nullPostId_throwsBadRequestException() {
        assertThrows(BadRequestException.class,
            () -> commentService.addComment(null, 2L, "testuser", "Nice post!", null));
    }

    @Test
    void addComment_nullUserId_throwsBadRequestException() {
        assertThrows(BadRequestException.class,
            () -> commentService.addComment(1L, null, "testuser", "Nice post!", null));
    }

    @Test
    void addComment_blankUsername_throwsBadRequestException() {
        assertThrows(BadRequestException.class,
            () -> commentService.addComment(1L, 2L, "", "Nice post!", null));
    }

    @Test
    void addComment_blankContent_throwsBadRequestException() {
        assertThrows(BadRequestException.class,
            () -> commentService.addComment(1L, 2L, "testuser", "  ", null));
    }

    @Test
    void addComment_incrementsPostCommentCount() {
        when(commentRepository.save(any())).thenReturn(new Comment());

        commentService.addComment(1L, 2L, "testuser", "Nice post!", null);

        verify(restTemplate).put(contains("/posts/1/comments/increment"), isNull());
    }

    /* ── getByPost() tests ─────────────────────────────────────────── */

    @Test
    void getByPost_returnsTopLevelComments() {
        Comment c1 = new Comment(); c1.setCommentId(1L);
        Comment c2 = new Comment(); c2.setCommentId(2L);
        when(commentRepository
            .findByPostIdAndParentCommentIdIsNullAndDeletedFalseOrderByCreatedAtAsc(1L))
            .thenReturn(List.of(c1, c2));

        List<Comment> result = commentService.getByPost(1L);

        assertEquals(2, result.size());
    }

    @Test
    void getByPost_noComments_returnsEmptyList() {
        when(commentRepository
            .findByPostIdAndParentCommentIdIsNullAndDeletedFalseOrderByCreatedAtAsc(1L))
            .thenReturn(List.of());

        List<Comment> result = commentService.getByPost(1L);

        assertTrue(result.isEmpty());
    }

    /* ── editComment() tests ───────────────────────────────────────── */

    @Test
    void editComment_success_updatesContent() {
        Comment comment = new Comment();
        comment.setCommentId(1L);
        comment.setContent("Old content");
        when(commentRepository.findById(1L)).thenReturn(Optional.of(comment));
        when(commentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Comment result = commentService.editComment(1L, "New content");

        assertEquals("New content", result.getContent());
        assertNotNull(result.getUpdatedAt());
    }

    @Test
    void editComment_notFound_throwsResourceNotFoundException() {
        when(commentRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class,
            () -> commentService.editComment(99L, "New content"));
    }

    @Test
    void editComment_blankContent_throwsBadRequestException() {
        assertThrows(BadRequestException.class,
            () -> commentService.editComment(1L, ""));
    }

    /* ── softDeleteComment() tests ─────────────────────────────────── */

    @Test
    void softDeleteComment_existingComment_marksDeleted() {
        Comment comment = new Comment();
        comment.setCommentId(1L);
        comment.setDeleted(false);
        when(commentRepository.findById(1L)).thenReturn(Optional.of(comment));
        when(commentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        commentService.softDeleteComment(1L);

        assertTrue(comment.isDeleted());
        verify(commentRepository).save(comment);
    }

    @Test
    void softDeleteComment_notFound_doesNothing() {
        when(commentRepository.findById(99L)).thenReturn(Optional.empty());

        commentService.softDeleteComment(99L);

        verify(commentRepository, never()).save(any());
    }

    /* ── deleteComment() tests ─────────────────────────────────────── */

    @Test
    void deleteComment_callsRepositoryDeleteById() {
        commentService.deleteComment(1L);
        verify(commentRepository).deleteById(1L);
    }

    /* ── getReplies() tests ────────────────────────────────────────── */

    @Test
    void getReplies_returnsRepliesForComment() {
        Comment reply = new Comment();
        reply.setCommentId(5L);
        reply.setParentCommentId(1L);
        when(commentRepository.findByParentCommentIdAndDeletedFalseOrderByCreatedAtAsc(1L))
            .thenReturn(List.of(reply));

        List<Comment> result = commentService.getReplies(1L);

        assertEquals(1, result.size());
        assertEquals(5L, result.get(0).getCommentId());
    }

    @Test
    void addComment_topLevelNotifiesPostOwnerWhenDifferentUser() {
        Comment saved = comment(10L, 100L, 2L, "testuser");
        when(commentRepository.save(any(Comment.class))).thenReturn(saved);
        when(restTemplate.getForObject(contains("/posts/100"), eq(Map.class))).thenReturn(Map.of("userId", 7));

        Comment result = commentService.addComment(100L, 2L, "testuser", "Nice", null);

        assertEquals(10L, result.getCommentId());
        verify(rabbitTemplate).convertAndSend(eq("connectsphere.events"), eq("comment.created"), any(Map.class));
    }

    @Test
    void addComment_topLevelSkipsNotificationForOwnPostAndToleratesPostLookupFailure() {
        when(commentRepository.save(any(Comment.class))).thenAnswer(i -> i.getArgument(0));
        when(restTemplate.getForObject(contains("/posts/101"), eq(Map.class))).thenReturn(Map.of("userId", 2));

        commentService.addComment(101L, 2L, "testuser", "Own post", null);

        verify(rabbitTemplate, never()).convertAndSend(eq("connectsphere.events"), eq("comment.created"), any(Map.class));

        when(restTemplate.getForObject(contains("/posts/102"), eq(Map.class))).thenThrow(new RuntimeException("post down"));
        Comment result = commentService.addComment(102L, 2L, "testuser", "Still saves", null);
        assertNotNull(result);
    }

    @Test
    void addComment_incrementFailureStillSaves() {
        when(commentRepository.save(any(Comment.class))).thenAnswer(i -> i.getArgument(0));
        doThrow(new RuntimeException("post down")).when(restTemplate).put(contains("/posts/200/comments/increment"), isNull());

        Comment result = commentService.addComment(200L, 2L, "testuser", "Nice", null);

        assertEquals(200L, result.getPostId());
    }

    @Test
    void addReply_notifiesParentAuthorWhenDifferentUser() {
        Comment parent = comment(1L, 300L, 9L, "parent");
        Comment saved = comment(2L, 300L, 2L, "replyUser");
        saved.setParentCommentId(1L);
        when(commentRepository.save(any(Comment.class))).thenReturn(saved);
        when(commentRepository.findById(1L)).thenReturn(Optional.of(parent));

        Comment result = commentService.addComment(300L, 2L, "replyUser", "reply", 1L);

        assertEquals(1L, result.getParentCommentId());
        verify(rabbitTemplate).convertAndSend(eq("connectsphere.events"), eq("reply.created"), any(Map.class));
    }

    @Test
    void addReply_skipsSelfNotificationAndToleratesLookupFailure() {
        Comment parent = comment(1L, 301L, 2L, "same");
        when(commentRepository.save(any(Comment.class))).thenAnswer(i -> i.getArgument(0));
        when(commentRepository.findById(1L)).thenReturn(Optional.of(parent));

        commentService.addComment(301L, 2L, "same", "reply", 1L);

        verify(rabbitTemplate, never()).convertAndSend(eq("connectsphere.events"), eq("reply.created"), any(Map.class));

        when(commentRepository.findById(2L)).thenThrow(new RuntimeException("db down"));
        Comment result = commentService.addComment(302L, 2L, "same", "reply", 2L);
        assertEquals(302L, result.getPostId());
    }

    @Test
    void adminQueryAndReportMethods_coverRemainingBranches() {
        Comment c1 = comment(1L, 1L, 2L, "one");
        Comment c2 = comment(2L, 1L, 3L, "two");
        when(commentRepository.findAll()).thenReturn(List.of(c1, c2));
        assertEquals(2, commentService.getAllComments().size());

        assertThrows(BadRequestException.class, () -> commentService.reportComment(1L, ""));
        when(commentRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> commentService.reportComment(99L, "spam"));
        assertThrows(ResourceNotFoundException.class, () -> commentService.clearReport(99L));

        when(commentRepository.findById(1L)).thenReturn(Optional.of(c1));
        when(commentRepository.save(any(Comment.class))).thenAnswer(i -> i.getArgument(0));
        commentService.reportComment(1L, "spam");
        assertTrue(c1.isReported());
        assertEquals("spam", c1.getReportReason());

        commentService.clearReport(1L);
        assertFalse(c1.isReported());
        assertNull(c1.getReportReason());

        when(commentRepository.findByReportedTrueOrderByCreatedAtDesc()).thenReturn(List.of(c2));
        assertEquals(1, commentService.getReportedComments().size());
    }

    private Comment comment(Long id, Long postId, Long userId, String username) {
        Comment comment = new Comment();
        comment.setCommentId(id);
        comment.setPostId(postId);
        comment.setUserId(userId);
        comment.setUsername(username);
        comment.setContent("content");
        return comment;
    }

}
