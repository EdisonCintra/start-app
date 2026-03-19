package com.start.start_app.exception.business;

// Representa qualquer falha relacionada ao processamento OCR
// Segue o mesmo padrão das outras exceptions do projeto
public class OcrException extends RuntimeException {

    public OcrException(String message) {
        super(message);
    }

    // Construtor padrão para quando não temos contexto adicional
    public OcrException() {
        super("Erro ao processar OCR no arquivo enviado.");
    }
}
