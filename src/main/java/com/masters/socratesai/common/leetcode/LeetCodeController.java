package com.masters.socratesai.common.leetcode;

import com.masters.socratesai.common.leetcode.dto.LeetCodeTasksRequest;
import com.masters.socratesai.common.leetcode.dto.LeetCodeTasksResponse;
import com.masters.socratesai.common.leetcode.service.LeetCodeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/leetcode")
@RequiredArgsConstructor
public class LeetCodeController {

    private final LeetCodeService leetCodeService;

    @PostMapping("/problems")
    public LeetCodeTasksResponse fetchTasks(@RequestBody LeetCodeTasksRequest request) {
        return leetCodeService.fetchTasks(request);
    }
}
