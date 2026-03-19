package com.start.start_app.exception.business;

// Estendemos RuntimeException para não travar o código com try-catch obrigatório
public class EmailConflictException extends RuntimeException {

    public EmailConflictException(String message) {
        super(message);
    }

    // Construtor padrão prático
    public EmailConflictException() {
        super("Conflito: Email já cadastrado no sistema.");
    }
}