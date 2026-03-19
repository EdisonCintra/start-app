package com.start.start_app.infrastructure.repository;

import com.start.start_app.infrastructure.entitys.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User,Integer> {



    UserDetails findByLogin(String login);

}
