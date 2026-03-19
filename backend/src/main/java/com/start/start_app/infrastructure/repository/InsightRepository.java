package com.start.start_app.infrastructure.repository;

import com.start.start_app.infrastructure.entitys.Insight;
import com.start.start_app.infrastructure.entitys.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InsightRepository extends JpaRepository<Insight, Long> {

    // Spring Data gera a query automaticamente pelo nome do método:
    // SELECT * FROM insights WHERE user_id = ? ORDER BY created_at DESC
    List<Insight> findByUserOrderByCreatedAtDesc(User user);

    // Busca por id E user numa query só — evita acessar o relacionamento lazy
    // para verificar ownership, o que causaria LazyInitializationException.
    java.util.Optional<Insight> findByIdAndUser(Long id, User user);
}