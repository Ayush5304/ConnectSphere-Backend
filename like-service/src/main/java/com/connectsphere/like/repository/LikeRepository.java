package com.connectsphere.like.repository;

import com.connectsphere.like.entity.Like;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface LikeRepository extends JpaRepository<Like, Long> {
    Optional<Like> findByUserIdAndTargetIdAndTargetType(Long userId, Long targetId, Like.TargetType targetType);
    List<Like> findByTargetIdAndTargetType(Long targetId, Like.TargetType targetType);
    boolean existsByUserIdAndTargetIdAndTargetType(Long userId, Long targetId, Like.TargetType targetType);
}
