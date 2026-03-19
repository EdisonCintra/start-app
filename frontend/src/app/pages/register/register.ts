import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService, UserRole } from '../../services/auth.service';

@Component({
  selector: 'app-register',
  imports: [FormsModule, RouterLink],
  templateUrl: './register.html',
  styleUrl: './register.css',
})
export class Register {
  private authService = inject(AuthService);
  private router = inject(Router);

  // Campos do formulário — espelham os campos do RegisterDTO do backend
  login = '';
  password = '';
  confirmPassword = '';
  // Role padrão: USER — a maioria dos cadastros é de usuário comum
  role: UserRole = 'USER';

  isLoading = signal(false);
  errorMessage = signal('');
  successMessage = signal('');

  onSubmit() {
    // Validação no frontend: evita uma ida desnecessária ao backend
    if (!this.login.trim() || !this.password.trim() || !this.confirmPassword.trim()) {
      this.errorMessage.set('Preencha todos os campos.');
      return;
    }

    if (this.password !== this.confirmPassword) {
      this.errorMessage.set('As senhas não coincidem.');
      return;
    }

    this.isLoading.set(true);
    this.errorMessage.set('');

    // Chama o AuthService que faz POST /auth/register no backend
    this.authService.register(this.login, this.password, this.role).subscribe({
      next: () => {
        // Registro bem-sucedido: redireciona para o login
        this.router.navigate(['/login']);
      },
      error: () => {
        // O backend retorna 400 Bad Request quando o email já está cadastrado
        this.errorMessage.set('Este email já está cadastrado.');
        this.isLoading.set(false);
      },
    });
  }
}
