package com.connectsphere.post;

import com.connectsphere.post.entity.Post;
import com.connectsphere.post.exception.BadRequestException;
import com.connectsphere.post.exception.ResourceNotFoundException;
import com.connectsphere.post.repository.PostRepository;
import com.connectsphere.post.service.PostServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * PostServiceTest — Unit tests for PostServiceImpl.
 * Uses Mockito to mock all dependencies.
 */
@ExtendWith(MockitoExtension.class)
class PostServiceTest {

    @Mock PostRepository postRepository;
    @Mock RabbitTemplate rabbitTemplate;
    @Mock RestTemplate restTemplate;
    @InjectMocks PostServiceImpl postService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(postService, "searchServiceUrl", "http://localhost:8088");
        ReflectionTestUtils.setField(postService, "authServiceUrl", "http://localhost:8081");
    }

    /* ── createPost() tests ────────────────────────────────────────── */

    @Test
    void createPost_success_savesAndReturns() {
        Post saved = new Post();
        saved.setPostId(1L);
        saved.setContent("Hello world");
        when(postRepository.save(any())).thenReturn(saved);

        Post result = postService.createPost(1L, "testuser", "Hello world", null, Post.Visibility.PUBLIC);

        assertNotNull(result);
        assertEquals("Hello world", result.getContent());
        verify(postRepository).save(any(Post.class));
    }

    @Test
    void createPost_nullUserId_throwsBadRequestException() {
        assertThrows(BadRequestException.class,
            () -> postService.createPost(null, "testuser", "Hello", null, Post.Visibility.PUBLIC));
    }

    @Test
    void createPost_blankUsername_throwsBadRequestException() {
        assertThrows(BadRequestException.class,
            () -> postService.createPost(1L, "", "Hello", null, Post.Visibility.PUBLIC));
    }

    @Test
    void createPost_blankContent_throwsBadRequestException() {
        assertThrows(BadRequestException.class,
            () -> postService.createPost(1L, "testuser", "  ", null, Post.Visibility.PUBLIC));
    }

    @Test
    void createPost_defaultsToPublicVisibility() {
        Post saved = new Post();
        saved.setPostId(1L);
        saved.setVisibility(Post.Visibility.PUBLIC);
        when(postRepository.save(any())).thenReturn(saved);

        Post result = postService.createPost(1L, "testuser", "Hello", null, null);

        assertEquals(Post.Visibility.PUBLIC, result.getVisibility());
    }

    /* ── getById() tests ───────────────────────────────────────────── */

    @Test
    void getById_found_returnsPost() {
        Post post = new Post();
        post.setPostId(1L);
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));

        Post result = postService.getById(1L);

        assertEquals(1L, result.getPostId());
    }

    @Test
    void getById_notFound_throwsResourceNotFoundException() {
        when(postRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> postService.getById(99L));
    }

    /* ── getPublicFeed() tests ─────────────────────────────────────── */

    @Test
    void getPublicFeed_returnsList() {
        when(postRepository.findByVisibilityAndDeletedFalseOrderByCreatedAtDesc(Post.Visibility.PUBLIC))
            .thenReturn(List.of(new Post(), new Post()));

        List<Post> result = postService.getPublicFeed();

        assertEquals(2, result.size());
    }

    /* ── softDeletePost() tests ────────────────────────────────────── */

    @Test
    void softDeletePost_marksDeletedTrue() {
        Post post = new Post();
        post.setPostId(1L);
        post.setDeleted(false);
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));
        when(postRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        postService.softDeletePost(1L);

        assertTrue(post.isDeleted());
        verify(postRepository).save(post);
    }

    /* ── deletePost() tests ────────────────────────────────────────── */

    @Test
    void deletePost_callsRepositoryDeleteById() {
        postService.deletePost(1L);
        verify(postRepository).deleteById(1L);
    }

    /* ── incrementLikes() tests ────────────────────────────────────── */

    @Test
    void incrementLikes_incrementsCountByOne() {
        Post post = new Post();
        post.setPostId(1L);
        post.setLikesCount(5);
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));
        when(postRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        postService.incrementLikes(1L);

        assertEquals(6, post.getLikesCount());
    }

    /* ── decrementLikes() tests ────────────────────────────────────── */

    @Test
    void decrementLikes_decrementsCountByOne() {
        Post post = new Post();
        post.setPostId(1L);
        post.setLikesCount(3);
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));
        when(postRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        postService.decrementLikes(1L);

        assertEquals(2, post.getLikesCount());
    }

    @Test
    void decrementLikes_doesNotGoBelowZero() {
        Post post = new Post();
        post.setPostId(1L);
        post.setLikesCount(0);
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));
        when(postRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        postService.decrementLikes(1L);

        assertEquals(0, post.getLikesCount());
    }

    /* ── reportPost() tests ────────────────────────────────────────── */

    @Test
    void reportPost_setsReportedTrueAndReason() {
        Post post = new Post();
        post.setPostId(1L);
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));
        when(postRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        postService.reportPost(1L, "Spam content");

        assertTrue(post.isReported());
        assertEquals("Spam content", post.getReportReason());
    }

    /* ── updateVisibility() tests ──────────────────────────────────── */

    @Test
    void updateVisibility_changesVisibility() {
        Post post = new Post();
        post.setPostId(1L);
        post.setVisibility(Post.Visibility.PUBLIC);
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));
        when(postRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Post result = postService.updateVisibility(1L, Post.Visibility.PRIVATE);

        assertEquals(Post.Visibility.PRIVATE, result.getVisibility());
    }

    @Test
    void createPost_mediaOnlyAndPrivateVisibility_savesExpectedFields() {
        when(postRepository.save(any(Post.class))).thenAnswer(i -> {
            Post p = i.getArgument(0);
            p.setPostId(22L);
            return p;
        });

        Post result = postService.createPost(3L, "mediauser", null, "/media/photo.jpg", Post.Visibility.FOLLOWERS);

        assertEquals(22L, result.getPostId());
        assertEquals("", result.getContent());
        assertEquals("/media/photo.jpg", result.getMediaUrl());
        assertEquals(Post.Visibility.FOLLOWERS, result.getVisibility());
    }

    @Test
    void createPost_mentionsOtherUser_publishesMentionNotification() {
        when(postRepository.save(any(Post.class))).thenAnswer(i -> {
            Post p = i.getArgument(0);
            p.setPostId(31L);
            return p;
        });
        when(restTemplate.exchange(anyString(), any(), isNull(), any(ParameterizedTypeReference.class)))
            .thenReturn(ResponseEntity.ok(List.of(Map.of("userId", 99, "username", "friend"))));

        Post result = postService.createPost(1L, "author", "hello @friend", null, Post.Visibility.PUBLIC);

        assertEquals(31L, result.getPostId());
        verify(rabbitTemplate).convertAndSend(eq("connectsphere.events"), eq("mention.created"), any(Map.class));
    }

    @Test
    void createPost_selfMentionAndSideEffectFailure_doNotBreakCreation() {
        when(postRepository.save(any(Post.class))).thenAnswer(i -> {
            Post p = i.getArgument(0);
            p.setPostId(32L);
            return p;
        });
        when(restTemplate.exchange(anyString(), any(), isNull(), any(ParameterizedTypeReference.class)))
            .thenReturn(ResponseEntity.ok(List.of(Map.of("userId", 1, "username", "author"))));

        Post result = postService.createPost(1L, "author", "hello @author", null, Post.Visibility.PUBLIC);

        assertEquals(32L, result.getPostId());
        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(Map.class));

        when(restTemplate.exchange(anyString(), any(), isNull(), any(ParameterizedTypeReference.class)))
            .thenThrow(new RuntimeException("auth down"));
        Post second = postService.createPost(1L, "author", "hello @missing", null, Post.Visibility.PUBLIC);
        assertNotNull(second);
    }

    @Test
    void editPost_updatesProvidedFieldsAndKeepsNullFields() {
        Post post = post(41L);
        post.setContent("old");
        post.setMediaUrl("/old.jpg");
        post.setVisibility(Post.Visibility.PUBLIC);
        when(postRepository.findById(41L)).thenReturn(Optional.of(post));
        when(postRepository.save(any(Post.class))).thenAnswer(i -> i.getArgument(0));

        Post updated = postService.editPost(41L, "new #tag", null, Post.Visibility.PRIVATE);

        assertEquals("new #tag", updated.getContent());
        assertEquals("/old.jpg", updated.getMediaUrl());
        assertEquals(Post.Visibility.PRIVATE, updated.getVisibility());
        assertNotNull(updated.getUpdatedAt());
    }

    @Test
    void editPost_indexFailureStillReturnsUpdatedPost() {
        Post post = post(42L);
        post.setContent("old");
        when(postRepository.findById(42L)).thenReturn(Optional.of(post));
        when(postRepository.save(any(Post.class))).thenAnswer(i -> i.getArgument(0));
        when(restTemplate.postForObject(anyString(), any(), eq(Void.class))).thenThrow(new RuntimeException("search down"));

        Post updated = postService.editPost(42L, "new", "/new.jpg", null);

        assertEquals("new", updated.getContent());
        assertEquals("/new.jpg", updated.getMediaUrl());
    }

    @Test
    void softDeletePost_withMedia_marksMediaDeleted() {
        Post post = post(51L);
        post.setMediaUrl("/media/video.mp4");
        when(postRepository.findById(51L)).thenReturn(Optional.of(post));
        when(postRepository.save(any(Post.class))).thenAnswer(i -> i.getArgument(0));

        postService.softDeletePost(51L);

        assertTrue(post.isDeleted());
        assertTrue(post.isMediaDeleted());
        assertNotNull(post.getMediaDeletedAt());
    }

    @Test
    void queryMethods_delegateToRepository() {
        Post p1 = post(1L);
        Post p2 = post(2L);
        when(postRepository.findByUserIdAndDeletedFalseOrderByCreatedAtDesc(10L)).thenReturn(List.of(p1, p2));
        assertEquals(2, postService.getByUser(10L).size());
        assertEquals(2, postService.countByUser(10L));

        when(postRepository.findByUserIdInAndDeletedFalseOrderByCreatedAtDesc(List.of(10L, 11L))).thenReturn(List.of(p1));
        assertEquals(1, postService.getFeedForUsers(List.of(10L, 11L)).size());

        when(postRepository.findByContentContainingIgnoreCaseAndVisibilityAndDeletedFalse("hello", Post.Visibility.PUBLIC))
            .thenReturn(List.of(p2));
        assertEquals(1, postService.search("hello").size());

        when(postRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(p1, p2));
        assertEquals(2, postService.getAllPosts().size());

        when(postRepository.findByReportedTrueOrderByCreatedAtDesc()).thenReturn(List.of(p1));
        assertEquals(1, postService.getReportedPosts().size());
    }

    @Test
    void incrementComments_clearReport_analyticsAndBoost_coverRemainingServiceBranches() {
        Post post = post(61L);
        post.setCommentsCount(4);
        post.setReported(true);
        post.setReportReason("spam");
        when(postRepository.findById(61L)).thenReturn(Optional.of(post));
        when(postRepository.save(any(Post.class))).thenAnswer(i -> i.getArgument(0));

        postService.incrementComments(61L);
        assertEquals(5, post.getCommentsCount());

        postService.clearReport(61L);
        assertFalse(post.isReported());
        assertNull(post.getReportReason());

        postService.boostPost(61L);
        assertTrue(post.isBoosted());
        assertNotNull(post.getUpdatedAt());

        when(postRepository.countByDeletedFalse()).thenReturn(12L);
        when(postRepository.countByVisibilityAndDeletedFalse(Post.Visibility.PUBLIC)).thenReturn(9L);
        when(postRepository.countByReportedTrueAndDeletedFalse()).thenReturn(2L);
        Map<String, Object> analytics = postService.getAnalytics();
        assertEquals(12L, analytics.get("totalPosts"));
        assertEquals(9L, analytics.get("publicPosts"));
        assertEquals(2L, analytics.get("reportedPosts"));
    }

    private Post post(Long id) {
        Post post = new Post();
        post.setPostId(id);
        post.setUserId(1L);
        post.setUsername("author");
        post.setContent("content");
        post.setVisibility(Post.Visibility.PUBLIC);
        return post;
    }

}
