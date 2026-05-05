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

    // 3-Gap Model
    @Column(name = "alignment_gap")
    private Double alignmentGap;

    @Column(name = "honesty_gap")
    private Double honestyGap;

    @Column(name = "execution_gap")
    private Double executionGap;

    // Speech Act 카운팅 기반 심리적 안전감 점수 (0~100)
    @Column(name = "safety_score")
    private Double safetyScore;

    // {"vulnerability":[{text,timestamp}], "dissent":[...], "initiative":[...]}
    @Type(JsonBinaryType.class)
    @Column(name = "speech_acts", columnDefinition = "jsonb")
    private Map<String, Object> speechActs;

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

    // 최근 3회 평균 등 베이스라인: {prev_avg_vulnerability, prev_avg_dissent, prev_avg_initiative, ...}
    @Type(JsonBinaryType.class)
    @Column(name = "baseline_data", columnDefinition = "jsonb")
    private Map<String, Object> baselineData;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
