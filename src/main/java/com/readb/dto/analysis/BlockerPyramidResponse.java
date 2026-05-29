package com.readb.dto.analysis;

import java.util.List;

public record BlockerPyramidResponse(
        List<BlockerKeyword> blockerKeywords,
        List<ActionPrescription> actionPrescriptions
) {
    public record ActionPrescription(
            String severity,
            String title,
            String dataSummary,
            String actionGuide
    ) {}
}
