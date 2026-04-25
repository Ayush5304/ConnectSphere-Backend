/**
 * CommentResource.java — Comment REST Controller
 *
 * Handles all HTTP requests for comments.
 * All endpoints start with /comments
 */

package com.connectsphere.comment.controller;

import com.connectsphere.comment.entity.Comment;
import com.connectsphere.comment.service.CommentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/comments")
@RequiredArgsConstructor
public class CommentResource {

    private final CommentService commentService;

    /**
     * POST /comments — Add a comment or reply
     *
     * Receives: {postId, userId, username, content, parentCommentId (optional)}
     * parentCommentId is null for top-level comments, set for replies
     * body.get("parentCommentId") != null — check before parsing to Long
     */
    @PostMapping
    public ResponseEntity<Comment> add(@RequestBody Map<String, String> body) {
        Long parentId = body.get("parentCommentId") != null
            ? Long.parseLong(body.get("parentCommentId")) : null;
        return ResponseEntity.ok(commentService.addComment(
                Long.parseLong(body.get("postId")),
                Long.parseLong(body.get("userId")),
                body.get("username"),
                body.get("content"),
                parentId));
    }

    /**
     * GET /comments/post/{postId} — Get top-level comments for a post
     * Returns only non-deleted, non-reply comments sorted oldest first
     */
    @GetMapping("/post/{postId}")
    public ResponseEntity<List<Comment>> getByPost(@PathVariable Long postId) {
        return ResponseEntity.ok(commentService.getByPost(postId));
    }

    /**
     * GET /comments/{commentId}/replies — Get replies to a comment
     * Returns all non-deleted replies sorted oldest first
     */
    @GetMapping("/{commentId}/replies")
    public ResponseEntity<List<Comment>> getReplies(@PathVariable Long commentId) {
        return ResponseEntity.ok(commentService.getReplies(commentId));
    }

    /**
     * PUT /comments/{id} — Edit a comment
     * Receives: {"content": "updated comment text"}
     */
    @PutMapping("/{id}")
    public ResponseEntity<Comment> edit(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(commentService.editComment(id, body.get("content")));
    }

    /**
     * DELETE /comments/{id} — Soft delete a comment (user action)
     * Sets deleted=true, comment stays in database
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        commentService.softDeleteComment(id);
        return ResponseEntity.noContent().build();
    }

    /** GET /comments/admin/all — Get all comments (Admin only) */
    @GetMapping("/admin/all")
    public ResponseEntity<List<Comment>> getAllComments() {
        return ResponseEntity.ok(commentService.getAllComments());
    }

    /**
     * DELETE /comments/admin/{id} — Hard delete a comment (Admin only)
     * Permanently removes from database
     */
    @DeleteMapping("/admin/{id}")
    public ResponseEntity<Void> adminDelete(@PathVariable Long id) {
        commentService.deleteComment(id);
        return ResponseEntity.noContent().build();
    }
}
