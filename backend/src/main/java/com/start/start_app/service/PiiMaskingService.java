package com.start.start_app.service;

import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

/**
 * Detecta e mascara dados pessoais sensíveis (PII) em texto livre.
 * Cada padrão substitui a informação encontrada por "******".
 *
 * A ordem dos replaces importa:
 *   CNPJ antes de CPF  → CNPJ contém sequência que o CPF também bate
 *   RG_STATE_PREFIX antes de RG → evita que "MG-12.345.678" seja parcialmente mascarado
 *   PHONE_0800 antes de PHONE  → o padrão genérico de telefone faz match parcial em 0800
 */
@Service
public class PiiMaskingService {

    private static final String MASK = "******";

    // CNPJ: 00.000.000/0000-00  ou  00000000000000
    private static final Pattern CNPJ = Pattern.compile(
            "\\d{2}\\.?\\d{3}\\.?\\d{3}/?\\d{4}-?\\d{2}"
    );

    // CPF: 000.000.000-00  |  000 000 000 00  |  00000000000
    // Separadores aceitos: ponto OU espaço entre grupos; traço ou espaço antes dos 2 últimos dígitos
    private static final Pattern CPF = Pattern.compile(
            "\\d{3}[. ]?\\d{3}[. ]?\\d{3}[- ]?\\d{2}"
    );

    // RG com prefixo de estado: MG-12.345.678 / SP-1.234.567-X
    // O hífen explícito entre sigla e número torna o match inequívoco
    private static final Pattern RG_STATE_PREFIX = Pattern.compile(
            "[A-Z]{2}-\\d{1,2}\\.?\\d{3}\\.?\\d{3}(-?[0-9Xx])?"
    );

    // RG formatado: 12.345.678-9 / 12.345.678X / 123456789 (8-9 dígitos)
    private static final Pattern RG = Pattern.compile(
            "\\d{1,2}\\.?\\d{3}\\.?\\d{3}-?[0-9Xx]"
    );

    // RG antigo de 7 dígitos: requer o rótulo "RG" antes para evitar falsos positivos.
    // "1234567" puro é indistinguível de qualquer outro número sem contexto.
    // O grupo 1 captura o rótulo — replaceAll("$1" + MASK) mascara só o número,
    // preservando o texto "RG" ou "rg" para o usuário entender o que foi omitido.
    private static final Pattern RG_LABELED_7 = Pattern.compile(
            "(?i)(rg\\s*[-:]?\\s*)\\d{7}"
    );

    // Número de cartão: 16 dígitos em grupos de 4 separados por espaço ou hífen
    private static final Pattern CARD = Pattern.compile(
            "\\b\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}\\b"
    );

    // E-mail
    private static final Pattern EMAIL = Pattern.compile(
            "[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}"
    );

    // 0800: 0800 123 4567 / 0800-123-4567 / 08001234567
    // Padrão dedicado: o regex genérico de telefone faz match parcial (só "0800 1234")
    private static final Pattern PHONE_0800 = Pattern.compile(
            "\\b0800[\\s-]?\\d{3}[\\s-]?\\d{4}\\b"
    );

    // Telefone convencional: (11) 91234-5678 / 11912345678 / +55 11 91234-5678
    private static final Pattern PHONE = Pattern.compile(
            "(\\+55[\\s-]?)?(\\(?\\d{2}\\)?[\\s-]?)?\\d{4,5}[\\s-]?\\d{4}"
    );

    public String mask(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }

        String result = text;
        result = CNPJ            .matcher(result).replaceAll(MASK);
        result = CPF             .matcher(result).replaceAll(MASK);
        result = RG_STATE_PREFIX .matcher(result).replaceAll(MASK);
        result = RG              .matcher(result).replaceAll(MASK);
        // $1 preserva o rótulo "RG" — apenas o número vira ******
        result = RG_LABELED_7   .matcher(result).replaceAll("$1" + MASK);
        result = CARD            .matcher(result).replaceAll(MASK);
        result = EMAIL           .matcher(result).replaceAll(MASK);
        result = PHONE_0800      .matcher(result).replaceAll(MASK);
        result = PHONE           .matcher(result).replaceAll(MASK);

        return result;
    }
}