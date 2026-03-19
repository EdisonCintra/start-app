package com.start.start_app.exception.handler;

import lombok.AllArgsConstructor;
import lombok.Getter;

// O status HTTP já está no código de resposta do ResponseEntity —
// não precisa repetir no corpo. Manter só message evita problemas
// de serialização do enum HttpStatus no Jackson 3.x (Spring Boot 4.x).
@AllArgsConstructor
@Getter
public class RestErrorMessage {
    private String message;
}