package com.readb.dto.analysis;

import java.util.List;

public record BlockerKeyword(
        String keyword,
        int count,
        int mentionedBy,
        List<RelatedMember> relatedMembers,
        String actionGuide
) {
    public record RelatedMember(Long memberId, String memberName, int mentionCount) {}
}
