package com.readb.service.analysis;

import com.readb.domain.promise.Promise;
import com.readb.domain.promise.PromiseStatus;
import com.readb.repository.PromiseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PromiseService {

    private final PromiseRepository promiseRepository;

    @Transactional(readOnly = true)
    public List<Promise> getPromisesByLeader(Long leaderId) {
        // TODO(5/7+): Meeting을 거쳐 팀 단위로 promises 집계하도록 확장.
        List<Promise> actual = promiseRepository.findByOwnerIdOrderByCreatedAtDesc(leaderId);
        if (!actual.isEmpty()) return actual;

        // 5/8 데모 stub — DB가 비어 있을 때 빈 화면 회피용 정적 더미.
        return List.of(
                Promise.builder()
                        .meetingId(101L)
                        .ownerId(leaderId)
                        .content("팀원 Career Memory 주간 1회 리뷰")
                        .deadline(LocalDate.now().plusDays(7))
                        .status(PromiseStatus.PENDING)
                        .build(),
                Promise.builder()
                        .meetingId(102L)
                        .ownerId(leaderId)
                        .content("QA 리소스 확보 방안 논의")
                        .deadline(LocalDate.now().minusDays(2))
                        .status(PromiseStatus.MISSED)
                        .build(),
                Promise.builder()
                        .meetingId(100L)
                        .ownerId(leaderId)
                        .content("1on1 주기 격주로 변경")
                        .deadline(LocalDate.now().minusDays(10))
                        .status(PromiseStatus.DONE)
                        .build()
        );
    }
}
