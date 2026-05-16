package com.connectsphere.media;

import com.connectsphere.media.entity.Story;
import com.connectsphere.media.entity.StoryView;
import com.connectsphere.media.repository.StoryRepository;
import com.connectsphere.media.repository.StoryViewRepository;
import com.connectsphere.media.service.MediaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MediaServiceTest {

    @Mock StoryRepository storyRepository;
    @Mock StoryViewRepository storyViewRepository;
    @InjectMocks MediaService mediaService;

    @TempDir Path tempDir;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(mediaService, "uploadDir", tempDir.toString());
        ReflectionTestUtils.setField(mediaService, "maxImageSize", 10_485_760L);
        ReflectionTestUtils.setField(mediaService, "maxVideoSize", 104_857_600L);
        ReflectionTestUtils.setField(mediaService, "publicUrlPrefix", "/media/files");
    }

    @Test
    void uploadFile_acceptsCameraWebmWithCodecContentType() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file", "camera-story.webm", "video/webm;codecs=vp9", new byte[] {1, 2, 3});

        String url = mediaService.uploadFile(file);

        assertTrue(url.startsWith("/media/files/"));
        assertEquals(1, Files.list(tempDir).count());
    }

    @Test
    void uploadFile_rejectsUnsupportedType() {
        MockMultipartFile file = new MockMultipartFile(
            "file", "story.gif", "image/gif", new byte[] {1});

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> mediaService.uploadFile(file));

        assertTrue(ex.getMessage().contains("Unsupported file type"));
    }

    @Test
    void uploadFile_rejectsAllowedTypeWithDisallowedExtension() {
        MockMultipartFile file = new MockMultipartFile(
            "file", "story.txt", "image/jpeg", new byte[] {1});

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> mediaService.uploadFile(file));

        assertTrue(ex.getMessage().contains("extension"));
    }

    @Test
    void uploadFile_rejectsOversizedVideo() {
        ReflectionTestUtils.setField(mediaService, "maxVideoSize", 2L);
        MockMultipartFile file = new MockMultipartFile(
            "file", "story.mp4", "video/mp4", new byte[] {1, 2, 3});

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> mediaService.uploadFile(file));

        assertTrue(ex.getMessage().contains("File too large"));
    }

    @Test
    void createStory_uploadsMediaAndSavesStory() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file", "story.jpg", "image/jpeg", new byte[] {7, 8, 9});
        when(storyRepository.save(any(Story.class))).thenAnswer(invocation -> {
            Story story = invocation.getArgument(0);
            story.setStoryId(99L);
            return story;
        });

        Story saved = mediaService.createStory(5L, "ayush", file);

        assertEquals(99L, saved.getStoryId());
        assertEquals(5L, saved.getUserId());
        assertEquals("ayush", saved.getUsername());
        assertEquals("image/jpeg", saved.getMediaType());
        assertTrue(saved.getMediaUrl().contains("/media/files/"));
    }

    @Test
    void getActiveStoriesForUsers_returnsRepositoryResults() {
        Story story = new Story();
        when(storyRepository.findByUserIdInAndExpiresAtAfter(any(), any()))
            .thenReturn(List.of(story));

        List<Story> result = mediaService.getActiveStoriesForUsers(List.of(1L, 2L));

        assertEquals(1, result.size());
    }

    @Test
    void getStoriesByUser_returnsActiveStoriesForSingleUser() {
        Story story = new Story();
        when(storyRepository.findByUserIdInAndExpiresAtAfter(eq(List.of(5L)), any()))
            .thenReturn(List.of(story));

        List<Story> result = mediaService.getStoriesByUser(5L);

        assertEquals(List.of(story), result);
    }

    @Test
    void deleteStory_allowsOwnerDeletesViewsFileAndEntity() throws Exception {
        Path uploaded = tempDir.resolve("owned.jpg");
        Files.write(uploaded, new byte[] {1});
        Story story = new Story();
        story.setStoryId(10L);
        story.setUserId(4L);
        story.setMediaUrl("http://localhost:8080/api/media/files/owned.jpg");
        when(storyRepository.findById(10L)).thenReturn(Optional.of(story));

        mediaService.deleteStory(10L, 4L, "USER");

        verify(storyViewRepository).deleteByStoryId(10L);
        verify(storyRepository).delete(story);
        assertFalse(Files.exists(uploaded));
    }

    @Test
    void deleteStory_allowsAdminToDeleteAnyStory() {
        Story story = new Story();
        story.setStoryId(10L);
        story.setUserId(4L);
        story.setMediaUrl("http://localhost:8080/api/media/files/missing.jpg");
        when(storyRepository.findById(10L)).thenReturn(Optional.of(story));

        assertDoesNotThrow(() -> mediaService.deleteStory(10L, 99L, "ADMIN"));

        verify(storyRepository).delete(story);
    }

    @Test
    void deleteStory_rejectsNonOwnerNonAdmin() {
        Story story = new Story();
        story.setStoryId(10L);
        story.setUserId(4L);
        when(storyRepository.findById(10L)).thenReturn(Optional.of(story));

        assertThrows(SecurityException.class, () -> mediaService.deleteStory(10L, 7L, "USER"));

        verify(storyRepository, never()).delete(any());
    }

    @Test
    void deleteStory_missingStoryThrowsRuntimeException() {
        when(storyRepository.findById(404L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> mediaService.deleteStory(404L, 1L, "ADMIN"));
    }

    @Test
    void reportStory_usesProvidedReasonAndSaves() {
        Story story = new Story();
        when(storyRepository.findById(1L)).thenReturn(Optional.of(story));

        mediaService.reportStory(1L, "spam");

        assertTrue(story.isReported());
        assertEquals("spam", story.getReportReason());
        verify(storyRepository).save(story);
    }

    @Test
    void reportStory_usesDefaultReasonWhenReasonIsNull() {
        Story story = new Story();
        when(storyRepository.findById(1L)).thenReturn(Optional.of(story));

        mediaService.reportStory(1L, null);

        assertEquals("Reported from story viewer", story.getReportReason());
    }

    @Test
    void reportStory_missingStoryThrowsRuntimeException() {
        when(storyRepository.findById(404L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> mediaService.reportStory(404L, "spam"));
    }

    @Test
    void incrementViewCount_newViewerRecordsUniqueView() {
        Story story = new Story();
        story.setStoryId(1L);
        story.setUserId(5L);
        story.setViewCount(0);
        when(storyRepository.findById(1L)).thenReturn(Optional.of(story));
        when(storyViewRepository.findByStoryIdAndViewerUserId(1L, 10L)).thenReturn(Optional.empty());
        when(storyViewRepository.save(any(StoryView.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(storyRepository.save(any(Story.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Story result = mediaService.incrementViewCount(1L, 10L, "viewer");

        assertEquals(1, result.getViewCount());
        verify(storyViewRepository).save(any(StoryView.class));
    }

    @Test
    void incrementViewCount_ownerViewDoesNotCount() {
        Story story = new Story();
        story.setStoryId(1L);
        story.setUserId(5L);
        when(storyRepository.findById(1L)).thenReturn(Optional.of(story));

        Story result = mediaService.incrementViewCount(1L, 5L, "owner");

        assertEquals(0, result.getViewCount());
        verify(storyViewRepository, never()).save(any());
    }

    @Test
    void incrementViewCount_existingViewerDoesNotIncrementAgain() {
        Story story = new Story();
        story.setStoryId(1L);
        story.setUserId(5L);
        story.setViewCount(2);
        when(storyRepository.findById(1L)).thenReturn(Optional.of(story));
        when(storyViewRepository.findByStoryIdAndViewerUserId(1L, 10L))
            .thenReturn(Optional.of(new StoryView()));

        Story result = mediaService.incrementViewCount(1L, 10L, null);

        assertEquals(2, result.getViewCount());
        verify(storyViewRepository, never()).save(any());
        verify(storyRepository, never()).save(any());
    }

    @Test
    void incrementViewCount_missingStoryThrowsRuntimeException() {
        when(storyRepository.findById(404L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> mediaService.incrementViewCount(404L, 10L, "viewer"));
    }

    @Test
    void getViewers_returnsNewestViewers() {
        when(storyViewRepository.findByStoryIdOrderByViewedAtDesc(1L))
            .thenReturn(List.of(new StoryView(), new StoryView()));

        assertEquals(2, mediaService.getViewers(1L).size());
    }

    @Test
    void purgeExpiredStories_removesMediaAndDeletesRows() throws Exception {
        Path uploaded = tempDir.resolve("old.jpg");
        Files.write(uploaded, new byte[] {1});
        Story expired = new Story();
        expired.setStoryId(1L);
        expired.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        expired.setMediaUrl("http://localhost:8080/api/media/files/old.jpg");
        when(storyRepository.findByExpiresAtBefore(any())).thenReturn(List.of(expired));

        mediaService.purgeExpiredStories();

        verify(storyRepository).deleteAll(List.of(expired));
        assertFalse(Files.exists(uploaded));
    }
}
