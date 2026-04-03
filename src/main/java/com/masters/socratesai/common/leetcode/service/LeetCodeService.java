package com.masters.socratesai.common.leetcode.service;

import com.masters.socratesai.common.leetcode.dto.LeetCodeTasksRequest;
import com.masters.socratesai.common.leetcode.dto.LeetCodeTasksResponse;
import com.masters.socratesai.common.leetcode.dto.TaskDto;
import com.masters.socratesai.common.leetcode.dto.graphql.GraphQLRequest;
import com.masters.socratesai.common.leetcode.dto.graphql.GraphQLResponse;
import com.masters.socratesai.common.leetcode.dto.graphql.QuestionListVariables;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LeetCodeService {

    private final RestClient leetCodeRestClient;

    private static final String QUERY = """
        query problemsetQuestionList($categorySlug: String, $limit: Int, $skip: Int, $filters: QuestionListFilterInput) {
          questionList(
            categorySlug: $categorySlug
            limit: $limit
            skip: $skip
            filters: $filters
          ) {
            totalNum
            data {
              questionFrontendId
              title
              difficulty
              isPaidOnly
              content
            }
          }
        }
        """;

    public LeetCodeTasksResponse fetchTasks(LeetCodeTasksRequest request) {
        QuestionListVariables variables = new QuestionListVariables(
                request.getCategorySlug(),
                request.getLimit(),
                request.getSkip(),
                request.getFilters()
        );

        GraphQLRequest<QuestionListVariables> graphQLRequest =
                new GraphQLRequest<>(QUERY, variables);

        GraphQLResponse response = leetCodeRestClient.post()
                .uri("/graphql")
                .body(graphQLRequest)
                .retrieve()
                .body(GraphQLResponse.class);

        if (response == null) {
            throw new RuntimeException("Empty response from LeetCode");
        }

        if (response.getErrors() != null && !response.getErrors().isEmpty()) {
            throw new RuntimeException("LeetCode GraphQL error: " + response.getErrors().get(0).getMessage());
        }

        if (response.getData() == null || response.getData().getQuestionList() == null) {
            return new LeetCodeTasksResponse(0, Collections.emptyList());
        }

        List<TaskDto> tasks = response.getData().getQuestionList().getData()
                .stream()
                .map(q -> {
                    TaskDto dto = new TaskDto();
                    dto.setQuestionFrontendId(q.getQuestionFrontendId());
                    dto.setTitle(q.getTitle());
                    dto.setDifficulty(q.getDifficulty());
                    dto.setIsPaidOnly(q.getIsPaidOnly());
                    dto.setContent(q.getContent());
                    return dto;
                })
                .toList();

        return new LeetCodeTasksResponse(
                response.getData().getQuestionList().getTotalNum(),
                tasks
        );
    }
}