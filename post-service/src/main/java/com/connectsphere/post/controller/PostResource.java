/**
 * PostResource.java — Post REST Controller
 *
 * Handles all HTTP requests related to posts.
 * All endpoints start with /posts (mapped at class level).
 *
 * @RestController — handles HTTP requests, returns JSON
 * @RequestMapping("/posts") — all endpoints prefixed with /posts
 * @RequiredArgsConstructor — Lombok generates constructor for final fields
 */

package com.connectsphere.post.controller;

import com.connectsphere.post.entity.Post;
import com.connectsphere.post.service.PostService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/posts")
@RequiredArgsConstructor
public class PostResource {

    private final PostService postService;

    /**
     * POST /posts — Create a new post
     *
     * Receives: {userId, username, content, mediaUrl, visibility}
     * visibility defaults to PUBLIC if not provided
     * Post.Visibility.valueOf() converts string "PUBLIC" to enum Visibility.PUBLIC
     */
    @PostMapping
    public ResponseEntity<Post> create(@RequestBody Map<String, String> body) {
        return ResponseEntity.ok(postService.createPost(
                Long.parseLong(body.get("userId")), body.get("username"),
                body.get("content"), body.get("mediaUrl"),
                body.get("visibility") != null
                    ? Post.Visibility.valueOf(body.get("visibility"))
                    : Post.Visibility.PUBLIC));
    }

    /**
     * GET /posts/{id} — Get a single post by ID
     * Used by other services (like-service, comment-service) to get post details
     */
    @GetMapping("/{id}")
    public ResponseEntity<Post> getById(@PathVariable Long id) {
        return ResponseEntity.ok(postService.getById(id));
    }

    /**
     * GET /posts/user/{userId} — Get all posts by a specific user
     * Used by Profile page to show user's posts
     * Only returns non-deleted posts, sorted newest first
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<Post>> getByUser(@PathVariable Long userId) {
        return ResponseEntity.ok(postService.getByUser(userId));
    }

    /**
     * GET /posts/feed — Get public feed (no auth required)
     * Returns all PUBLIC posts sorted by newest first
     * Used for guest users and as fallback when user has no followings
     */
    @GetMapping("/feed")
    public ResponseEntity<List<Post>> publicFeed() {
        return ResponseEntity.ok(postService.getPublicFeed());
    }

    /**
     * POST /posts/feed/users — Get personalized feed for specific users
     *
     * @RequestBody List<Long> userIds — list of user IDs to get posts from
     * Used for logged-in users: sends [followingId1, followingId2, ..., ownId]
     * Returns posts from all those users sorted newest first
     */
    @PostMapping("/feed/users")
    public ResponseEntity<List<Post>> feedForUsers(@RequestBody List<Long> userIds) {
        return ResponseEntity.ok(postService.getFeedForUsers(userIds));
    }

    /**
     * GET /posts/search?keyword=hello — Search posts by keyword
     * Only searches PUBLIC non-deleted posts
     * Case-insensitive search in post content
     */
    @GetMapping("/search")
    public ResponseEntity<List<Post>> search(@RequestParam String keyword) {
        return ResponseEntity.ok(postService.search(keyword));
    }

    /**
     * PUT /posts/{id}/visibility?visibility=PRIVATE — Change post visibility
     * Allows user to change who can see their post after creation
     */
    @PutMapping("/{id}/visibility")
    public ResponseEntity<Post> updateVisibility(@PathVariable Long id, @RequestParam String visibility) {
        return ResponseEntity.ok(postService.updateVisibility(id, Post.Visibility.valueOf(visibility)));
    }

    /**
     * PUT /posts/{id} — Edit post content/media/visibility
     * Only updates fields that are provided (null = keep existing)
     */
    @PutMapping("/{id}")
    public ResponseEntity<Post> edit(@PathVariable Long id, @RequestBody Map<String, String> body) {
        Post.Visibility vis = body.get("visibility") != null
            ? Post.Visibility.valueOf(body.get("visibility")) : null;
        return ResponseEntity.ok(postService.editPost(id, body.get("content"), body.get("mediaUrl"), vis));
    }

