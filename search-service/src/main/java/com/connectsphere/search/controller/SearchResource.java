/**
 * SearchResource.java — Search REST Controller
 *
 * Handles search and hashtag endpoints.
 * Note: No @RequestMapping at class level — paths defined per method.
 */

package com.connectsphere.search.controller;

import com.connectsphere.search.entity.Hashtag;
import com.connectsphere.search.service.SearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class SearchResource {

    private final SearchService searchService;

    /**
     * POST /hashtags/index — Index hashtags from a post (internal endpoint)
     *
     * Called by post-service when a post is created or edited.
     * Not called directly by the frontend.
     *
     * Receives: {"content": "Hello #world", "postId": 5}
     * body.containsKey("postId") — check before parsing (postId is optional)
     */
    @PostMapping("/hashtags/index")
    public ResponseEntity<Void> index(@RequestBody Map<String, Object> body) {
        String content = (String) body.get("content");
        Long postId = body.containsKey("postId")
            ? Long.parseLong(body.get("postId").toString()) : null;
        searchService.indexHashtags(content, postId);
        return ResponseEntity.ok().build();
    }

    /**
     * GET /hashtags/trending?limit=10 — Get trending hashtags
     *
     * @RequestParam(defaultValue = "10") — returns 10 by default
     * Used by SearchBar to show trending hashtags in dropdown
     * Used by admin dashboard for analytics
     */
    @GetMapping("/hashtags/trending")
    public ResponseEntity<List<Hashtag>> trending(
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(searchService.getTrending(limit));
    }

    /**
     * GET /search?query=hello — Search posts, users, and hashtags
     *
     * Returns: {"posts": [...], "users": [...], "hashtags": [...]}
     * Used by SearchBar component for real-time search results
     */
    @GetMapping("/search")
    public ResponseEntity<Map<String, Object>> search(@RequestParam String query) {
        return ResponseEntity.ok(searchService.search(query));
    }

    /**
     * GET /hashtags/{tag}/posts — Get all posts with a specific hashtag
     * Used when user clicks on a hashtag to see related posts
     */
    @GetMapping("/hashtags/{tag}/posts")
    public ResponseEntity<List<?>> getPostsByHashtag(@PathVariable String tag) {
        return ResponseEntity.ok(searchService.getPostsByHashtag(tag));
    }

    /** GET /hashtags/post/{postId} — Get all hashtag IDs for a post */
    @GetMapping("/hashtags/post/{postId}")
    public ResponseEntity<List<Long>> getHashtagsByPost(@PathVariable Long postId) {
        return ResponseEntity.ok(searchService.getPostIdsByHashtag(postId.toString()));
    }
}
