package com.masters.socratesai.common.leetcode.dto.graphql;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GraphQLResponse {
    private DataNode data;
    private List<GraphQLError> errors;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DataNode {
        private QuestionList questionList;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class QuestionList {
        private Integer totalNum;
        private List<QuestionData> data;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class QuestionData {
        private String questionFrontendId;
        private String title;
        private String difficulty;
        private Boolean isPaidOnly;
        private String content;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GraphQLError {
        private String message;
    }
}