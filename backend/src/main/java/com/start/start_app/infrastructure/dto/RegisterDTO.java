package com.start.start_app.infrastructure.dto;

import com.start.start_app.infrastructure.entitys.UserRole;

public record RegisterDTO(String login, String password, UserRole role) {
}
