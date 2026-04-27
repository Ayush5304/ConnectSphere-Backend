package com.connectsphere.media.repository;

import com.connectsphere.media.entity.StoryView;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface StoryViewRepository extends JpaRepository<StoryView, Long> {
    List<StoryView> findByStoryIdOrderByViewedAtDesc(Long storyId);
    Optional<StoryView> findByStoryIdAndViewerUserId(Long storyId, Long viewerUserId);
    long countByStoryId(Long storyId);
}
