# Integração Frontend + Backend: Autenticação com JWT

> Documento de aprendizado — explica cada decisão de código feita na implementação
> do AuthService e da LoginPage no Angular 21.

---

## Índice

1. [O problema que estamos resolvendo](#1-o-problema-que-estamos-resolvendo)
2. [Como JWT funciona na prática](#2-como-jwt-funciona-na-prática)
3. [Por que separar em pastas?](#3-por-que-separar-em-pastas)
4. [app.config.ts — o ponto de entrada da aplicação](#4-appconfigts--o-ponto-de-entrada-da-aplicação)
5. [app.routes.ts — o mapa das páginas](#5-approutests--o-mapa-das-páginas)
6. [app.ts e app.html — o container raiz](#6-appts-e-apphtml--o-container-raiz)
7. [AuthService — a camada de comunicação com o backend](#7-authservice--a-camada-de-comunicação-com-o-backend)
8. [LoginPage — o componente de tela](#8-loginpage--o-componente-de-tela)
9. [RegisterPage — o componente de cadastro](#9-registerpage--o-componente-de-cadastro)
10. [HomePage — por que foi criada?](#10-homepage--por-que-foi-criada)
11. [AuthGuard — porteiro das rotas protegidas](#11-authguard--porteiro-das-rotas-protegidas)
12. [AuthInterceptor — o carimbo automático nas requisições](#12-authinterceptor--o-carimbo-automático-nas-requisições)
13. [app.routes.ts revisitado — rotas públicas, protegidas e o loop infinito](#13-approutests-revisitado--rotas-públicas-protegidas-e-o-loop-infinito)
14. [DashboardPage — estado de upload, drag & drop e resultados](#14-dashboardpage--estado-de-upload-drag--drop-e-resultados)
15. [Como tudo se conecta — visão final](#15-como-tudo-se-conecta--visão-final)

---

## 1. O problema que estamos resolvendo

Antes dessa implementação, o projeto tinha um backend Spring Boot com autenticação JWT
funcionando, mas o frontend Angular não sabia nada disso. Para testar o login, era preciso
usar o Postman manualmente.

O objetivo é fazer o Angular fazer exatamente o que o Postman fazia:
enviar uma requisição HTTP ao backend, receber o token JWT e guardar esse token
para usar nas próximas requisições.

---

## 2. Como JWT funciona na prática

JWT (JSON Web Token) é um sistema de autenticação sem estado (*stateless*).
Isso significa que o servidor **não guarda sessão** — ele só verifica se o token é válido.

O fluxo completo é:

```
1. Usuário digita email + senha no formulário
        ↓
2. Angular envia POST /auth/login  { login: "...", password: "..." }
        ↓
3. Spring Boot valida as credenciais
        ↓
4. Spring Boot retorna { token: "eyJhbGciOiJIUzI1Ni..." }
        ↓
5. Angular salva esse token no localStorage do navegador
        ↓
6. Em toda requisição futura, Angular envia o header:
   Authorization: Bearer eyJhbGciOiJIUzI1Ni...
        ↓
7. Spring Boot lê o token, valida, e libera ou bloqueia o acesso
```

No Postman você fazia os passos 2 e 6 manualmente.
O código que escrevemos automatiza isso.

---

## 3. Por que separar em pastas?

A estrutura que adotamos é uma convenção bem estabelecida em projetos Angular:

```
src/app/
├── components/    → Peças visuais reutilizáveis (botão, card, upload zone)
├── pages/         → Telas completas que o router exibe
│   ├── home/
│   └── login/
├── services/      → Lógica de negócio e comunicação com APIs
├── guards/        → (próximo passo) Proteção de rotas
└── interceptors/  → (próximo passo) Modificação automática de requisições
```

**Por que não colocar tudo junto?**

Imagine que você tem 10 telas e cada uma faz chamadas ao backend.
Se a URL do backend mudar, você teria que alterar 10 arquivos.
Com o `AuthService` centralizado, você altera em um lugar só.

Essa separação é chamada de **Separation of Concerns** (separação de responsabilidades)
— cada arquivo tem uma única razão para existir e uma única razão para mudar.

Analogia com o backend Java que você já conhece:

| Frontend Angular   | Backend Spring Boot       |
|--------------------|---------------------------|
| `components/`      | Templates / Views         |
| `pages/`           | Sem equivalente direto    |
| `services/`        | `@Service`                |
| `guards/`          | `SecurityFilter`          |
| `interceptors/`    | `SecurityFilter`          |

---

## 4. app.config.ts — o ponto de entrada da aplicação

```typescript
import { provideHttpClient } from '@angular/common/http';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    provideHttpClient(), // ← linha adicionada
  ]
};
```

**O que é `providers`?**

É onde você registra serviços e funcionalidades globais na aplicação.
Pense nisso como o equivalente ao `@Bean` do Spring — você está dizendo:
"essa funcionalidade existe e pode ser injetada em qualquer lugar".

**Por que `provideHttpClient()` precisou ser adicionado?**

O `HttpClient` (que faz as requisições HTTP) não vem ativo por padrão.
Você precisa registrá-lo aqui uma vez. Sem isso, ao tentar injetar o `HttpClient`
no `AuthService`, a aplicação quebraria com erro de injeção de dependência.

---

## 5. app.routes.ts — o mapa das páginas

```typescript
export const routes: Routes = [
  { path: '', component: Home },
  { path: 'login', component: Login },
];
```

**O que é roteamento no Angular?**

Roteamento é o sistema que decide qual componente exibir dependendo da URL.
Quando o usuário acessa `localhost:4200/login`, o Angular olha esse arquivo,
encontra `path: 'login'` e renderiza o componente `Login`.

**Por que `path: ''` para a home?**

`''` (string vazia) representa a raiz — `localhost:4200/`.
É a rota padrão, o que o usuário vê ao entrar no site.

**Como funciona o `component:`?**

Você passa a classe do componente diretamente.
O Angular instancia esse componente e o coloca dentro do `<router-outlet>`.

---

## 6. app.ts e app.html — o container raiz

**Antes:**
```typescript
// app.ts tinha todos os imports dos components
imports: [RouterOutlet, Header, Features, FileUploadZone, ResultsPreview]
```

**Depois:**
```typescript
// app.ts ficou limpo
imports: [RouterOutlet]
```

**Por que isso mudou?**

Antes, o `App` era responsável por montar toda a tela diretamente.
Agora ele é apenas um **container** — uma casca que deixa o router decidir
o que mostrar dentro dele.

O `<router-outlet>` no `app.html` é o buraco onde o Angular injeta a página atual:

```html
<!-- app.html -->
<router-outlet></router-outlet>
```

Quando a URL é `/`, o Angular coloca o `HomeComponent` ali dentro.
Quando a URL é `/login`, coloca o `LoginComponent`.
O `App` em si nunca muda — só o conteúdo do `<router-outlet>` muda.

---

## 7. AuthService — a camada de comunicação com o backend

```typescript
@Injectable({
  providedIn: 'root',
})
export class AuthService {
  private http = inject(HttpClient);
  private apiUrl = 'http://localhost:8080/auth';
  private tokenKey = 'auth_token';
  ...
}
```

### `@Injectable({ providedIn: 'root' })`

Esse decorator faz duas coisas:
1. Marca a classe como **injetável** — ela pode ser recebida como dependência
2. `providedIn: 'root'` cria uma única instância compartilhada por toda a aplicação (singleton)

Se dois componentes injetarem o `AuthService`, os dois recebem **a mesma instância**.
Isso é importante: significa que se o usuário fizer login em qualquer tela,
o token estará disponível para todos os outros componentes automaticamente.

### `inject(HttpClient)`

Essa é a forma moderna de injeção de dependência no Angular 21.
O equivalente mais antigo seria via construtor:

```typescript
// Forma antiga (ainda funciona, mas inject() é preferido no Angular 17+)
constructor(private http: HttpClient) {}

// Forma moderna
private http = inject(HttpClient);
```

Ambas fazem a mesma coisa: pedem ao Angular para fornecer uma instância do `HttpClient`.

### `private apiUrl` e `private tokenKey`

São constantes privadas. O motivo de usar `private`:
- Evita que outros arquivos acessem ou modifiquem esses valores diretamente
- Se a URL mudar, você altera em um único lugar
- `auth_token` é a chave usada para salvar/ler do `localStorage`

### O método `login()`

```typescript
login(login: string, password: string) {
  return this.http
    .post<LoginResponse>(`${this.apiUrl}/login`, { login, password })
    .pipe(
      tap((response) => {
        localStorage.setItem(this.tokenKey, response.token);
      })
    );
}
```

**`this.http.post<LoginResponse>(...)`**

Faz um HTTP POST para `/auth/login` com o body `{ login, password }`.
O `<LoginResponse>` é um *generic* do TypeScript — ele diz ao compilador
"a resposta desse POST tem o formato `{ token: string }`".
Isso é o espelho do `LoginResponseDTO` do seu backend Java.

**Por que retornar o Observable em vez de já chamar `.subscribe()`?**

O `HttpClient` retorna um **Observable** — um objeto que representa
uma operação assíncrona que ainda não aconteceu.
Ao não chamar `.subscribe()` aqui, você deixa o componente que chamou o método
decidir o que fazer quando a requisição terminar (sucesso ou erro).
Isso é responsabilidade do `LoginComponent`, não do `AuthService`.

**`.pipe(tap(...))`**

`pipe` é uma forma de encadear operações sobre o Observable.
`tap` permite executar um efeito colateral (salvar no localStorage)
**sem modificar** o valor que passa pelo Observable.
Funciona como um "espião" — vê o resultado e faz algo com ele,
mas não interfere no fluxo.

### `!!this.getToken()`

```typescript
isLoggedIn(): boolean {
  return !!this.getToken();
}
```

`!!` (dupla negação) converte qualquer valor para booleano:
- Se `getToken()` retornar `"eyJ..."` (string) → `!!` converte para `true`
- Se `getToken()` retornar `null` → `!!` converte para `false`

É um atalho idiomático em JavaScript/TypeScript para verificar existência.

---

## 8. LoginPage — o componente de tela

### login.ts

```typescript
export class Login {
  private authService = inject(AuthService);
  private router = inject(Router);

  login = '';
  password = '';

  isLoading = signal(false);
  errorMessage = signal('');
  ...
}
```

**`inject(AuthService)` e `inject(Router)`**

O componente pede ao Angular duas dependências:
- `AuthService` — para fazer o login
- `Router` — para navegar após o login ser bem-sucedido

O Angular já sabe como criar essas instâncias porque foram registradas
(o `AuthService` via `providedIn: 'root'`, o `Router` via `provideRouter(routes)` no config).

**`signal(false)` vs propriedade comum**

```typescript
// Propriedade comum
isLoading = false;

// Signal
isLoading = signal(false);
```

`signal` é o sistema de reatividade do Angular 17+.
A diferença prática: quando você chama `isLoading.set(true)`,
o Angular detecta automaticamente a mudança e atualiza o HTML.
Com uma propriedade comum, o Angular precisaria de uma estratégia de
detecção de mudanças mais pesada para perceber a alteração.

No HTML você lê com `isLoading()` (chamada de função), não `isLoading`:

```html
[disabled]="isLoading()"
{{ isLoading() ? 'Entrando...' : 'Entrar' }}
```

**O método `onSubmit()`**

```typescript
onSubmit() {
  this.isLoading.set(true);
  this.errorMessage.set('');

  this.authService.login(this.login, this.password).subscribe({
    next: () => {
      this.router.navigate(['/']);
    },
    error: () => {
      this.errorMessage.set('Email ou senha inválidos.');
      this.isLoading.set(false);
    },
  });
}
```

Aqui é onde o `.subscribe()` aparece. Dois callbacks:
- `next` — chamado quando a requisição retorna com sucesso (HTTP 200)
- `error` — chamado quando o backend retorna erro (HTTP 401, 403, etc.)

Note que `isLoading.set(false)` só é chamado no `error`.
No `next`, o usuário é redirecionado — a tela de login some, então não importa.

### login.html

```html
<form (ngSubmit)="onSubmit()" #form="ngForm">
```

**`(ngSubmit)`** — binding de evento. Quando o formulário é submetido
(botão clicado ou Enter pressionado), chama `onSubmit()` no componente.
O `()` ao redor indica que é um evento (output), não um valor (input).

**`[(ngModel)]="login"`** — *two-way binding* (ligação bidirecional).
O `[...]` lê o valor do componente para o HTML.
O `(...)` escreve o que o usuário digita de volta no componente.
Junto `[(...)]` sincroniza os dois lados em tempo real.

```html
@if (errorMessage()) {
  <p class="text-sm text-red-500 mb-4">{{ errorMessage() }}</p>
}
```

`@if` é a nova sintaxe de controle de fluxo do Angular 17+.
Só renderiza o parágrafo de erro quando `errorMessage()` não for string vazia.
`{{ }}` é interpolação — imprime o valor da expressão dentro do HTML.

---

## 9. RegisterPage — o componente de cadastro

### O contrato com o backend

O endpoint `POST /auth/register` no Spring Boot espera este body:

```json
{ "login": "user@email.com", "password": "123456", "role": "USER" }
```

O campo `role` vem de um enum Java chamado `UserRole`:

```java
public enum UserRole {
    ADMIN("admin"),
    USER("user");
}
```

No TypeScript, espelhamos isso com um **union type**:

```typescript
// services/auth.service.ts
export type UserRole = 'ADMIN' | 'USER';
```

**Por que `type` e não `enum` do TypeScript?**

TypeScript tem `enum`, mas em Angular é mais comum usar union types para valores simples.
A razão prática: o JSON enviado ao backend precisa ser a string `"USER"` ou `"ADMIN"`.
Com um union type, o TypeScript garante isso em tempo de compilação sem nenhum custo em runtime.

```typescript
// ERRADO — TypeScript não reclamaria, mas o backend rejeitaria
role = 'administrador';

// CORRETO — TypeScript só deixa atribuir 'ADMIN' ou 'USER'
role: UserRole = 'USER';
```

### O método `register()` no AuthService

```typescript
register(login: string, password: string, role: UserRole) {
  return this.http.post(`${this.apiUrl}/register`, { login, password, role });
}
```

Compare com o `login()`:

| `login()` | `register()` |
|-----------|-------------|
| `post<LoginResponse>(...)` | `post(...)` sem generic |
| Usa `.pipe(tap(...))` para salvar token | Sem efeito colateral |
| Sucesso retorna `{ token: string }` | Sucesso retorna `200 OK` sem body |

Por isso o `register()` é mais simples — o backend só confirma que deu certo (HTTP 200),
não devolve nenhum dado. Não há token ainda: o usuário precisa fazer login depois do cadastro.

### Validação no frontend antes de chamar o backend

```typescript
onSubmit() {
  if (this.password !== this.confirmPassword) {
    this.errorMessage.set('As senhas não coincidem.');
    return; // ← interrompe o método aqui, sem chamar o backend
  }
  // ...
}
```

**Por que validar no frontend se o backend também valida?**

Essa validação específica (senha ≠ confirmação) **não existe no backend** — o backend só recebe
`password` e salva. Verificar as duas senhas é responsabilidade do frontend.

Além disso: mesmo para validações que existem dos dois lados, validar no frontend evita
uma requisição HTTP desnecessária. Menos tráfego de rede, resposta mais rápida para o usuário.

A regra geral é: **nunca confie só no frontend** (o usuário pode burlar),
**mas use o frontend para melhorar a experiência** (feedback imediato).

### Navegação após registro bem-sucedido

```typescript
next: () => {
  this.router.navigate(['/login']);
}
```

Após o cadastro, redirecionamos para `/login` em vez de `/`.
Isso é intencional: o registro **não faz login automático**.
O usuário precisa entrar com as credenciais recém-criadas.

Se quiséssemos login automático, chamaríamos `authService.login()` depois do registro.
Mas isso adiciona complexidade — para este projeto, o fluxo simples é o correto.

### `RouterLink` — navegação declarativa no template

```typescript
// register.ts e login.ts
imports: [FormsModule, RouterLink]
```

```html
<!-- register.html -->
<a routerLink="/login">Entrar</a>

<!-- login.html -->
<a routerLink="/register">Criar conta</a>
```

`RouterLink` é uma diretiva do Angular que transforma um `<a>` comum em um link
que usa o roteamento do Angular em vez de recarregar a página inteira.

| `<a href="/login">` | `<a routerLink="/login">` |
|---|---|
| Recarrega a página (requisição HTTP nova) | Troca o componente sem recarregar |
| Perde o estado da aplicação | Mantém o estado (localStorage, etc.) |
| Comportamento padrão do HTML | Comportamento SPA (Single Page Application) |

Para usar `routerLink` no template, o componente precisa importar `RouterLink`.
É por isso que adicionamos ao array `imports` do `@Component`.

---

## 10. HomePage — por que foi criada?

Antes da refatoração, o `App` (componente raiz) montava toda a tela diretamente.
Isso era um problema porque o `App` não pode ser substituído pelo router —
ele é sempre renderizado.

**O problema:** se o `App` tem o conteúdo da home fixo, o usuário veria
a home mesmo na tela de login, porque o `App` nunca some.

**A solução:** o `App` vira um container vazio com só `<router-outlet>`.
O conteúdo da home vai para `pages/home/home`, que o router renderiza
quando a URL é `/`.

```
Antes:                    Depois:
App                       App
└── Header                └── <router-outlet>
└── FileUploadZone                ↕ troca dinamicamente
└── Features              ├── Home (URL: /)
└── ResultsPreview        │   ├── Header
                          │   ├── FileUploadZone
                          │   ├── Features
                          │   └── ResultsPreview
                          └── Login (URL: /login)
```

---

## 11. AuthGuard — porteiro das rotas protegidas

O Guard é executado pelo Angular **antes** de renderizar qualquer componente de uma rota.
É a última barreira antes do usuário ver o conteúdo protegido.

```typescript
// guards/auth.guard.ts
export const authGuard: CanActivateFn = () => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (authService.isLoggedIn()) {
    return true;
  }

  return router.createUrlTree(['/login']);
};
```

### Por que uma função e não uma classe?

Antes do Angular 15, guards eram obrigatoriamente classes implementando interfaces:

```typescript
// Forma ANTIGA (ainda funciona, mas não é mais recomendada)
@Injectable()
export class AuthGuard implements CanActivate {
  canActivate(): boolean { ... }
}
```

A partir do Angular 15, guards funcionais foram introduzidos.
São mais simples: uma função pura, sem `@Injectable`, sem classe.
O resultado é o mesmo, mas com menos código e mais fácil de testar.

### `CanActivateFn`

É o tipo que define a assinatura da função guard.
O Angular exige esse tipo para saber que sua função é um guard válido.
Sem ele, o TypeScript não deixaria você usar a função no `canActivate:` das rotas.

### Por que `inject()` funciona aqui?

`inject()` normalmente só funciona dentro de um contexto de injeção do Angular
(dentro de classes com `@Injectable`, `@Component`, etc.).
Funções normais não têm esse contexto.

Mas o Angular executa o guard **dentro** do seu sistema de injeção,
então `inject()` funciona normalmente — o Angular garante isso.

### `createUrlTree` vs `router.navigate()`

```typescript
// Forma menos recomendada dentro de guards
router.navigate(['/login']);
return false;

// Forma correta
return router.createUrlTree(['/login']);
```

A diferença é sutil mas importante:
- `router.navigate()` + `return false`: dispara uma nova navegação e cancela a atual de forma abrupta
- `createUrlTree`: retorna um objeto `UrlTree` que o próprio router usa para fazer o redirecionamento de forma limpa, integrada ao ciclo de navegação

Pense no `UrlTree` como uma instrução: "não vá para onde estava indo, vá para cá".
O router entende essa instrução nativamente.

### Como o guard é conectado à rota

```typescript
// app.routes.ts
{ path: '', component: Home, canActivate: [authGuard] }
```

`canActivate` recebe um array — você pode ter múltiplos guards numa mesma rota.
Todos precisam retornar `true` para o componente ser renderizado.
Se qualquer um retornar `false` ou um `UrlTree`, a navegação é interrompida.

---

## 12. AuthInterceptor — o carimbo automático nas requisições

O Interceptor é um middleware HTTP — fica sentado entre o seu código
e a rede, capturando toda requisição antes de ela sair e toda resposta antes de chegar.

```typescript
// interceptors/auth.interceptor.ts
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);
  const token = authService.getToken();

  if (!token) {
    return next(req);
  }

  const authReq = req.clone({
    headers: req.headers.set('Authorization', `Bearer ${token}`),
  });

  return next(authReq);
};
```

### `HttpInterceptorFn`

Assim como `CanActivateFn` para guards, `HttpInterceptorFn` é o tipo
que define a assinatura de um interceptor funcional.

A função recebe dois parâmetros:
- `req` — a requisição original que está saindo
- `next` — uma função que representa "continue e envie essa requisição"

### Por que `req` é imutável?

O Angular trata requisições HTTP como objetos imutáveis.
Isso evita bugs difíceis de rastrear onde um código modifica a requisição
e outro código fica confuso com o estado alterado.

Por isso existe o `.clone()`:

```typescript
// ERRADO — TypeScript não deixa fazer isso
req.headers.set('Authorization', `Bearer ${token}`);

// CORRETO — cria uma cópia com a alteração
const authReq = req.clone({
  headers: req.headers.set('Authorization', `Bearer ${token}`),
});
```

`req.clone()` copia tudo da requisição original e aplica apenas as alterações que você passar.
O objeto original permanece intacto.

### `return next(req)` vs `return next(authReq)`

`next(req)` significa: "passa a requisição original, sem modificação, para a próxima etapa".
`next(authReq)` significa: "passa a requisição modificada (com o header) para a próxima etapa".

Quando não há token (usuário não logado), usamos `next(req)` — sem alterar nada.
Isso é necessário para que rotas públicas como `/auth/login` continuem funcionando.
Se você tentasse adicionar `Bearer null` no header, o backend rejeitaria até o login.

### Como o interceptor é conectado à aplicação

```typescript
// app.config.ts
provideHttpClient(withInterceptors([authInterceptor]))
```

`withInterceptors([...])` é uma função que ativa interceptors funcionais.
O array aceita múltiplos interceptors — eles são executados em ordem.

Antes do Angular 15, interceptors eram classes registradas diferente:
```typescript
// Forma ANTIGA
{ provide: HTTP_INTERCEPTORS, useClass: AuthInterceptor, multi: true }
```
A forma nova com `withInterceptors` é mais explícita e não precisa do `multi: true`.

---

## 13. app.routes.ts revisitado — rotas públicas, protegidas e o loop infinito

### O problema que surgiu

Quando o guard foi alterado para redirecionar para `/` em vez de `/login`,
a configuração de rotas antiga criava um **loop infinito**:

```
Usuário sem token acessa /dashboard
      ↓
authGuard executa → isLoggedIn() → false
      ↓
Retorna router.createUrlTree(['/'])     ← redireciona para /
      ↓
Router tenta renderizar /
      ↓
canActivate: [authGuard] executa de novo → false
      ↓
Redireciona para / de novo → loop infinito 🔴
```

A causa: a rota `/` tinha `canActivate: [authGuard]`, mas o guard redirecionava
exatamente para `/`. O router tentava acessar a rota, o guard bloqueava,
o guard mandava de volta para a mesma rota, e assim por diante.

### A solução: separar claramente rotas públicas de protegidas

```typescript
export const routes: Routes = [
  // ✅ Rota pública — sem guard, qualquer um acessa
  { path: '', component: Home },

  // ✅ Rotas públicas de autenticação
  { path: 'login', component: Login },
  { path: 'register', component: Register },

  // ✅ Rota protegida — guard protege SOMENTE essa rota
  { path: 'dashboard', component: Dashboard, canActivate: [authGuard] },
];
```

Agora o fluxo sem loop é:

```
Usuário sem token acessa /dashboard
      ↓
authGuard executa → false
      ↓
Redireciona para /     ← landing page, sem guard
      ↓
Home é renderizada normalmente ✅
```

### Por que o guard redireciona para `/` e não para `/login`?

As duas abordagens são válidas — a escolha depende da experiência que você quer:

| Redirecionar para `/login` | Redirecionar para `/` |
|---|---|
| Fluxo direto: usuário vai logo à tela de login | Fluxo com landing page: usuário vê a apresentação do produto |
| Comum em sistemas internos (dashboards corporativos) | Comum em produtos com landing page pública |
| Menos cliques para autenticar | Mais contexto sobre o produto antes de pedir login |

Neste projeto, o redirecionamento para `/` foi escolhido porque o produto
tem uma landing page que explica o que ele faz — faz sentido o usuário
ver essa página antes de ser direcionado ao login.

### Hierarquia de proteção

A estrutura final ficou assim:

```
/           → público  (landing page)
/login      → público  (autenticação)
/register   → público  (cadastro)
/dashboard  → protegido (authGuard verifica token)
```

Regra simples para definir se uma rota precisa de guard:
> "Essa página tem informações ou ações que dependem de o usuário estar logado?"
> Se sim → `canActivate: [authGuard]`. Se não → sem guard.

---

## 14. DashboardPage — estado de upload, drag & drop e resultados

### Visão geral da página

O Dashboard é a tela principal do produto — onde o usuário logado
envia documentos financeiros para análise. Ela tem três estados:

```
Estado 1: vazio
─────────────────────────────
[Zona de upload — aguardando arquivo]

Estado 2: arquivo selecionado
─────────────────────────────
[Zona de upload]
[Lista: fatura.pdf  1.2 MB  ✕]
[Botão: Analisar 1 arquivo]

Estado 3: análise concluída
─────────────────────────────
[Zona de upload]
[Lista de arquivos]
[Card de resultado: Valor Total / Vencimento / Status / Parcelas]
```

### Interfaces TypeScript — espelhando o backend

```typescript
export interface FinancialData {
  label: string;
  value: string;
  color: string;
}

export interface AnalysisResult {
  fileName: string;
  analysisTimeMs: number;
  data: FinancialData[];
}
```

**Por que definir interfaces antes de ter o backend pronto?**

Trabalhar com interfaces é um contrato: você define a forma dos dados
que espera receber, e o TypeScript garante que o código que usa esses dados
está correto. Quando o backend ficar pronto, você adapta apenas a interface
— não o código inteiro.

É o equivalente a definir um DTO no Java antes de implementar o endpoint.

### Estado com `signal` — os três sinais do Dashboard

```typescript
selectedFiles = signal<File[]>([]);
isAnalyzing  = signal(false);
result       = signal<AnalysisResult | null>(null);
```

**`File[]`** — `File` é um tipo nativo do browser (não é do Angular nem do TypeScript).
Representa um arquivo real do sistema operacional. Tem `.name`, `.size`, `.type`, `.lastModified`.

**`signal<AnalysisResult | null>(null)`** — o `null` inicial é intencional.
No template, `result()` retorna `null` ou um objeto.
O `@if (result())` no HTML só renderiza o card de resultado quando há um valor.
Isso é mais explícito do que usar um boolean separado como `showResults = false`.

### Drag & Drop — os três eventos necessários

Para implementar drag & drop no browser, três eventos são obrigatórios:

```typescript
// 1. Enquanto o arquivo está sendo arrastado SOBRE a zona
onDragOver(event: DragEvent) {
  event.preventDefault(); // ← SEM ISSO, o drop não funciona
  this.isDragging.set(true);
}

// 2. Quando o arquivo SAI da zona sem ser solto
onDragLeave() {
  this.isDragging.set(false);
}

// 3. Quando o arquivo é SOLTO na zona
onDrop(event: DragEvent) {
  event.preventDefault(); // ← também necessário aqui
  this.isDragging.set(false);
  const dropped = event.dataTransfer?.files;
  if (dropped && dropped.length > 0) {
    this.addFiles(Array.from(dropped));
  }
}
```

**Por que `event.preventDefault()` no `dragover`?**

O comportamento padrão do browser ao soltar um arquivo é **abrir o arquivo**
(ou navegar para ele). `preventDefault()` cancela esse comportamento padrão,
permitindo que o seu código trate o arquivo ao invés do browser.
Sem isso, o evento `drop` nunca dispara.

**`event.dataTransfer?.files`**

`dataTransfer` é o objeto que carrega os dados arrastados.
O `?` é optional chaining — `dataTransfer` pode ser `null` em alguns contextos
(ex: drag de texto em vez de arquivo), então acessamos com segurança.
`Array.from()` converte o `FileList` (estrutura especial do browser) para um array comum.

### Template Reference Variable — `#fileInput`

```html
<!-- Input oculto com id referenciado pelo template -->
<input #fileInput type="file" class="hidden" (change)="onFileInputChange($event)" />

<!-- Clique na zona chama o clique do input -->
<div (click)="fileInput.click()">...</div>
```

`#fileInput` é uma **template reference variable**.
Ela cria uma referência ao elemento HTML que pode ser usada em qualquer lugar
do mesmo template — sem precisar de `document.getElementById()` ou `@ViewChild`.

Por que fazer isso? O `<input type="file">` nativo do browser tem um visual feio
que não dá para customizar com CSS. A solução padrão é:
1. Esconder o input com `class="hidden"`
2. Criar uma zona visível e bonita
3. Fazer a zona chamar `.click()` no input oculto quando clicada

### Deduplicação de arquivos

```typescript
private addFiles(files: File[]) {
  const current = this.selectedFiles();
  const currentNames = new Set(current.map((f) => f.name));
  const newFiles = files.filter((f) => !currentNames.has(f.name));
  this.selectedFiles.set([...current, ...newFiles]);
}
```

**`Set`** é uma estrutura de dados JavaScript que armazena apenas valores únicos
e permite verificar existência em O(1) (tempo constante).

Por que usar `Set` em vez de `Array.includes()`?

```typescript
// Com array: para cada arquivo novo, percorre o array inteiro
const alreadyExists = current.some((f) => f.name === newFile.name); // O(n)

// Com Set: verificação instantânea independente do tamanho
const alreadyExists = currentNames.has(newFile.name); // O(1)
```

Para poucos arquivos a diferença é imperceptível, mas `Set` é o idioma
correto para esse tipo de verificação — comunica a intenção claramente.

### `pointer-events-none` na zona de drop

```html
<div (click)="fileInput.click()" ...>
  <div class="pointer-events-none">
    <!-- ícone e texto -->
  </div>
</div>
```

Sem `pointer-events-none`, o texto e o ícone dentro da zona capturam os eventos
de mouse — e o evento `dragover` seria disparado neles, não na zona.
Isso causaria o `dragleave` ser ativado quando o mouse passasse sobre o texto,
fazendo o visual piscar. `pointer-events-none` torna os elementos filhos
"invisíveis" para o mouse, delegando todos os eventos ao container pai.

### O `TODO` intencional em `analyzeFiles()`

```typescript
analyzeFiles() {
  this.isAnalyzing.set(true);

  // Simulação de chamada assíncrona ao backend (será substituída por HttpClient)
  setTimeout(() => {
    this.result.set({ ... dados placeholder ... });
    this.isAnalyzing.set(false);
  }, 1500);
}
```

O `setTimeout` simula o delay de uma requisição HTTP real.
Isso serve para dois propósitos:
1. Permite testar e validar o frontend (estados de loading, resultado) sem o backend pronto
2. Documenta a intenção: o `TODO` e o comentário deixam claro onde a integração vai acontecer

Quando o endpoint de análise estiver pronto, a substituição será cirúrgica:

```typescript
// Substituição futura:
this.http.post<AnalysisResult>('/api/analyze', formData).subscribe({
  next: (result) => {
    this.result.set(result);
    this.isAnalyzing.set(false);
  },
  error: () => { ... }
});
```

### `result()!` — o non-null assertion operator

```html
@if (result()) {
  <p>{{ result()!.fileName }}</p>
}
```

O `!` após `result()` é o **non-null assertion operator** do TypeScript.
Ele diz ao compilador: "eu sei que esse valor não é null aqui".

O `@if (result())` já garante que `result()` não é `null` dentro do bloco.
Mas o TypeScript não consegue inferir isso — ele ainda acha que poderia ser `null`.
O `!` resolve essa tensão sem precisar de um cast explícito.

**Quando usar `!`:** somente quando você tem certeza lógica que o valor não é null/undefined.
Usá-lo sem essa certeza mascara bugs — o programa explode em runtime em vez de em compile time.

---

## 15. Como tudo se conecta — visão final

Com todos os arquivos implementados, o fluxo completo ficou assim:

```
FLUXO 1 — Usuário não logado tenta acessar a landing page
──────────────────────────────────────────────────────────
localhost:4200/
      ↓
Router consulta app.routes.ts
      ↓
Encontra { path: '', component: Home }  ← sem guard
      ↓
Home (landing page) é renderizada normalmente ✅


FLUXO 2 — Usuário não logado tenta acessar o dashboard
────────────────────────────────────────────────────────
localhost:4200/dashboard
      ↓
Router encontra canActivate: [authGuard]
      ↓
authGuard executa → isLoggedIn() → false (sem token)
      ↓
Retorna router.createUrlTree(['/'])
      ↓
Landing page é renderizada — dashboard NUNCA é exibido ✅


FLUXO 3 — Usuário se cadastra
───────────────────────────────
RegisterPage: preenche email + senha → clica Criar conta
      ↓
Validação frontend: senha === confirmação?
      ↓ (sim)
authService.register(login, password, 'USER')
      ↓
POST http://localhost:8080/auth/register
      ↓
Spring Boot → cria usuário no banco → 200 OK (body vazio)
      ↓
Angular: responseType: 'text' → não tenta parsear JSON vazio
      ↓
next() → router.navigate(['/login']) ✅


FLUXO 4 — Usuário faz login
─────────────────────────────
LoginPage: preenche email + senha → clica Entrar
      ↓
authService.login(login, password)
      ↓
POST http://localhost:8080/auth/login
      ↓
Spring Boot valida → retorna { token: "eyJ..." }
      ↓
tap() salva token no localStorage
      ↓
next() → router.navigate(['/dashboard'])
      ↓
authGuard executa → isLoggedIn() → true → Dashboard renderizado ✅


FLUXO 5 — Usuário logado analisa um documento
───────────────────────────────────────────────
Dashboard: arrasta arquivo ou clica na zona de upload
      ↓
onDrop() / onFileInputChange() → addFiles() com deduplicação
      ↓
selectedFiles signal atualizado → lista de arquivos aparece no template
      ↓
Usuário clica "Analisar"
      ↓
analyzeFiles() → isAnalyzing.set(true) → spinner aparece
      ↓
[futuro] POST /api/analyze com o arquivo (FormData)
      ↓
Backend processa → retorna AnalysisResult
      ↓
result.set(analysisResult) → card de resultado aparece no template ✅


FLUXO 6 — Usuário logado faz qualquer requisição HTTP
──────────────────────────────────────────────────────
HttpClient dispara uma requisição
      ↓
authInterceptor captura
      ↓
authService.getToken() → "eyJ..."
      ↓
req.clone() adiciona Authorization: Bearer eyJ...
      ↓
Spring Boot SecurityFilter valida → acesso liberado ✅


FLUXO 7 — Usuário faz logout
──────────────────────────────
Clica em "Sair" no header do Dashboard
      ↓
authService.logout() → localStorage.removeItem('auth_token')
      ↓
router.navigate(['/login'])
      ↓
Se tentar acessar /dashboard → authGuard bloqueia → redireciona para / ✅
```

### Mapa de responsabilidades

| Arquivo | Quando é executado | O que faz |
|---|---|---|
| `auth.service.ts` | Quando chamado explicitamente | Faz login/registro/logout, guarda/lê/remove token |
| `auth.guard.ts` | Antes de cada navegação para rota protegida | Decide se a rota pode ser acessada |
| `auth.interceptor.ts` | Em toda requisição HTTP | Injeta o token no header automaticamente |
| `app.routes.ts` | Na inicialização + a cada mudança de URL | Mapeia URLs para componentes e guards |
| `app.config.ts` | Na inicialização da aplicação | Registra serviços globais |
| `dashboard.ts` | Quando o usuário acessa `/dashboard` | Gerencia upload de arquivo e exibe resultado |

### Mapa das rotas

```
/           → Home (público)      → landing page do produto
/login      → Login (público)     → autenticação
/register   → Register (público)  → cadastro de novo usuário
/dashboard  → Dashboard (🔒 guard) → upload e análise de documentos
```

---

*Este documento é um recurso de aprendizado atualizado durante o desenvolvimento.*
*Última atualização: implementação do Dashboard, correção do loop infinito no guard e separação de rotas públicas/protegidas.*