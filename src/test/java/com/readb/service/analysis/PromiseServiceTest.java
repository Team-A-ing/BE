package com.readb.service.analysis;

import com.readb.domain.meeting.Meeting;
import com.readb.domain.promise.Promise;
import com.readb.domain.promise.PromiseStatus;
import com.readb.dto.promise.FulfillmentRateResponse;
import com.readb.repository.MeetingRepository;
import com.readb.repository.PromiseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PromiseServiceTest {

    @Mock
    private PromiseRepository promiseRepository;

    @Mock
    private MeetingRepository meetingRepository;

    private PromiseService promiseService;

    @BeforeEach
    void setUp() {
        promiseService = new PromiseService(promiseRepository, meetingRepository);
    }

    // ⑤ teamId 필터 실 쿼리 테스트

    @Test
    void getPromisesByTeamReturnsPromisesForTeamMeetings() {
        Meeting m1 = Meeting.builder().id(1L).teamId(10L).build();
        Meeting m2 = Meeting.builder().id(2L).teamId(10L).build();
        Promise p1 = Promise.builder().meetingId(1L).ownerId(100L).status(PromiseStatus.PENDING).build();
        Promise p2 = Promise.builder().meetingId(2L).ownerId(200L).status(PromiseStatus.DONE).build();

        when(meetingRepository.findByTeamIdOrderByCreatedAtDesc(10L)).thenReturn(List.of(m1, m2));
        when(promiseRepository.findByMeetingIdIn(List.of(1L, 2L))).thenReturn(List.of(p1, p2));

        List<Promise> result = promiseService.getPromisesByTeam(10L);

        assertThat(result).hasSize(2);
        verify(meetingRepository).findByTeamIdOrderByCreatedAtDesc(10L);
        verify(promiseRepository).findByMeetingIdIn(List.of(1L, 2L));
    }

    @Test
    void getPromisesByTeamReturnsEmptyWhenNoMeetings() {
        when(meetingRepository.findByTeamIdOrderByCreatedAtDesc(99L)).thenReturn(List.of());
        when(promiseRepository.findByMeetingIdIn(List.of())).thenReturn(List.of());

        List<Promise> result = promiseService.getPromisesByTeam(99L);

        assertThat(result).isEmpty();
    }

    // ④ 이행률 계산 테스트

    @Test
    void getFulfillmentRateReturnsZeroWhenNoPromises() {
        when(promiseRepository.findByOwnerIdOrderByCreatedAtDesc(1L)).thenReturn(List.of());

        FulfillmentRateResponse result = promiseService.getFulfillmentRate(1L);

        assertThat(result.total()).isEqualTo(0);
        assertThat(result.doneRate()).isEqualTo(0.0);
        assertThat(result.missedRate()).isEqualTo(0.0);
        assertThat(result.pendingRate()).isEqualTo(0.0);
    }

    @Test
    void getFulfillmentRateCalculatesCountsAndRatesCorrectly() {
        List<Promise> promises = List.of(
                Promise.builder().ownerId(1L).status(PromiseStatus.DONE).build(),
                Promise.builder().ownerId(1L).status(PromiseStatus.DONE).build(),
                Promise.builder().ownerId(1L).status(PromiseStatus.MISSED).build(),
                Promise.builder().ownerId(1L).status(PromiseStatus.PENDING).build()
        );
        when(promiseRepository.findByOwnerIdOrderByCreatedAtDesc(1L)).thenReturn(promises);

        FulfillmentRateResponse result = promiseService.getFulfillmentRate(1L);

        assertThat(result.total()).isEqualTo(4);
        assertThat(result.doneCount()).isEqualTo(2);
        assertThat(result.missedCount()).isEqualTo(1);
        assertThat(result.pendingCount()).isEqualTo(1);
        assertThat(result.doneRate()).isEqualTo(50.0);
        assertThat(result.missedRate()).isEqualTo(25.0);
        assertThat(result.pendingRate()).isEqualTo(25.0);
    }

    @Test
    void getFulfillmentRateRoundsToOneDecimal() {
        List<Promise> promises = List.of(
                Promise.builder().ownerId(1L).status(PromiseStatus.DONE).build(),
                Promise.builder().ownerId(1L).status(PromiseStatus.MISSED).build(),
                Promise.builder().ownerId(1L).status(PromiseStatus.PENDING).build()
        );
        when(promiseRepository.findByOwnerIdOrderByCreatedAtDesc(1L)).thenReturn(promises);

        FulfillmentRateResponse result = promiseService.getFulfillmentRate(1L);

        // 1/3 = 33.3%
        assertThat(result.doneRate()).isEqualTo(33.3);
        assertThat(result.missedRate()).isEqualTo(33.3);
        assertThat(result.pendingRate()).isEqualTo(33.3);
    }
}
