package com.graphify.trading.rule;

import com.graphify.common.dto.ApiResponse;
import com.graphify.trading.rule.dto.RuleResponse;
import com.graphify.trading.rule.dto.RuleUpsertRequest;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/trading/paper/rules")
public class PaperRuleController {

    private final PaperRuleService paperRuleService;

    public PaperRuleController(PaperRuleService paperRuleService) {
        this.paperRuleService = paperRuleService;
    }

    @GetMapping
    public ApiResponse<List<RuleResponse>> list() {
        return paperRuleService.list();
    }

    @GetMapping("/{id}")
    public ApiResponse<RuleResponse> get(@PathVariable Long id) {
        return paperRuleService.get(id);
    }

    @PostMapping
    public ApiResponse<RuleResponse> create(@RequestBody RuleUpsertRequest request) {
        return paperRuleService.create(request);
    }

    @PutMapping("/{id}")
    public ApiResponse<RuleResponse> update(@PathVariable Long id, @RequestBody RuleUpsertRequest request) {
        return paperRuleService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        return paperRuleService.delete(id);
    }
}
