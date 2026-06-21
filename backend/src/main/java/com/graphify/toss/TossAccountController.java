package com.graphify.toss;

import com.graphify.common.dto.ApiResponse;
import com.graphify.history.HistoryService;
import com.graphify.toss.dto.TossAccountDto;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/toss/accounts")
public class TossAccountController {

    private final TossAccountService accountService;

    public TossAccountController(TossAccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping
    public ApiResponse<List<TossAccountDto>> getAccounts() {
        Long userId = HistoryService.requireCurrentUserId();
        return ApiResponse.ok(accountService.getAccounts(userId));
    }
}
