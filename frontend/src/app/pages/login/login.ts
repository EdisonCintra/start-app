import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-login',
  imports: [FormsModule, RouterLink], // FormsModule habilita o [(ngModel)]; RouterLink habilita o routerLink no template
  templateUrl: './login.html',
  styleUrl: './login.css',
})
export class Login {
  private authService = inject(AuthService);
  private router = inject(Router);

  // Campos do formulário — ligados ao HTML via [(ngModel)]
  login = '';
  password = '';

  // Estado da tela
  isLoading = signal(false);
  errorMessage = signal('');

  onSubmit() {
    if (!this.login.trim() || !this.password.trim()) {
      this.errorMessage.set('Preencha todos os campos.');
      return;
    }

    this.isLoading.set(true);
    this.errorMessage.set('');

    // Chama o AuthService que faz POST /auth/login no backend
    this.authService.login(this.login, this.password).subscribe({
      next: () => {
        // Token já foi salvo no localStorage pelo AuthService
        // Redireciona para a home após login bem-sucedido
        this.router.navigate(['/dashboard']);
      },
      error: () => {
        this.errorMessage.set('Email ou senha inválidos.');
        this.isLoading.set(false);
      },
    });
  }
}
