package com.masters.socratesai.interaction.service;

import com.masters.socratesai.interaction.dto.ExpertAgreementResponse;
import com.masters.socratesai.interaction.dto.ExpertLabelRequest;
import com.masters.socratesai.interaction.dto.ExpertLabelResponse;
import com.masters.socratesai.interaction.model.ExpertActionLabel;
import com.masters.socratesai.interaction.model.InteractionLog;
import com.masters.socratesai.interaction.repo.ExpertActionLabelRepository;
import com.masters.socratesai.interaction.repo.InteractionLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ExpertLabelService {

    private final InteractionLogRepository interactionLogRepository;
    private final ExpertActionLabelRepository expertActionLabelRepository;

    public ExpertLabelService(
            InteractionLogRepository interactionLogRepository,
            ExpertActionLabelRepository expertActionLabelRepository
    ) {
        this.interactionLogRepository = interactionLogRepository;
        this.expertActionLabelRepository = expertActionLabelRepository;
    }

    @Transactional
    public ExpertLabelResponse upsertLabel(UUID interactionId, ExpertLabelRequest request) {
        InteractionLog interaction = interactionLogRepository.findById(interactionId)
                .orElseThrow(() -> new IllegalArgumentException("Interaction not found: " + interactionId));

        ExpertActionLabel label = expertActionLabelRepository
                .findByInteractionInteractionIdAndReviewerId(interactionId, request.getReviewerId())
                .orElseGet(() -> ExpertActionLabel.builder()
                        .interaction(interaction)
                        .reviewerId(request.getReviewerId())
                        .build());

        label.setTargetFeedbackAction(request.getTargetFeedbackAction().name());
        label.setConfidence(request.getConfidence());
        label.setRationale(request.getRationale());

        return toResponse(expertActionLabelRepository.save(label));
    }

    @Transactional(readOnly = true)
    public byte[] exportLabelsCsv() {
        List<ExpertActionLabel> labels = expertActionLabelRepository.findAllByOrderByCreatedAtAsc();
        StringBuilder csv = new StringBuilder();
        csv.append(String.join(",",
                "label_id",
                "interaction_id",
                "reviewer_id",
                "target_feedback_action",
                "confidence",
                "rationale",
                "created_at",
                "error_type",
                "feedback_action"
        )).append('\n');

        for (ExpertActionLabel label : labels) {
            InteractionLog interaction = label.getInteraction();
            csv.append(String.join(",",
                    escape(label.getLabelId()),
                    escape(interaction.getInteractionId()),
                    escape(label.getReviewerId()),
                    escape(label.getTargetFeedbackAction()),
                    escape(label.getConfidence()),
                    escape(label.getRationale()),
                    escape(label.getCreatedAt()),
                    escape(interaction.getErrorType()),
                    escape(interaction.getFeedbackAction())
            )).append('\n');
        }

        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Transactional(readOnly = true)
    public ExpertAgreementResponse calculateAgreement() {
        List<ExpertActionLabel> labels = expertActionLabelRepository.findAllByOrderByCreatedAtAsc();
        Map<UUID, List<ExpertActionLabel>> labelsByInteraction = labels.stream()
                .collect(Collectors.groupingBy(label -> label.getInteraction().getInteractionId()));

        int overlappingInteractions = 0;
        int exactMatches = 0;
        Map<String, Integer> firstCounts = new TreeMap<>();
        Map<String, Integer> secondCounts = new TreeMap<>();

        for (List<ExpertActionLabel> interactionLabels : labelsByInteraction.values()) {
            if (interactionLabels.size() < 2) {
                continue;
            }

            interactionLabels.sort(Comparator.comparing(ExpertActionLabel::getReviewerId));
            String first = interactionLabels.get(0).getTargetFeedbackAction();
            String second = interactionLabels.get(1).getTargetFeedbackAction();

            overlappingInteractions++;
            if (Objects.equals(first, second)) {
                exactMatches++;
            }
            firstCounts.merge(first, 1, Integer::sum);
            secondCounts.merge(second, 1, Integer::sum);
        }

        double observed = overlappingInteractions == 0 ? 0.0 : (double) exactMatches / overlappingInteractions;
        double expected = calculateExpectedAgreement(firstCounts, secondCounts, overlappingInteractions);
        double kappa = overlappingInteractions == 0 || expected == 1.0 ? 0.0 : (observed - expected) / (1.0 - expected);

        return ExpertAgreementResponse.builder()
                .labeledInteractions(labelsByInteraction.size())
                .overlappingInteractions(overlappingInteractions)
                .observedAgreement(round4(observed))
                .expectedAgreement(round4(expected))
                .cohenKappa(round4(kappa))
                .firstRaterLabelCounts(firstCounts)
                .secondRaterLabelCounts(secondCounts)
                .build();
    }

    private double calculateExpectedAgreement(Map<String, Integer> firstCounts, Map<String, Integer> secondCounts, int total) {
        if (total == 0) {
            return 0.0;
        }
        Set<String> labels = new HashSet<>();
        labels.addAll(firstCounts.keySet());
        labels.addAll(secondCounts.keySet());

        double expected = 0.0;
        for (String label : labels) {
            double firstRate = firstCounts.getOrDefault(label, 0) / (double) total;
            double secondRate = secondCounts.getOrDefault(label, 0) / (double) total;
            expected += firstRate * secondRate;
        }
        return expected;
    }

    private ExpertLabelResponse toResponse(ExpertActionLabel label) {
        return ExpertLabelResponse.builder()
                .labelId(label.getLabelId())
                .interactionId(label.getInteraction().getInteractionId())
                .reviewerId(label.getReviewerId())
                .targetFeedbackAction(label.getTargetFeedbackAction())
                .confidence(label.getConfidence())
                .rationale(label.getRationale())
                .createdAt(label.getCreatedAt())
                .build();
    }

    private double round4(double value) {
        return Math.round(value * 10_000.0) / 10_000.0;
    }

    private String escape(Object value) {
        if (value == null) {
            return "";
        }

        String text = String.valueOf(value);
        String escaped = text.replace("\"", "\"\"");
        if (escaped.contains(",") || escaped.contains("\"") || escaped.contains("\n") || escaped.contains("\r")) {
            return "\"" + escaped + "\"";
        }
        return escaped;
    }
}
