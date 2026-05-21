package com.masters.socratesai.interaction.dto;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class ExpertAgreementResponse {
    private Integer labeledInteractions;
    private Integer overlappingInteractions;
    private Double observedAgreement;
    private Double expectedAgreement;
    private Double cohenKappa;
    private Map<String, Integer> firstRaterLabelCounts;
    private Map<String, Integer> secondRaterLabelCounts;
}
