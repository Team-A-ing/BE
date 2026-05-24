package com.readb.service.analysis;

import com.readb.domain.promise.Promise;
import com.readb.domain.promise.PromiseStatus;
import com.readb.dto.promise.FulfillmentRateResponse;
import com.readb.repository.MeetingRepository;
import com.readb.repository.PromiseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PromiseService {

    private final PromiseRepository promiseRepository;
    private final MeetingRepository meetingRepository;

    @Transactional(readOnly = true)
    public List<Promise> getPromisesByTeam(Long teamId) {
        List<Long> meetingIds = meetingRepository.findByTeamIdOrderByCreatedAtDesc(teamId)
                .stream().map(m -> m.getId()).toList();
        return promiseRepository.findByMeetingIdIn(meetingIds);
    }

    @Transactional(readOnly = true)
    public FulfillmentRateResponse getFulfillmentRate(Long ownerId) {
        List<Promise> promises = promiseRepository.findByOwnerIdOrderByCreatedAtDesc(ownerId);
        int total = promises.size();
        if (total == 0) return new FulfillmentRateResponse(0, 0, 0, 0, 0.0, 0.0, 0.0);

        int doneCount = (int) promises.stream().filter(p -> p.getStatus() == PromiseStatus.DONE).count();
        int missedCount = (int) promises.stream().filter(p -> p.getStatus() == PromiseStatus.MISSED).count();
        int pendingCount = total - doneCount - missedCount;

        return new FulfillmentRateResponse(
                total,
                doneCount,
                missedCount,
                pendingCount,
                round(doneCount, total),
                round(missedCount, total),
                round(pendingCount, total)
        );
    }

    private double round(int count, int total) {
        return Math.round((double) count / total * 1000) / 10.0;
    }
}
