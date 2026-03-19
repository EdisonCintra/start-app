package com.start.start_app.exception.handler;

import com.start.start_app.exception.business.EmailConflictException;
import com.start.start_app.exception.business.GroqException;
import com.start.start_app.exception.business.InvalidDocumentException;
import com.start.start_app.exception.business.NoExistEmail;
import com.start.start_app.exception.business.OcrException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@ControllerAdvice
public class RestExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(RestExceptionHandler.class);

    @ExceptionHandler(EmailConflictException.class)
    private ResponseEntity<RestErrorMessage> emailConflictHandler(EmailConflictException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new RestErrorMessage(exception.getMessage()));
    }

    @ExceptionHandler(NoExistEmail.class)
    private ResponseEntity<RestErrorMessage> noExistEmailHandler(NoExistEmail exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new RestErrorMessage(exception.getMessage()));
    }

    // 401 Unauthorized = credenciais inválidas no login
    @ExceptionHandler(BadCredentialsException.class)
    private ResponseEntity<RestErrorMessage> badCredentialsHandler(BadCredentialsException exception) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new RestErrorMessage("E-mail ou senha inválidos."));
    }

    // 422 Unprocessable Entity = entendemos a requisição mas não conseguimos processá-la
    @ExceptionHandler(OcrException.class)
    private ResponseEntity<RestErrorMessage> ocrHandler(OcrException exception) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(new RestErrorMessage(exception.getMessage()));
    }

    // 422 Unprocessable Entity = PDF recebido, mas não é uma fatura reconhecida
    @ExceptionHandler(InvalidDocumentException.class)
    private ResponseEntity<RestErrorMessage> invalidDocumentHandler(InvalidDocumentException exception) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(new RestErrorMessage(exception.getMessage()));
    }

    // 502 Bad Gateway = o servidor recebeu uma resposta inválida de um serviço externo (Groq)
    @ExceptionHandler(GroqException.class)
    private ResponseEntity<RestErrorMessage> groqHandler(GroqException exception) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new RestErrorMessage(exception.getMessage()));
    }

    // Fallback para qualquer exceção não mapeada — garante que o corpo da resposta
    // sempre tenha campo "message" legível pelo frontend
    @ExceptionHandler(Exception.class)
    private ResponseEntity<RestErrorMessage> genericHandler(Exception exception) {
        log.error("Erro interno não tratado: {}", exception.getMessage(), exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new RestErrorMessage("Erro interno no servidor."));
    }
}