/**
 * SearchService.java — Search and Hashtag Business Logic
 *
 * Handles:
 *   1. Hashtag indexing — extracts #tags from post content and stores them
 *   2. Trending hashtags — returns most-used hashtags
 *   3. Cross-service search — searches posts, users, and hashtags together
 *   4. Posts by hashtag — finds all posts with a specific hashtag
 *
 * Two database tables:
 *   hashtags      — stores each unique hashtag with its post count
 *   post_hashtags — maps posts to their hashtags (many-to-many)
 */

package com.connectsphere.search.service;

import com.connectsphere.search.entity.Hashtag;
import com.connectsphere.search.entity.PostHashtag;
import com.connectsphere.search.repository.HashtagRepository;
import com.connectsphere.search.repository.PostHashtagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SearchService {

    private final HashtagRepository hashtagRepository;
    private final PostHashtagRepository postHashtagRepository;
    private final RestTemplate restTemplate;

    @Value("${post.service.url}")
    private String postServiceUrl;

    @Value("${auth.service.url}")
    private String authServiceUrl;

    /**
     * HASHTAG_PATTERN — Regex to find hashtags in post content
     * #(\\w+) matches # followed by word characters (letters, digits, underscore)
     * e.g. "Hello #world how are #you" → finds "world" and "you"
     */
    private static final Pattern HASHTAG_PATTERN = Pattern.compile("#(\\w+)");

    /**
     * indexHashtags() — Extracts and stores hashtags from post content
     *
     * Called by post-service when a post is created or edited.
     *
     * For each #tag found:
     *   1. Find existing hashtag or create new one
     *   2. Increment postCount (how many posts use this tag)
     *   3. Create PostHashtag record linking this post to this tag
     *      (only if not already linked — prevents duplicates)
     *
     * .orElseGet() — if hashtag not found, create a new one
     * This is an "upsert" pattern (update or insert)
     *
     * @param content — post text to extract hashtags from
     * @param postId  — ID of the post (for PostHashtag mapping)
     */
    public void indexHashtags(String content, Long postId) {
        if (content == null) return;
        Matcher matcher = HASHTAG_PATTERN.matcher(content);
        while (matcher.find()) {
            String tag = matcher.group(1).toLowerCase(); /* Normalize to lowercase */

            /* Find existing hashtag or create new one */
            Hashtag hashtag = hashtagRepository.findByTag(tag).orElseGet(() -> {
                Hashtag h = new Hashtag();
                h.setTag(tag);
                return h;
            });
            hashtag.setPostCount(hashtag.getPostCount() + 1);
            hashtagRepository.save(hashtag);

            /* Link post to hashtag (avoid duplicates) */
            if (postId != null && !postHashtagRepository.existsByPostIdAndTag(postId, tag)) {
                PostHashtag ph = new PostHashtag();
                ph.setPostId(postId);
                ph.setTag(tag);
                postHashtagRepository.save(ph);
            }
        }
    }

    /**
     * indexHashtags() — Overloaded version without postId
     * Backward compatible — called when postId is not available
     */
    public void indexHashtags(String content) {
        indexHashtags(content, null);
    }

    /**
     * getTrending() — Returns most-used hashtags
     *
     * PageRequest.of(0, limit) — pagination: page 0, up to 'limit' results
     * findAllByOrderByPostCountDesc — sorted by postCount descending
     * (most used hashtags first)
     *
     * @param limit — maximum number of trending hashtags to return
     */
    public List<Hashtag> getTrending(int limit) {
        return hashtagRepository.findAllByOrderByPostCountDesc(PageRequest.of(0, limit));
    }

    /**
     * search() — Cross-service search for posts, users, and hashtags
     *
     * Searches three sources in parallel:
     *   1. Posts — calls post-service: GET /posts/search?keyword=query
     *   2. Hashtags — searches local hashtags table
     *   3. Users — calls auth-service: GET /auth/search?query=query
     *
     * Each search is wrapped in try-catch:
     *   If one service is down, others still return results
     *
     * Returns a Map with three keys: "posts", "hashtags", "users"
     *
     * ParameterizedTypeReference — tells Spring the generic type of the response
     * Needed because Java erases generic types at runtime
     */
    public Map<String, Object> search(String query) {
        /* Search posts via post-service */
        List<?> posts = List.of();
        try {
            posts = restTemplate.exchange(
                postServiceUrl + "/posts/search?keyword=" + query,
                HttpMethod.GET, null,
                new ParameterizedTypeReference<List<?>>() {}).getBody();
        } catch (Exception ignored) {}

        /* Search hashtags locally */
        List<Hashtag> hashtags = hashtagRepository.findByTagContainingIgnoreCase(query);

        /* Search users via auth-service */
        List<Map<String, Object>> users = new java.util.ArrayList<>();
        try {
            List<Map<String, Object>> allUsers = restTemplate.exchange(
                authServiceUrl + "/auth/search?query=" + query,
                HttpMethod.GET, null,
                new ParameterizedTypeReference<List<Map<String, Object>>>() {}).getBody();
            if (allUsers != null) users = allUsers;
        } catch (Exception ignored) {}

        return Map.of(
            "posts",    posts != null ? posts : List.of(),
            "hashtags", hashtags,
            "users",    users
        );
    }

    /**
     * getPostIdsByHashtag() — Gets all post IDs that use a specific hashtag
     *
     * Queries PostHashtag table for all records with this tag
     * .map(PostHashtag::getPostId) — extracts just the postId from each record
     */
    public List<Long> getPostIdsByHashtag(String tag) {
        return postHashtagRepository.findByTag(tag.toLowerCase())
            .stream().map(PostHashtag::getPostId).collect(Collectors.toList());
    }

    /**
     * getPostsByHashtag() — Gets full post objects for a hashtag
     *
     * Steps:
     *   1. Get list of post IDs for this hashtag
     *   2. Call post-service with those IDs to get full post objects
     *
     * Uses POST /posts/feed/users endpoint (reused for fetching by ID list)
     * Returns empty list if no posts or post-service is unavailable
     */
    public List<?> getPostsByHashtag(String tag) {
        List<Long> postIds = getPostIdsByHashtag(tag);
        if (postIds.isEmpty()) return List.of();
        try {
            return restTemplate.exchange(
                postServiceUrl + "/posts/feed/users",
                HttpMethod.POST,
                new org.springframework.http.HttpEntity<>(postIds),
                new ParameterizedTypeReference<List<?>>() {}).getBody();
        } catch (Exception e) {
            return List.of();
        }
    }
}
