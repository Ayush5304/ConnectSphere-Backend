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
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.List;
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
}
