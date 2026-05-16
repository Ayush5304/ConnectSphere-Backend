package com.connectsphere.media.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "story_views",
       uniqueConstraints = @UniqueConstraint(columnNames = {"storyId", "viewerUserId"}))
@Data
public class StoryView {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long storyId;
    private Long viewerUserId;
    private String viewerUsername;
    private LocalDateTime viewedAt = LocalDateTime.now();
}
