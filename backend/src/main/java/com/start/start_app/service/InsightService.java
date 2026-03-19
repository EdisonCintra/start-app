package com.start.start_app.service;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.start.start_app.infrastructure.dto.FinancialAnalysisDTO;
import com.start.start_app.infrastructure.dto.InsightResponseDTO;
import com.start.start_app.infrastructure.entitys.Insight;
import com.start.start_app.infrastructure.entitys.User;
import com.start.start_app.infrastructure.repository.InsightRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class InsightService {

    private final InsightRepository insightRepository;

    // ObjectMapper é thread-safe e caro de instanciar — injetado pelo Spring
    // para reutilizar a mesma instância configurada em toda a aplicação.
    private final ObjectMapper objectMapper;

    public InsightService(InsightRepository insightRepository, ObjectMapper objectMapper) {
        this.insightRepository = insightRepository;
        this.objectMapper = objectMapper;
    }

    // Serializa a análise para JSON e persiste associada ao usuário.
    // Chamado pelo OcrController logo após receber a resposta do Groq.
    public Insight save(User user, FinancialAnalysisDTO analysis) {
        try {
            Insight insight = new Insight();
            insight.setUser(user);
            // Converte o DTO para String JSON antes de salvar no banco
            insight.setAnalysisJson(objectMapper.writeValueAsString(analysis));
            return insightRepository.save(insight);
        } catch (JacksonException e) {
            // Se a serialização falhar (não deveria), lança runtime para não silenciar o erro
            throw new RuntimeException("Erro ao serializar análise para JSON", e);
        }
    }

    // Remove um insight pelo id, verificando que pertence ao usuário autenticado.
    // Retorna false se o insight não existir ou não for do usuário — o controller decide o status HTTP.
    public boolean delete(Long id, User user) {
        return insightRepository.findByIdAndUser(id, user)
                .map(i -> { insightRepository.delete(i); return true; })
                .orElse(false);
    }

    // Retorna todos os insights do usuário, do mais recente ao mais antigo,
    // desserializando o JSON de volta para FinancialAnalysisDTO.
    public List<InsightResponseDTO> findByUser(User user) {
        return insightRepository.findByUserOrderByCreatedAtDesc(user)
                .stream()
                .map(this::toResponseDTO)
                .toList();
    }

    private InsightResponseDTO toResponseDTO(Insight insight) {
        try {
            FinancialAnalysisDTO analysis = objectMapper.readValue(
                    insight.getAnalysisJson(),
                    FinancialAnalysisDTO.class
            );
            return new InsightResponseDTO(insight.getId(), insight.getCreatedAt(), analysis);
        } catch (JacksonException e) {
            throw new RuntimeException("Erro ao desserializar análise do banco", e);
        }
    }
}