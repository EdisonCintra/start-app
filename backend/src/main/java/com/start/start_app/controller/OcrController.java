package com.start.start_app.controller;

import com.start.start_app.exception.business.OcrException;
import com.start.start_app.infrastructure.dto.FinancialAnalysisDTO;
import com.start.start_app.infrastructure.entitys.User;
import com.start.start_app.service.GroqService;
import com.start.start_app.service.InsightService;
import com.start.start_app.service.OcrService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/ocr")
public class OcrController {

    private final OcrService ocrService;
    private final GroqService groqService;
    private final InsightService insightService;

    public OcrController(OcrService ocrService, GroqService groqService, InsightService insightService) {
        this.ocrService = ocrService;
        this.groqService = groqService;
        this.insightService = insightService;
    }

    // Retorna apenas o texto bruto mascarado (mantido para compatibilidade)
    @PostMapping
    public ResponseEntity<String> extractText(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(ocrService.extractText(file));
    }

    // Extrai o texto via OCR, aplica mascaramento de PII, envia à IA para análise financeira
    // e persiste o resultado vinculado ao usuário autenticado para consulta futura.
    // Aceita List para detectar envio múltiplo e rejeitar antes de qualquer processamento.
    @PostMapping("/analyze")
    public ResponseEntity<FinancialAnalysisDTO> analyzeDocument(
            @RequestParam("file") List<MultipartFile> files,
            @AuthenticationPrincipal User user) {

        if (files.size() > 1) {
            throw new OcrException("Envie apenas um arquivo por vez. Foram recebidos " + files.size() + " arquivos.");
        }

        String maskedText = ocrService.extractText(files.get(0));
        FinancialAnalysisDTO analysis = groqService.analyze(maskedText);

        // Salva o insight no banco associado ao usuário — ele poderá ver o histórico em GET /insights
        insightService.save(user, analysis);

        return ResponseEntity.ok(analysis);
    }
}
