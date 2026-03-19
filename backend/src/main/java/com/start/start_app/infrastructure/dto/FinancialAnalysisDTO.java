package com.start.start_app.infrastructure.dto;

import java.util.List;

// DTO que representa a análise financeira retornada pela IA (Groq).
public record FinancialAnalysisDTO(
        EntriesSection entries,
        ExpensesSection expenses,
        List<String> insights,
        List<String> advice
) {

    public record EntriesSection(
            String total,
            String classNote,
            List<EntryItem> items
    ) {}

    public record EntryItem(
            String description,
            String value,
            String note
    ) {}

    public record ExpensesSection(
            String total,
            List<ExpenseItem> items
    ) {}

    public record ExpenseItem(
            String category,
            String value,
            String description
    ) {}
}