package com.readb.dto.analysis;

public record BlockerKeyword(
        String keyword,
        int count,
        int mentionedBy,
        java.util.List<String> memberNames
) {}
