package com.start.start_app.exception.business;

// Representa qualquer falha na integração com a API da Groq (IA).
// Separada de OcrException para facilitar tratamento HTTP diferente no handler.
public class GroqException extends RuntimeException {

    public GroqException(String message) {
        super(message);
    }
}