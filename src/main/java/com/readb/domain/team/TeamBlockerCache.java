package com.readb.domain.team;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "team_blocker_cache")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class TeamBlockerCache {

    @Id
    @Column(name = "team_id")
    private Long teamId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String signature;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public void update(String signature, String payload) {
        this.signature = signature;
        this.payload = payload;
    }
}
