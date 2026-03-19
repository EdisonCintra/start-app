import { Routes } from '@angular/router';
import { Login } from './pages/login/login';
import { Register } from './pages/register/register';
import { Home } from './pages/home/home';
import { Dashboard } from './pages/dashboard/dashboard';
import { authGuard } from './guards/auth.guard';

export const routes: Routes = [
  // Rota pública — landing page, qualquer um pode acessar
  { path: '', component: Home },

  // Rotas públicas de autenticação
  { path: 'login', component: Login },
  { path: 'register', component: Register },

  // Rota protegida — o guard verifica o token antes de renderizar
  { path: 'dashboard', component: Dashboard, canActivate: [authGuard] }
];
