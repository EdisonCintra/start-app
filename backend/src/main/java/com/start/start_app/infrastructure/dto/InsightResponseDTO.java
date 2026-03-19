package com.start.start_app.infrastructure.dto;

import java.time.LocalDateTime;

// DTO de resposta para um insight salvo.
// Retorna o id (para futuras operações), quando foi gerado,
// e a análise financeira completa desserializada.
public record InsightResponseDTO(
        Long id,
        LocalDateTime createdAt,
        FinancialAnalysisDTO analysis
) {}