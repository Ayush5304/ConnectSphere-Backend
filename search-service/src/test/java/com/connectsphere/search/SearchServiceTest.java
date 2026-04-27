package com.connectsphere.search;

import com.connectsphere.search.entity.Hashtag;
import com.connectsphere.search.entity.PostHashtag;
import com.connectsphere.search.repository.HashtagRepository;
import com.connectsphere.search.repository.PostHashtagRepository;
import com.connectsphere.search.service.SearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * SearchServiceTest — Unit tests for SearchService.
 */
@ExtendWith(MockitoExtension.class)
class SearchServiceTest {

    @Mock HashtagRepository hashtagRepository;
    @Mock PostHashtagRepository postHashtagRepository;
    @Mock RestTemplate restTemplate;
    @InjectMocks SearchService searchService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(searchService, "postServiceUrl", "http://localhost:8082");
        ReflectionTestUtils.setField(searchService, "authServiceUrl", "http://localhost:8081");
    }

    /* ── indexHashtags() tests ─────────────────────────────────────── */

    @Test
    void indexHashtags_newHashtag_createsAndSaves() {
        when(hashtagRepository.findByTag("java")).thenReturn(Optional.empty());
        when(hashtagRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(postHashtagRepository.existsByPostIdAndTag(1L, "java")).thenReturn(false);
        when(postHashtagRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        searchService.indexHashtags("Hello #java world", 1L);

        verify(hashtagRepository).save(any(Hashtag.class));
        verify(postHashtagRepository).save(any(PostHashtag.class));
    }

    @Test
    void indexHashtags_existingHashtag_incrementsCount() {
        Hashtag existing = new Hashtag();
        existing.setTag("java");
        existing.setPostCount(5);
        when(hashtagRepository.findByTag("java")).thenReturn(Optional.of(existing));
        when(hashtagRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(postHashtagRepository.existsByPostIdAndTag(1L, "java")).thenReturn(false);
        when(postHashtagRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        searchService.indexHashtags("#java is great", 1L);

        assertEquals(6, existing.getPostCount());
    }

    @Test
    void indexHashtags_nullContent_doesNothing() {
        searchService.indexHashtags(null, 1L);
        verify(hashtagRepository, never()).save(any());
    }

    @Test
    void indexHashtags_noHashtags_doesNothing() {
        searchService.indexHashtags("Hello world no tags here", 1L);
        verify(hashtagRepository, never()).save(any());
    }

    @Test
    void indexHashtags_duplicatePostHashtag_skipsCreation() {
        Hashtag existing = new Hashtag();
        existing.setTag("java");
        existing.setPostCount(3);
        when(hashtagRepository.findByTag("java")).thenReturn(Optional.of(existing));
        when(hashtagRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(postHashtagRepository.existsByPostIdAndTag(1L, "java")).thenReturn(true);

        searchService.indexHashtags("#java again", 1L);

        verify(postHashtagRepository, never()).save(any());
    }

    /* ── getTrending() tests ───────────────────────────────────────── */

    @Test
    void getTrending_returnsHashtagsSortedByCount() {
        Hashtag h1 = new Hashtag(); h1.setTag("java");   h1.setPostCount(50);
        Hashtag h2 = new Hashtag(); h2.setTag("spring"); h2.setPostCount(30);
        when(hashtagRepository.findAllByOrderByPostCountDesc(any()))
            .thenReturn(List.of(h1, h2));

        List<Hashtag> result = searchService.getTrending(10);

        assertEquals(2, result.size());
        assertEquals("java", result.get(0).getTag());
        assertEquals(50, result.get(0).getPostCount());
    }

    @Test
    void getTrending_noHashtags_returnsEmptyList() {
        when(hashtagRepository.findAllByOrderByPostCountDesc(any()))
            .thenReturn(List.of());

        List<Hashtag> result = searchService.getTrending(10);

        assertTrue(result.isEmpty());
    }

    /* ── getPostIdsByHashtag() tests ───────────────────────────────── */

    @Test
    void getPostIdsByHashtag_returnsPostIds() {
        PostHashtag ph1 = new PostHashtag(); ph1.setPostId(1L); ph1.setTag("java");
        PostHashtag ph2 = new PostHashtag(); ph2.setPostId(2L); ph2.setTag("java");
        when(postHashtagRepository.findByTag("java")).thenReturn(List.of(ph1, ph2));

        List<Long> result = searchService.getPostIdsByHashtag("java");

        assertEquals(2, result.size());
        assertTrue(result.contains(1L));
        assertTrue(result.contains(2L));
    }

    @Test
    void getPostIdsByHashtag_normalizesToLowercase() {
        when(postHashtagRepository.findByTag("java")).thenReturn(List.of());

        searchService.getPostIdsByHashtag("JAVA");

        verify(postHashtagRepository).findByTag("java");
    }
}