    /**
     * DELETE /posts/{id} — Soft delete a post (user action)
     * Sets deleted=true, post stays in database
     * Returns 204 No Content on success
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        postService.softDeletePost(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * DELETE /posts/{id}/hard — Hard delete a post (permanent)
     * Completely removes from database — cannot be recovered
     * Not exposed to regular users, only used internally
     */
    @DeleteMapping("/{id}/hard")
    public ResponseEntity<Void> hardDelete(@PathVariable Long id) {
        postService.deletePost(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * PUT /posts/{id}/likes/increment — Increment likes count
     * Called by like-service when someone reacts to a post
     * Internal endpoint — not called directly by frontend
     */
    @PutMapping("/{id}/likes/increment")
    public ResponseEntity<Void> incrementLikes(@PathVariable Long id) {
        postService.incrementLikes(id);
        return ResponseEntity.ok().build();
    }

    /**
     * PUT /posts/{id}/likes/decrement — Decrement likes count
     * Called by like-service when someone removes their reaction
     */
    @PutMapping("/{id}/likes/decrement")
    public ResponseEntity<Void> decrementLikes(@PathVariable Long id) {
        postService.decrementLikes(id);
        return ResponseEntity.ok().build();
    }

    /**
     * PUT /posts/{id}/comments/increment — Increment comments count
     * Called by comment-service when someone adds a comment
     */
    @PutMapping("/{id}/comments/increment")
    public ResponseEntity<Void> incrementComments(@PathVariable Long id) {
        postService.incrementComments(id);
        return ResponseEntity.ok().build();
    }

    /** GET /posts/user/{userId}/count — Get total post count for a user */
    @GetMapping("/user/{userId}/count")
    public ResponseEntity<Long> count(@PathVariable Long userId) {
        return ResponseEntity.ok(postService.countByUser(userId));
    }

    /**
     * GET /posts/admin/all — Get ALL posts including deleted (Admin only)
     * Used by admin dashboard to see all posts
     */
    @GetMapping("/admin/all")
    public ResponseEntity<List<Post>> getAllPosts() {
        return ResponseEntity.ok(postService.getAllPosts());
    }

    /**
     * DELETE /posts/admin/{id} — Hard delete any post (Admin only)
     * Admin can permanently remove any post
     */
    @DeleteMapping("/admin/{id}")
    public ResponseEntity<Void> adminDelete(@PathVariable Long id) {
        postService.deletePost(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * POST /posts/{id}/report — Report a post
     * Receives: {"reason": "This post is spam"}
     * Sets reported=true on the post for admin review
     */
    @PostMapping("/{id}/report")
    public ResponseEntity<Void> report(@PathVariable Long id, @RequestBody Map<String, String> body) {
        postService.reportPost(id, body.get("reason"));
        return ResponseEntity.ok().build();
    }

    /** GET /posts/admin/reported — Get all reported posts (Admin only) */
    @GetMapping("/admin/reported")
    public ResponseEntity<List<Post>> getReported() {
        return ResponseEntity.ok(postService.getReportedPosts());
    }

    /**
     * PUT /posts/admin/{id}/clear-report — Clear report flag (Admin only)
     * Admin reviewed the post and decided it's not a violation
     */
    @PutMapping("/admin/{id}/clear-report")
    public ResponseEntity<Void> clearReport(@PathVariable Long id) {
        postService.clearReport(id);
        return ResponseEntity.noContent().build();
    }

    /** GET /posts/admin/analytics — Get post statistics (Admin only) */
    @GetMapping("/admin/analytics")
    public ResponseEntity<Map<String, Object>> analytics() {
        return ResponseEntity.ok(postService.getAnalytics());
    }

    /** PUT /posts/{id}/boost — Boost a post (called by payment-service) */
    @PutMapping("/{id}/boost")
    public ResponseEntity<Void> boostPost(@PathVariable Long id) {
        postService.boostPost(id);
        return ResponseEntity.ok().build();
    }
}
