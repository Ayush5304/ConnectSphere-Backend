package com.connectsphere.follow.repository;

import com.connectsphere.follow.entity.Follow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface FollowRepository extends JpaRepository<Follow, Long> {
    List<Follow> findByFollowerId(Long followerId);
    List<Follow> findByFollowingId(Long followingId);
    Optional<Follow> findByFollowerIdAndFollowingId(Long followerId, Long followingId);
    boolean existsByFollowerIdAndFollowingId(Long followerId, Long followingId);
    long countByFollowerId(Long followerId);
    long countByFollowingId(Long followingId);

    @Query("select distinct f.followingId from Follow f where f.followerId = :followerId")
    List<Long> findDistinctFollowingIds(@Param("followerId") Long followerId);

    @Query("select distinct f.followerId from Follow f where f.followingId = :followingId")
    List<Long> findDistinctFollowerIds(@Param("followingId") Long followingId);

    @Query("select count(distinct f.followingId) from Follow f where f.followerId = :followerId")
    long countDistinctFollowing(@Param("followerId") Long followerId);

    @Query("select count(distinct f.followerId) from Follow f where f.followingId = :followingId")
    long countDistinctFollowers(@Param("followingId") Long followingId);

    @Modifying
    @Transactional
    @Query("delete from Follow f where f.followerId = :followerId and f.followingId = :followingId")
    void deleteAllByPair(@Param("followerId") Long followerId, @Param("followingId") Long followingId);
}
