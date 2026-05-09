package com.connectsphere.media.service;

import com.connectsphere.media.entity.Story;
import com.connectsphere.media.entity.StoryView;
import com.connectsphere.media.repository.StoryRepository;
import com.connectsphere.media.repository.StoryViewRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MediaService {

    private final StoryRepository storyRepository;
    private final StoryViewRepository storyViewRepository;

    @Value("${media.upload.dir:uploads/}")
    private String uploadDir;

    @Value("${media.max-image-size:10485760}")
    private long maxImageSize;

    @Value("${media.max-video-size:104857600}")
    private long maxVideoSize;

    private static final Map<String, String> ALLOWED_TYPES = Map.of(
            "image/jpeg", "image",
            "image/png", "image",
            "image/webp", "image",
            "video/mp4", "video",
            "video/webm", "video"
    );

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            ".jpg", ".jpeg", ".png", ".webp", ".mp4", ".webm"
    );

    private void validate(MultipartFile file) {
        String rawContentType = file.getContentType();
        String contentType = rawContentType != null
                ? rawContentType.toLowerCase().split(";")[0].trim()
                : "";

        if (!ALLOWED_TYPES.containsKey(contentType)) {
            throw new IllegalArgumentException(
                    "Unsupported file type: " + rawContentType +
                            ". Allowed: JPEG, PNG, WebP, MP4, WebM."
            );
        }

        String originalName = file.getOriginalFilename() != null
                ? file.getOriginalFilename().toLowerCase()
                : "";

        boolean extOk = ALLOWED_EXTENSIONS.stream().anyMatch(originalName::endsWith);

        if (!extOk) {
            throw new IllegalArgumentException("File extension not allowed.");
        }

        String category = ALLOWED_TYPES.get(contentType);
        long limit = "video".equals(category) ? maxVideoSize : maxImageSize;

        if (file.getSize() > limit) {
            throw new IllegalArgumentException(
                    "File too large. Max size for " + category + ": " + (limit / 1_048_576) + " MB."
            );
        }
    }

    private String safeFilename(MultipartFile file) {
        String original = file.getOriginalFilename() != null
                ? file.getOriginalFilename()
                : "file";

        int dot = original.lastIndexOf('.');
        String ext = dot >= 0 ? original.substring(dot).toLowerCase() : "";

        return UUID.randomUUID() + ext;
    }

    public String uploadFile(MultipartFile file) throws IOException {
        validate(file);

        Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
        Files.createDirectories(uploadPath);

        String filename = safeFilename(file);
        Path dest = uploadPath.resolve(filename).normalize();

        if (!dest.startsWith(uploadPath)) {
            throw new IllegalArgumentException("Invalid file path.");
        }

        Files.write(dest, file.getBytes());

        return "http://localhost:8080/api/media/files/" + filename;
    }

    public Story createStory(Long userId, String username, MultipartFile file) throws IOException {
        String url = uploadFile(file);

        Story story = new Story();
        story.setUserId(userId);
        story.setUsername(username);
        story.setMediaUrl(url);

        String storyType = file.getContentType() != null
                ? file.getContentType().toLowerCase().split(";")[0].trim()
                : "application/octet-stream";

        story.setMediaType(storyType);

        return storyRepository.save(story);
    }

    public List<Story> getActiveStoriesForUsers(List<Long> userIds) {
        return storyRepository.findByUserIdInAndExpiresAtAfter(userIds, LocalDateTime.now());
    }

    public List<Story> getStoriesByUser(Long userId) {
        return storyRepository.findByUserIdInAndExpiresAtAfter(
                List.of(userId),
                LocalDateTime.now()
        );
    }

    private void deleteMediaFile(String mediaUrl) {
        if (mediaUrl == null || mediaUrl.isBlank()) {
            return;
        }

        try {
            String filename = mediaUrl.substring(mediaUrl.lastIndexOf('/') + 1);
            Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
            Path file = uploadPath.resolve(filename).normalize();

            if (file.startsWith(uploadPath)) {
                Files.deleteIfExists(file);
            }
        } catch (Exception ignored) {
        }
    }

    @Transactional
    public void deleteStory(Long storyId, Long requesterUserId, String requesterRole) {
        Story story = storyRepository.findById(storyId)
                .orElseThrow(() -> new RuntimeException("Story not found"));

        boolean admin = requesterRole != null && "ADMIN".equalsIgnoreCase(requesterRole);
        boolean owner = requesterUserId != null && story.getUserId().equals(requesterUserId);

        if (!admin && !owner) {
            throw new SecurityException("You can delete only your own story.");
        }

        storyViewRepository.deleteByStoryId(storyId);
        deleteMediaFile(story.getMediaUrl());
        storyRepository.delete(story);
    }

    public void reportStory(Long storyId, String reason) {
        Story story = storyRepository.findById(storyId)
                .orElseThrow(() -> new RuntimeException("Story not found"));

        story.setReported(true);
        story.setReportReason(reason != null ? reason : "Reported from story viewer");

        storyRepository.save(story);
    }

    public Story incrementViewCount(Long storyId, Long viewerUserId, String viewerUsername) {
        Story story = storyRepository.findById(storyId)
                .orElseThrow(() -> new RuntimeException("Story not found"));

        if (!story.getUserId().equals(viewerUserId)) {
            boolean alreadyViewed = storyViewRepository
                    .findByStoryIdAndViewerUserId(storyId, viewerUserId)
                    .isPresent();

            if (!alreadyViewed) {
                StoryView view = new StoryView();
                view.setStoryId(storyId);
                view.setViewerUserId(viewerUserId);
                view.setViewerUsername(viewerUsername != null ? viewerUsername : "user");

                storyViewRepository.save(view);

                story.setViewCount(story.getViewCount() + 1);
                storyRepository.save(story);
            }
        }

        return story;
    }

    public List<StoryView> getViewers(Long storyId) {
        return storyViewRepository.findByStoryIdOrderByViewedAtDesc(storyId);
    }

    @Scheduled(fixedRateString = "${media.purge.interval-ms:300000}")
    public void purgeExpiredStories() {
        List<Story> expired = storyRepository.findByExpiresAtBefore(LocalDateTime.now());

        if (!expired.isEmpty()) {
            expired.forEach(story -> deleteMediaFile(story.getMediaUrl()));
            storyRepository.deleteAll(expired);
        }
    }
}