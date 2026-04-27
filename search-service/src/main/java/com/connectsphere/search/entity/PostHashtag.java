package com.connectsphere.search.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "post_hashtags")
@Data
public class PostHashtag {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long postId;
    private String tag;
    private LocalDateTime createdAt = LocalDateTime.now();
}
