package com.start.start_app.controller;

import com.start.start_app.infrastructure.dto.InsightResponseDTO;
import com.start.start_app.infrastructure.entitys.User;
import com.start.start_app.service.InsightService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/insights")
public class InsightController {

    private final InsightService insightService;

    public InsightController(InsightService insightService) {
        this.insightService = insightService;
    }

    // @AuthenticationPrincipal injeta automaticamente o usuário autenticado
    // a partir do SecurityContext — o mesmo que foi populado pelo SecurityFilter.
    // Assim, cada usuário só enxerga os seus próprios insights.
    @GetMapping
    public ResponseEntity<List<InsightResponseDTO>> getMyInsights(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(insightService.findByUser(user));
    }

    // 204 = removido com sucesso | 404 = não encontrado ou não pertence ao usuário
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteInsight(@PathVariable Long id, @AuthenticationPrincipal User user) {
        return insightService.delete(id, user)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }
}