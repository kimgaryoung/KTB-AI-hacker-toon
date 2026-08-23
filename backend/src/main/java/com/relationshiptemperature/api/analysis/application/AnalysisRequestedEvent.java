package com.relationshiptemperature.api.analysis.application;

import java.util.UUID;

public record AnalysisRequestedEvent(UUID jobId) {
}
