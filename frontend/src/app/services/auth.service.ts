import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { tap } from 'rxjs';
import { environment } from '../../environments/environment';

// Espelha o LoginResponseDTO do backend
interface LoginResponse {
  token: string;
}

// Espelha o RegisterDTO do backend
// UserRole é o enum Java: 'ADMIN' | 'USER'
export type UserRole = 'ADMIN' | 'USER';

@Injectable({
  providedIn: 'root', // singleton: uma única instância para toda a aplicação
})
export class AuthService {
  private http = inject(HttpClient);
  private apiUrl = `${environment.apiUrl}/auth`;
  private tokenKey = 'auth_token';

  // Equivalente a: POST /auth/login com body { login, password }
  login(login: string, password: string) {
    return this.http
      .post<LoginResponse>(`${this.apiUrl}/login`, { login, password })
      .pipe(
        tap((response) => {
          // Ao receber o token do backend, salva no localStorage do navegador
          localStorage.setItem(this.tokenKey, response.token);
        })
      );
  }

  // Equivalente a: POST /auth/register com body { login, password, role }
  // responseType: 'text' porque o backend retorna 200 OK com body vazio.
  // Sem isso, o Angular tenta fazer JSON.parse("") → lança erro mesmo com sucesso.
  register(login: string, password: string, role: UserRole) {
    return this.http.post(`${this.apiUrl}/register`, { login, password, role }, { responseType: 'text' });
  }

  logout() {
    localStorage.removeItem(this.tokenKey);
  }

  getToken(): string | null {
    return localStorage.getItem(this.tokenKey);
  }

  isLoggedIn(): boolean {
    return !!this.getToken(); // retorna true se o token existir
  }
}
