package com.readb.domain.analysis;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "analyses")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Analysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "meeting_id", nullable = false, unique = true)
    private Long meetingId;

    @Column(name = "gap_score")
    private Double gapScore;

    @Column(name = "surface_score")
    private Double surfaceScore;

    @Column(name = "inferred_score")
    private Double inferredScore;

    @Type(JsonBinaryType.class)
    @Column(name = "blocker_keywords", columnDefinition = "jsonb")
    private List<String> blockerKeywords;

    @Type(JsonBinaryType.class)
    @Column(name = "leader_feedback", columnDefinition = "jsonb")
    private Map<String, Object> leaderFeedback;

    @Type(JsonBinaryType.class)
    @Column(name = "member_feedback", columnDefinition = "jsonb")
    private Map<String, Object> memberFeedback;

    @Type(JsonBinaryType.class)
    @Column(name = "career_tags", columnDefinition = "jsonb")
    private List<String> careerTags;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
