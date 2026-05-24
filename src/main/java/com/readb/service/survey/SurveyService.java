package com.readb.service.survey;

import com.readb.common.exception.BusinessException;
import com.readb.common.exception.ErrorCode;
import com.readb.domain.survey.Survey;
import com.readb.dto.survey.SurveyHistoryResponse;
import com.readb.dto.survey.SurveyRequest;
import com.readb.dto.survey.SurveyResponse;
import com.readb.repository.SurveyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SurveyService {

    private final SurveyRepository surveyRepository;

    @Transactional
    public void submitSurvey(Long memberId, SurveyRequest request) {
        if (surveyRepository.existsByMeetingIdAndMemberId(request.meetingId(), memberId)) {
            throw new BusinessException(ErrorCode.SURVEY_ALREADY_SUBMITTED);
        }
        Survey survey = Survey.builder()
                .meetingId(request.meetingId())
                .memberId(memberId)
                .scores(request.scores())
                .build();
        surveyRepository.save(survey);
    }

    @Transactional(readOnly = true)
    public List<SurveyHistoryResponse> getHistory(Long memberId) {
        return surveyRepository.findByMemberIdOrderBySubmittedAtDesc(memberId).stream()
                .map(s -> new SurveyHistoryResponse(s.getMeetingId(), s.getSubmittedAt(), s.getScores()))
                .toList();
    }

    @Transactional(readOnly = true)
    public SurveyResponse getSurvey(Long meetingId) {
        Survey survey = surveyRepository.findByMeetingId(meetingId).stream()
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.SURVEY_NOT_FOUND));
        return SurveyResponse.from(survey);
    }
}
