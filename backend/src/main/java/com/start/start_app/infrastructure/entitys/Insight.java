package com.start.start_app.infrastructure.entitys;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

// Representa um insight financeiro gerado pela IA para um usuário específico.
// Cada vez que o usuário analisa um documento, o resultado é persistido aqui
// para que ele possa consultar o histórico depois.
@Entity
@Table(name = "insights")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Insight {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Relacionamento N:1 — muitos insights pertencem a um único usuário.
    // @JoinColumn cria a coluna "user_id" (FK) na tabela insights.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // Armazena o JSON completo da FinancialAnalysisDTO como texto.
    // Usar TEXT permite análises longas sem truncamento.
    @Column(columnDefinition = "TEXT", nullable = false)
    private String analysisJson;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    // Executado automaticamente pelo JPA antes de qualquer INSERT,
    // garantindo que createdAt seja sempre preenchido.
    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}