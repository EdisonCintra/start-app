package com.start.start_app.exception.business;

// Lançada quando o documento enviado não é uma fatura reconhecida pelo sistema.
// Separada de GroqException para retornar 422 em vez de 502 — o problema está no documento, não na IA.
public class InvalidDocumentException extends RuntimeException {

    public InvalidDocumentException(String message) {
        super(message);
    }
}