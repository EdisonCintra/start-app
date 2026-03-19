package com.start.start_app.service;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.start.start_app.exception.business.GroqException;
import com.start.start_app.exception.business.InvalidDocumentException;
import com.start.start_app.infrastructure.dto.FinancialAnalysisDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class GroqService {

    @Value("${groq.api.key}")
    private String apiKey;

    private static final String GROQ_URL = "https://api.groq.com/openai/v1/chat/completions";

    // llama-3.3-70b-versatile: modelo gratuito da Groq com bom desempenho em português
    private static final String MODEL = "llama-3.3-70b-versatile";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    // RestClient.create() cria uma instância padrão sem necessidade de injeção do Builder
    public GroqService(ObjectMapper objectMapper) {
        this.restClient = RestClient.create();
        this.objectMapper = objectMapper;
    }

    //construção e configuração da requisição para a API da Groq
    public FinancialAnalysisDTO analyze(String maskedText) {
        Map<String, Object> requestBody = Map.of(
                "model", MODEL,
                // response_format: json_object força o modelo a retornar JSON válido
                "response_format", Map.of("type", "json_object"),
                "messages", List.of(Map.of("role", "user", "content", buildPrompt(maskedText)))
        );

        try {
            String rawResponse = restClient.post()
                    .uri(GROQ_URL)
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            // Java recalcula os totais — LLMs erram somas; delegamos apenas o que a IA faz bem
            return recalcularTotais(parseGroqResponse(rawResponse));

        } catch (InvalidDocumentException e) {
            throw e; // rejeição de negócio — não é falha técnica, não envolve no GroqException
        } catch (RestClientException e) {
            // Erro de rede ou status HTTP 4xx/5xx da Groq
            throw new GroqException("Falha na comunicação com a IA: " + e.getMessage());
        }
    }

    // Extrai o conteúdo do primeiro "choice" e desserializa no DTO.
    // Antes de mapear para FinancialAnalysisDTO, verifica se a IA rejeitou o documento
    // por não ser uma fatura — nesse caso o JSON retornado tem campo "error": "DOCUMENTO_INVALIDO".
    private FinancialAnalysisDTO parseGroqResponse(String rawResponse) {
        try {
            JsonNode root = objectMapper.readTree(rawResponse);
            // asString() substitui asText() depreciado no Jackson 3.x
            String content = root.path("choices").get(0).path("message").path("content").asString();

            // Parseia o conteúdo para inspecionar antes de desserializar no DTO.
            // has("error") evita chamar asString() em MissingNode — em Jackson 3.x isso lança exceção.
            JsonNode contentNode = objectMapper.readTree(content);
            if (contentNode.has("error") && "DOCUMENTO_INVALIDO".equals(contentNode.path("error").asString())) {
                // A IA identificou que o PDF não é uma fatura — não é falha técnica, é rejeição de negócio
                String motivo = contentNode.has("message")
                        ? contentNode.path("message").asString()
                        : "O documento enviado não é uma fatura. Envie uma fatura de cartão de crédito ou conta de serviço.";
                throw new InvalidDocumentException(motivo);
            }

            return objectMapper.readValue(content, FinancialAnalysisDTO.class);
        } catch (InvalidDocumentException e) {
            throw e; // repassa sem envolver no GroqException
        } catch (JacksonException | NullPointerException e) {
            throw new GroqException("Falha ao interpretar a resposta da IA: " + e.getMessage());
        }
    }

    // ─── Recálculo de totais ─────────────────────────────────────────────────
    // Records são imutáveis — criamos novas instâncias com os totais corrigidos.
    // A IA lista os itens; o Java faz a aritmética.
    private FinancialAnalysisDTO recalcularTotais(FinancialAnalysisDTO dto) {
        FinancialAnalysisDTO.EntriesSection entries = dto.entries();
        FinancialAnalysisDTO.ExpensesSection expenses = dto.expenses();

        FinancialAnalysisDTO.EntriesSection entriesRecalc = entries;
        if (entries != null && entries.items() != null) {
            BigDecimal total = entries.items().stream()
                    .filter(item -> item != null && item.value() != null) // item nulo → NPE sem esse filtro
                    .map(item -> parseMoeda(item.value()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            entriesRecalc = new FinancialAnalysisDTO.EntriesSection(
                    formatMoeda(total), entries.classNote(), entries.items());
        }

        FinancialAnalysisDTO.ExpensesSection expensesRecalc = expenses;
        if (expenses != null && expenses.items() != null) {
            BigDecimal total = expenses.items().stream()
                    .filter(item -> item != null && item.value() != null) // item nulo → NPE sem esse filtro
                    .map(item -> parseMoeda(item.value()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            expensesRecalc = new FinancialAnalysisDTO.ExpensesSection(
                    formatMoeda(total), expenses.items());
        }

        return new FinancialAnalysisDTO(entriesRecalc, expensesRecalc, dto.insights(), dto.advice());
    }

    // Converte "R$ -194,85" ou "R$ 194,85" → BigDecimal positivo
    private BigDecimal parseMoeda(String valor) {
        if (valor == null || valor.isBlank()) return BigDecimal.ZERO;
        String limpo = valor
                .replace("R$", "")
                .replaceAll("\\s", "")
                .replace(".", "")
                .replace(",", ".")
                .replace("-", ""); // ← remove sinal negativo
        try {
            return new BigDecimal(limpo);
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    // Formata BigDecimal para "R$ 1.234,56" no padrão brasileiro
    private String formatMoeda(BigDecimal valor) {
        NumberFormat nf = NumberFormat.getNumberInstance(Locale.of("pt", "BR"));
        nf.setMinimumFractionDigits(2);
        nf.setMaximumFractionDigits(2);
        return "R$ " + nf.format(valor.setScale(2, RoundingMode.HALF_UP));
    }

    // ─── Prompt ──────────────────────────────────────────────────────────────
    private String buildPrompt(String text) {
        return """
        Você é um analista financeiro especializado em documentos brasileiros.
        Sua função é IDENTIFICAR e CATEGORIZAR os lançamentos — não calcular totais.
        Os totais serão calculados externamente com precisão aritmética.

        ══════════════════════════════════════════════════════
        PASSO 0 — VALIDAÇÃO OBRIGATÓRIA ANTES DE QUALQUER ANÁLISE
        ══════════════════════════════════════════════════════
        Determine se o texto é de uma FATURA FINANCEIRA BRASILEIRA.

        DOCUMENTOS ACEITOS:
        - Fatura de cartão de crédito (Nubank, Itaú, Bradesco, etc.)
        - Conta de energia elétrica (CEMIG, Enel, CPFL, etc.)
        - Conta de água / saneamento
        - Conta de telefone / internet / TV
        - Conta de gás

        DOCUMENTOS NÃO ACEITOS (exemplos):
        - Contratos, apólices, termos de adesão
        - Notas fiscais avulsas de produtos
        - Boletos sem discriminação de lançamentos
        - Relatórios, artigos, currículos, manuais
        - Extratos bancários genéricos sem identificação de fatura
        - Qualquer outro documento que não seja uma fatura/conta de serviço

        Se o documento NÃO for uma fatura aceita, retorne APENAS este JSON e nada mais:
        {"error": "DOCUMENTO_INVALIDO", "message": "O documento enviado não é uma fatura reconhecida. Envie uma fatura de cartão de crédito ou conta de serviço (energia, água, telefone, internet ou gás)."}

        Se o documento FOR uma fatura aceita, ignore este passo e prossiga com as regras abaixo.
        ══════════════════════════════════════════════════════

        CONTEXTO IMPORTANTE — FATURA DE CARTÃO DE CRÉDITO:
        Em faturas de cartão, os sinais são invertidos em relação ao titular:
        - Valores com "-" (negativo) na fatura = ENTRADAS (pagamentos feitos pelo titular)
        - Valores sem "-" (positivo) na fatura = SAÍDAS (compras e débitos do titular)
        Exemplos:
          "-R$ 194,85 Pagamento em 10 MAR" → ENTRADA (titular pagou a fatura)
          "R$ 27,68 iFood - NuPay"         → SAÍDA (titular comprou algo)
          "R$ 43,49 Limite convertido"     → SAÍDA (titular usou crédito como saldo)
          "R$ 24,86 Renegociação"          → SAÍDA (débito parcelado)

        REGRAS DE EXTRAÇÃO:
        - Copie os valores SEM o sinal negativo (ex: "-R$ 194,85" vira "R$ 194,85")
        - Liste cada lançamento individualmente em items — nunca agrupe valores
        - Nunca invente, estime ou interpole valores ausentes
        - Se um valor não estiver claro no texto, use null
        - Sempre defina "total" como null — ele será calculado pelo sistema

        REGRAS DE CATEGORIZAÇÃO DE SAÍDAS:
        - "Compras com Cartão": iFood, Uber, estabelecimentos
        - "Juros e Encargos": IOF, juros, encargos de parcelamento
        - "Limite Convertido em Saldo": saques ou conversões de limite
        - "Renegociação / Parcelamento": parcelas de dívidas renegociadas
        - "Outros Lançamentos": o que não se encaixar acima

        REGRAS PARA INSIGHTS:
        - Foque em padrões de comportamento, não em cálculos de soma
        - Identifique categorias dominantes de gasto
        - Aponte hábitos relevantes (frequência, recorrência)
        - Se houver juros ou encargos, destaque isso com clareza
        - NÃO inclua somas totais nos insights

        RETORNE APENAS o JSON abaixo, sem texto, comentário ou markdown:
        {
          "entries": {
            "total": null,
            "classNote": null,
            "items": [
              {
                "description": "descrição da entrada (ex: Pagamento em 10 MAR)",
                "value": "R$ X.XXX,XX",
                "note": null
              }
            ]
          },
          "expenses": {
            "total": null,
            "items": [
              {
                "category": "categoria do gasto",
                "value": "R$ X.XXX,XX",
                "description": "detalhamento com data e estabelecimento"
              }
            ]
          },
          "insights": [
            "padrão ou comportamento observado",
            "destaque relevante sobre categoria ou hábito"
          ],
          "advice": [
            "conselho prático citando categoria específica",
            "conselho prático 2"
          ]
        }

        Texto do documento:
        """ + text;
    }

}