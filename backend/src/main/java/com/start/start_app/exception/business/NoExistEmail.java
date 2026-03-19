package com.start.start_app.exception.business;

public class NoExistEmail extends RuntimeException {
    public NoExistEmail(String message) {
        super(message);
    }

    public NoExistEmail() {
        super("Usuário não encontrado."
        );
    }
}
