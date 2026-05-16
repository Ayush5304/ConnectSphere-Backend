package com.connectsphere.search.repository;

import com.connectsphere.search.entity.PostHashtag;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PostHashtagRepository extends JpaRepository<PostHashtag, Long> {
    List<PostHashtag> findByTag(String tag);
    List<PostHashtag> findByPostId(Long postId);
    boolean existsByPostIdAndTag(Long postId, String tag);
}
