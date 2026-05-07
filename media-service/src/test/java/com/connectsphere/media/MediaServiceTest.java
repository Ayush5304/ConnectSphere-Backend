package com.connectsphere.media;

import com.connectsphere.media.entity.Story;
import com.connectsphere.media.repository.StoryRepository;
import com.connectsphere.media.repository.StoryViewRepository;
import com.connectsphere.media.service.MediaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * MediaServiceTest — Unit tests for MediaService.
 */
@ExtendWith(MockitoExtension.class)
class MediaServiceTest {

    @Mock StoryRepository storyRepository;
    @Mock StoryViewRepository storyViewRepository;
    @Mock MultipartFile mockFile;
    @InjectMocks MediaService mediaService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(mediaService, "uploadDir", "uploads/");
        ReflectionTestUtils.setField(mediaService, "maxImageSize", 10485760L);
        ReflectionTestUtils.setField(mediaService, "maxVideoSize", 104857600L);
    }

    /* ── getActiveStoriesForUsers() tests ────────────────────────────── */

    @Test
    void getActiveStoriesForUsers_returnsNonExpiredStories() {
        Story s1 = new Story(); s1.setStoryId(1L);
        Story s2 = new Story(); s2.setStoryId(2L);
        when(storyRepository.findByUserIdInAndExpiresAtAfter(any(), any()))
            .thenReturn(List.of(s1, s2));

        List<Story> result = mediaService.getActiveStoriesForUsers(List.of(1L, 2L));

        assertEquals(2, result.size());
    }

    @Test
    void getActiveStoriesForUsers_emptyList_returnsEmpty() {
        when(storyRepository.findByUserIdInAndExpiresAtAfter(any(), any()))
            .thenReturn(List.of());

        List<Story> result = mediaService.getActiveStoriesForUsers(List.of(1L));

        assertTrue(result.isEmpty());
    }

    /* ── deleteStory() tests ───────────────────────────────────────── */

    @Test
    void deleteStory_callsRepositoryDeleteById() {
        mediaService.deleteStory(1L, null, null);
        verify(storyRepository).deleteById(1L);
    }

    /* ── incrementViewCount() tests ────────────────────────────────── */

    @Test
    void incrementViewCount_ownerViewing_doesNotIncrement() {
        Story story = new Story();
        story.setStoryId(1L);
        story.setUserId(5L);
        story.setViewCount(0);
        when(storyRepository.findById(1L)).thenReturn(Optional.of(story));

        Story result = mediaService.incrementViewCount(1L, 5L, "owner");

        assertEquals(0, result.getViewCount());
        verify(storyViewRepository, never()).save(any());
    }

    @Test
    void incrementViewCount_newViewer_incrementsCount() {
        Story story = new Story();
        story.setStoryId(1L);
        story.setUserId(5L);
        story.setViewCount(0);
        when(storyRepository.findById(1L)).thenReturn(Optional.of(story));
        when(storyViewRepository.findByStoryIdAndViewerUserId(1L, 10L))
            .thenReturn(Optional.empty());
        when(storyViewRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(storyRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Story result = mediaService.incrementViewCount(1L, 10L, "viewer");

        assertEquals(1, result.getViewCount());
        verify(storyViewRepository).save(any());
    }

    @Test
    void incrementViewCount_alreadyViewed_doesNotIncrementAgain() {
        Story story = new Story();
        story.setStoryId(1L);
        story.setUserId(5L);
        story.setViewCount(1);
        when(storyRepository.findById(1L)).thenReturn(Optional.of(story));
        when(storyViewRepository.findByStoryIdAndViewerUserId(1L, 10L))
            .thenReturn(Optional.of(new com.connectsphere.media.entity.StoryView()));

        Story result = mediaService.incrementViewCount(1L, 10L, "viewer");

        assertEquals(1, result.getViewCount());
        verify(storyRepository, never()).save(any());
    }

    /* ── getStoriesByUser() tests ──────────────────────────────────── */

    @Test
    void getStoriesByUser_returnsUserStories() {
        Story s = new Story(); s.setUserId(1L);
        when(storyRepository.findByUserIdInAndExpiresAtAfter(any(), any()))
            .thenReturn(List.of(s));

        List<Story> result = mediaService.getStoriesByUser(1L);

        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).getUserId());
    }
}
