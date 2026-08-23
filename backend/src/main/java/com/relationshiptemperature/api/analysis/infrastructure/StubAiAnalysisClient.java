package com.relationshiptemperature.api.analysis.infrastructure;

import com.relationshiptemperature.api.analysis.application.AiAnalysisClient;
import com.relationshiptemperature.api.report.domain.RelationshipReport.PrqcScores;
import com.relationshiptemperature.api.report.domain.ReportEvidence.Metric;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.ai.mode", havingValue = "stub", matchIfMissing = true)
public class StubAiAnalysisClient implements AiAnalysisClient {

    @Override
    public AnalysisResult analyze(AnalysisRequest request) {
        PrqcScores scores = new PrqcScores(70, 68, 74, 76, 64, 72);
        return new AnalysisResult(
                "prqc-2026-08-19.1",
                "relationship-evidence-1.0.0",
                0,
                scores,
                List.of(new EvidenceResult(
                        "passion",
                        scores.passion(),
                        "대화 빈도와 시작 비율에 변화가 관찰됐어요. 실제 AI 어댑터로 교체해 주세요.",
                        new Metric("weeklyConversationCount", 1.0, null, "회/주", "최근 4주")
                )),
                "체크인에서 느낀 관계의 분위기와 대화에서 관찰된 신호를 함께 살펴보세요."
        );
    }
}
