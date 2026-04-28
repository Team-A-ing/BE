package com.readb.service.survey;

import com.readb.common.exception.BusinessException;
import com.readb.common.exception.ErrorCode;
import com.readb.domain.survey.Survey;
import com.readb.dto.survey.SurveyRequest;
import com.readb.dto.survey.SurveyResponse;
import com.readb.repository.SurveyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SurveyService {

    private final SurveyRepository surveyRepository;

    @Transactional
    public void submitSurvey(Long memberId, SurveyRequest request) {
        // TODO: BE2 구현
        throw new UnsupportedOperationException("BE2 구현 예정");
    }

    @Transactional(readOnly = true)
    public SurveyResponse getSurvey(Long meetingId) {
        Survey survey = surveyRepository.findByMeetingId(meetingId).stream()
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.SURVEY_NOT_FOUND));
        return SurveyResponse.from(survey);
    }
}
