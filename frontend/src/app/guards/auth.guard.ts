import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

// Executado automaticamente pelo router antes de renderizar qualquer rota protegida
export const authGuard: CanActivateFn = () => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (authService.isLoggedIn()) {
    // Token existe no localStorage → deixa o usuário passar
    return true;
  }

  // Token não existe → redireciona para /login sem renderizar nada
  return router.createUrlTree(['/']);
};
