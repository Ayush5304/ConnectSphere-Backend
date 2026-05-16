package com.connectsphere.follow.repository;

import com.connectsphere.follow.entity.FollowRequest;
import com.connectsphere.follow.entity.FollowRequest.Status;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FollowRequestRepository extends JpaRepository<FollowRequest, Long> {
    Optional<FollowRequest> findByFollowerIdAndFollowingId(Long followerId, Long followingId);
    boolean existsByFollowerIdAndFollowingIdAndStatus(Long followerId, Long followingId, Status status);
    List<FollowRequest> findByFollowingIdAndStatusOrderByCreatedAtDesc(Long followingId, Status status);
    List<FollowRequest> findByFollowerIdAndStatusOrderByCreatedAtDesc(Long followerId, Status status);
}
