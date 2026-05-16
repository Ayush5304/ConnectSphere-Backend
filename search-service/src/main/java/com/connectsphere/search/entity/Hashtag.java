package com.connectsphere.search.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "hashtags")
@Data
public class Hashtag {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long hashtagId;

    @Column(unique = true, nullable = false)
    private String tag;

    private int postCount = 0;
}
