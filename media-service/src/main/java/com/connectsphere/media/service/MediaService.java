/**
 * MediaService.java — Media and Story Business Logic
 *
 * Handles:
 *   1. File uploads (images and videos) for posts and profile pictures
 *   2. Story creation, retrieval, and deletion
 *   3. Story view tracking
 *   4. Automatic cleanup of expired stories (scheduled job)
 *
 * Security measures:
 *   - MIME type validation (only jpeg/png/webp/mp4 allowed)
 *   - File extension validation (extra guard against spoofed MIME types)
 *   - File size limits (10MB images, 100MB videos)
 *   - UUID filenames (prevents path traversal attacks)
 *   - Path traversal check (destination must be inside upload directory)
 */

package com.connectsphere.media.service;

import com.connectsphere.media.entity.Story;
import com.connectsphere.media.repository.StoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
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
    private final com.connectsphere.media.repository.StoryViewRepository storyViewRepository;

    /** uploadDir — Directory where uploaded files are stored */
    @Value("${media.upload.dir:uploads/}")
    private String uploadDir;

    /** maxImageSize — Maximum allowed image file size (default 10MB) */
    @Value("${media.max-image-size:10485760}")
    private long maxImageSize;

    /** maxVideoSize — Maximum allowed video file size (default 100MB) */
    @Value("${media.max-video-size:104857600}")
    private long maxVideoSize;

    /**
     * ALLOWED_TYPES — Maps MIME type to category (image or video)
     * Used to validate uploaded file type
     * Map.of() creates an immutable map
     */
    private static final Map<String, String> ALLOWED_TYPES = Map.of(
        "image/jpeg", "image",
        "image/png",  "image",
        "image/webp", "image",
        "video/mp4",  "video"
    );

    /**
     * ALLOWED_EXTENSIONS — Set of allowed file extensions
     * Extra security layer: validates file extension matches MIME type
     * Prevents attackers from uploading .exe files with image MIME type
     */
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
        ".jpg", ".jpeg", ".png", ".webp", ".mp4"
    );

    /**
     * validate() — Validates uploaded file before saving
     *
     * Three checks:
     *   1. MIME type must be in ALLOWED_TYPES
     *   2. File extension must be in ALLOWED_EXTENSIONS
     *   3. File size must be within limit (different for image vs video)
     *
     * Throws IllegalArgumentException with descriptive message if invalid
     * Controller catches this and returns 400 Bad Request
     */
    private void validate(MultipartFile file) {
        /* Check MIME type */
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.containsKey(contentType)) {
            throw new IllegalArgumentException(
                "Unsupported file type: " + contentType +
                ". Allowed: JPEG, PNG, WebP, MP4.");
        }

        /* Check file extension */
        String originalName = file.getOriginalFilename() != null
                ? file.getOriginalFilename().toLowerCase() : "";
        boolean extOk = ALLOWED_EXTENSIONS.stream().anyMatch(originalName::endsWith);
        if (!extOk) {
            throw new IllegalArgumentException("File extension not allowed.");
        }

        /* Check file size (different limits for image vs video) */
        String category = ALLOWED_TYPES.get(contentType);
        long limit = "video".equals(category) ? maxVideoSize : maxImageSize;
        if (file.getSize() > limit) {
            throw new IllegalArgumentException(
                "File too large. Max size for " + category + ": " + (limit / 1_048_576) + " MB.");
        }
    }

    /**
     * safeFilename() — Generates a safe filename to prevent path traversal attacks
     *
     * Problem: if user uploads file named "../../etc/passwd.jpg"
     * and we use that name, we might overwrite system files
     *
     * Solution:
     *   1. Extract only the file extension from original name
     *   2. Generate a random UUID as the filename
     *   3. Combine: UUID + extension (e.g. "a1b2c3d4-e5f6-....jpg")
     *
     * UUID.randomUUID() — generates a universally unique identifier
     * Very low probability of collision — safe to use as filename
     */
    private String safeFilename(MultipartFile file) {
        String original = file.getOriginalFilename() != null
                ? file.getOriginalFilename() : "file";
        int dot = original.lastIndexOf('.');
        String ext = (dot >= 0) ? original.substring(dot).toLowerCase() : "";
        return UUID.randomUUID() + ext;
    }

    /**
     * uploadFile() — Saves a file to disk and returns its URL
     *
     * Steps:
     *   1. Validate the file (type, extension, size)
     *   2. Create upload directory if it doesn't exist
     *   3. Generate safe UUID filename
     *   4. Resolve destination path
     *   5. Security check: destination must be inside uploadDir
     *   6. Write file bytes to disk
     *   7. Return the public URL for accessing the file
     *
     * Path traversal protection:
     *   dest.startsWith(uploadPath) — ensures the resolved path
     *   is still inside the upload directory
     *   Prevents: uploadDir + "../../etc/passwd" attacks
     *
     * Returns URL: "http://localhost:8080/media/files/{filename}"
     * This URL goes through the API Gateway which serves the file
     */
    public String uploadFile(MultipartFile file) throws IOException {
        validate(file);
        Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
        Files.createDirectories(uploadPath); /* Create directory if not exists */
        String filename = safeFilename(file);
        Path dest = uploadPath.resolve(filename).normalize();

        /* Security: ensure destination is inside upload directory */
        if (!dest.startsWith(uploadPath)) {
            throw new IllegalArgumentException("Invalid file path.");
        }

        Files.write(dest, file.getBytes()); /* Write file to disk */
        return "http://localhost:8080/media/files/" + filename;
    }

    /**
     * createStory() — Creates a new story with media
     *
     * Steps:
     *   1. Validate and upload the media file
     *   2. Create Story entity with userId, username, mediaUrl
     *   3. @PrePersist automatically sets createdAt and expiresAt (24h)
     *   4. Save and return the story
     */
    public Story createStory(Long userId, String username, MultipartFile file) throws IOException {
        validate(file);
        String url = uploadFile(file);
        Story story = new Story();
        story.setUserId(userId);
        story.setUsername(username);
        story.setMediaUrl(url);
        story.setMediaType(file.getContentType());
        return storyRepository.save(story);
    }

    /**
     * getActiveStoriesForUsers() — Gets non-expired stories for a list of users
     *
     * findByUserIdInAndExpiresAtAfter(userIds, now):
     *   - userIds IN (1, 2, 3, ...) — stories from these users
     *   - expiresAt > now — only stories that haven't expired yet
     *
     * Used by StoriesBar to show stories from followed users + self
     */
    public List<Story> getActiveStoriesForUsers(List<Long> userIds) {
        return storyRepository.findByUserIdInAndExpiresAtAfter(userIds, LocalDateTime.now());
    }

    /** getStoriesByUser() — Gets active stories for a single user */
    public List<Story> getStoriesByUser(Long userId) {
        return storyRepository.findByUserIdInAndExpiresAtAfter(
            List.of(userId), LocalDateTime.now());
    }

    /** deleteStory() — Hard deletes a story (owner action) */
    public void deleteStory(Long storyId) {
        storyRepository.deleteById(storyId);
    }

    /**
     * incrementViewCount() — Records a story view and increments counter
     *
     * Self-view guard: story owner viewing their own story doesn't count
     * Unique view: each viewer is only counted once
     *   (StoryView has unique constraint on storyId + viewerUserId)
     *
     * Steps:
     *   1. Find the story
     *   2. Check viewer is not the story owner
     *   3. Check viewer hasn't already viewed this story
     *   4. Create StoryView record
     *   5. Increment viewCount
     *   6. Save and return updated story
     */
    public Story incrementViewCount(Long storyId, Long viewerUserId, String viewerUsername) {
        Story story = storyRepository.findById(storyId)
            .orElseThrow(() -> new RuntimeException("Story not found"));

        /* Only count views from other users (not the story owner) */
        if (!story.getUserId().equals(viewerUserId)) {
            /* Check if this user already viewed this story */
            boolean alreadyViewed = storyViewRepository
                .findByStoryIdAndViewerUserId(storyId, viewerUserId).isPresent();

            if (!alreadyViewed) {
                /* Record the view */
                com.connectsphere.media.entity.StoryView view =
                    new com.connectsphere.media.entity.StoryView();
                view.setStoryId(storyId);
                view.setViewerUserId(viewerUserId);
                view.setViewerUsername(viewerUsername != null ? viewerUsername : "user");
                storyViewRepository.save(view);

                /* Increment view count */
                story.setViewCount(story.getViewCount() + 1);
                storyRepository.save(story);
            }
        }
        return story;
    }

    /**
     * getViewers() — Gets list of users who viewed a story
     * Only the story owner should call this
     * Returns viewers sorted newest first
     */
    public List<com.connectsphere.media.entity.StoryView> getViewers(Long storyId) {
        return storyViewRepository.findByStoryIdOrderByViewedAtDesc(storyId);
    }

    /**
     * purgeExpiredStories() — Scheduled job to delete expired stories
     *
     * @Scheduled(fixedRateString = "300000") — runs every 5 minutes (300,000ms)
     * fixedRateString reads from application.yml: media.purge.interval-ms
     *
     * Steps:
     *   1. Find all stories where expiresAt < now (expired)
     *   2. For each expired story → delete the media file from disk
     *   3. Delete all expired story records from database
     *
     * Path traversal protection:
     *   file.startsWith(uploadPath) — ensures we only delete files
     *   inside the upload directory (not system files)
     *
     * Files.deleteIfExists() — doesn't throw error if file already deleted
     */
    @Scheduled(fixedRateString = "${media.purge.interval-ms:300000}")
    public void purgeExpiredStories() {
        List<Story> expired = storyRepository.findByExpiresAtBefore(LocalDateTime.now());
        if (!expired.isEmpty()) {
            Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
            expired.forEach(s -> {
                try {
                    /* Extract filename from URL */
                    String filename = s.getMediaUrl()
                        .substring(s.getMediaUrl().lastIndexOf('/') + 1);
                    Path file = uploadPath.resolve(filename).normalize();
                    /* Security check before deleting */
                    if (file.startsWith(uploadPath)) {
                        Files.deleteIfExists(file);
                    }
                } catch (Exception ignored) {}
            });
            /* Delete all expired story records from database */
            storyRepository.deleteAll(expired);
        }
    }
}
