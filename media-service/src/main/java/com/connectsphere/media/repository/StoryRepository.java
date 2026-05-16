package com.connectsphere.media.repository;

import com.connectsphere.media.entity.Story;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;

public interface StoryRepository extends JpaRepository<Story, Long> {
    List<Story> findByUserIdInAndExpiresAtAfter(List<Long> userIds, LocalDateTime now);
    List<Story> findByExpiresAtBefore(LocalDateTime now);
    List<Story> findByUserId(Long userId);
    List<Story> findByUserIdAndExpiredFalse(Long userId);
}
