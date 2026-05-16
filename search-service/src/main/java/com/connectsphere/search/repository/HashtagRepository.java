package com.connectsphere.search.repository;

import com.connectsphere.search.entity.Hashtag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.Optional;

public interface HashtagRepository extends JpaRepository<Hashtag, Long> {
    Optional<Hashtag> findByTag(String tag);
    List<Hashtag> findAllByOrderByPostCountDesc(Pageable pageable);
    List<Hashtag> findByTagContainingIgnoreCase(String query);
}
