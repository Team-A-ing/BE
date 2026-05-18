package com.readb.domain.recording;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "recordings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Recording {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "meeting_id", nullable = false, unique = true)
    private Long meetingId;

    @Column(name = "file_url")
    private String fileUrl;

    @Column(name = "duration_sec")
    private Integer durationSec;

    @Column(columnDefinition = "TEXT")
    private String transcript;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public void updateTranscript(String transcript) {
        this.transcript = transcript;
    }

    public void updateFileUrl(String fileUrl) {
        this.fileUrl = fileUrl;
    }

    public void updateDurationSec(Integer durationSec) {
        this.durationSec = durationSec;
    }

    public void deleteFileUrl() {
        this.fileUrl = null;
    }
}
