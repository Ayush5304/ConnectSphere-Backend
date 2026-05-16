/**
 * MediaResource.java — Media REST Controller
 *
 * Handles file uploads and story operations.
 * Note: No @RequestMapping at class level — each method has its own path.
 */

package com.connectsphere.media.controller;

import com.connectsphere.media.entity.Story;
import com.connectsphere.media.service.MediaService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class MediaResource {

    private final MediaService mediaService;

    /**
     * POST /media/upload — Upload a file (image or video)
     *
     * @RequestParam MultipartFile file — receives the uploaded file
     * Returns the URL of the uploaded file as a string
     *
     * Error handling:
     *   IllegalArgumentException → 400 Bad Request (invalid file type/size)
     *   IOException → 500 Internal Server Error (disk write failed)
     */
    @PostMapping("/media/upload")
    public ResponseEntity<?> upload(@RequestParam MultipartFile file) {
        try {
            return ResponseEntity.ok(mediaService.uploadFile(file));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body("Upload failed.");
        }
    }

    /**
     * GET /media/files/{filename} — Serve a stored file
     *
     * This endpoint serves the actual image/video files to the browser.
     * It's in the public paths list so no authentication needed.
     *
     * Steps:
     *   1. Resolve the filename to a path in the uploads directory
     *   2. Create a Resource from the path
     *   3. Detect the content type (image/jpeg, video/mp4 etc.)
     *   4. Return the file with correct Content-Type header
     *
     * Files.probeContentType() — detects MIME type from file extension
     * MediaType.APPLICATION_OCTET_STREAM — fallback if type can't be detected
     * (browser will download instead of display)
     */
    @GetMapping("/media/files/{filename}")
    public ResponseEntity<Resource> serveFile(@PathVariable String filename) throws IOException {
        Path path = Paths.get("uploads/").resolve(filename).normalize();
        Resource resource = new UrlResource(path.toUri());
        if (!resource.exists()) return ResponseEntity.notFound().build();

        /* Detect content type for correct browser rendering */
        String contentType = Files.probeContentType(path);
        MediaType mediaType = contentType != null
                ? MediaType.parseMediaType(contentType)
                : MediaType.APPLICATION_OCTET_STREAM;

        return ResponseEntity.ok().contentType(mediaType).body(resource);
    }

    /**
     * POST /stories — Create a new story
     *
     * @RequestParam — receives userId, username, and file as form data
     * (not JSON body because we're uploading a file)
     */
    @PostMapping("/stories")
    public ResponseEntity<?> createStory(@RequestParam Long userId,
                                          @RequestParam String username,
                                          @RequestParam MultipartFile file) {
        try {
            return ResponseEntity.ok(mediaService.createStory(userId, username, file));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body("Story upload failed.");
        }
    }

    /**
     * POST /stories/feed — Get active stories for a list of users
     * @RequestBody List<Long> userIds — list of user IDs to get stories from
     */
    @PostMapping("/stories/feed")
    public ResponseEntity<List<Story>> getStories(@RequestBody List<Long> userIds) {
        return ResponseEntity.ok(mediaService.getActiveStoriesForUsers(userIds));
    }

    /** GET /stories/user/{userId} — Get active stories by a specific user */
    @GetMapping("/stories/user/{userId}")
    public ResponseEntity<List<Story>> getByUser(@PathVariable Long userId) {
        return ResponseEntity.ok(mediaService.getStoriesByUser(userId));
    }

    /** DELETE /stories/{storyId} - Delete a story. Owner or ADMIN only. */
    @DeleteMapping("/stories/{storyId}")
    public ResponseEntity<?> deleteStory(@PathVariable Long storyId,
                                          @RequestParam(required = false) Long requesterUserId,
                                          @RequestParam(required = false) String requesterRole,
                                          @RequestHeader(value = "X-User-Role", required = false) String gatewayRole) {
        try {
            String role = gatewayRole != null ? gatewayRole : requesterRole;
            mediaService.deleteStory(storyId, requesterUserId, role);
            return ResponseEntity.ok(Map.of("message", "Story deleted successfully."));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("message", e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.status(404).body(Map.of("message", e.getMessage()));
        }
    }

    /** POST /stories/{storyId}/report - Report a story for moderation. */
    @PostMapping("/stories/{storyId}/report")
    public ResponseEntity<?> reportStory(@PathVariable Long storyId, @RequestBody Map<String, String> body) {
        mediaService.reportStory(storyId, body.get("reason"));
        return ResponseEntity.ok(Map.of("message", "Story reported."));
    }

    /**
     * PUT /stories/{storyId}/view — Record a story view
     *
     * @RequestParam(required = false) viewerUsername — optional parameter
     * Called when a user opens a story
     * Self-views are ignored in the service layer
     */
    @PutMapping("/stories/{storyId}/view")
    public ResponseEntity<Story> incrementView(@PathVariable Long storyId,
                                                @RequestParam Long viewerUserId,
                                                @RequestParam(required = false) String viewerUsername) {
        return ResponseEntity.ok(mediaService.incrementViewCount(storyId, viewerUserId, viewerUsername));
    }

    /** GET /stories/{storyId}/viewers — Get list of viewers (story owner only) */
    @GetMapping("/stories/{storyId}/viewers")
    public ResponseEntity<List<com.connectsphere.media.entity.StoryView>> getViewers(
            @PathVariable Long storyId) {
        return ResponseEntity.ok(mediaService.getViewers(storyId));
    }
}
