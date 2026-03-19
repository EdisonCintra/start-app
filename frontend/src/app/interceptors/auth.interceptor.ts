import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { AuthService } from '../services/auth.service';

// Interceptor funcional — forma moderna do Angular 15+
// É executado automaticamente em TODA requisição HTTP que o Angular fizer
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);
  const token = authService.getToken();

  // Se não há token (usuário não logado), envia a requisição sem modificar
  // Isso permite que /auth/login e /auth/register funcionem normalmente
  if (!token) {
    return next(req);
  }

  // req é imutável no Angular — não podemos modificá-lo diretamente
  // .clone() cria uma cópia com as alterações que especificamos
  const authReq = req.clone({
    headers: req.headers.set('Authorization', `Bearer ${token}`),
  });

  // Equivalente ao que você fazia no Postman:
  // Headers → Authorization → Bearer eyJhbGciOiJIUzI1Ni...
  return next(authReq);
};