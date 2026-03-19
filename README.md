# 💰 FinanceAI — Análise Financeira com OCR e Inteligência Artificial

Aplicação full-stack que extrai, processa e analisa documentos financeiros brasileiros (faturas de cartão, contas de energia, água, telefone e gás) usando **OCR**, **mascaramento de dados pessoais (PII)** e **IA generativa**.

**Status:** ✅ Produção | **Versão:** 1.0.0 | **Última Atualização:** Março 2026

🌐 **Frontend:** https://sublime-light-production-3490.up.railway.app
🔗 **API:** https://start-app-production-29a4.up.railway.app

---

## 📋 Índice Rápido

1. [Visão Geral](#-visão-geral)
2. [Stack Tecnológico](#-stack-tecnológico)
3. [Arquitetura](#-arquitetura)
4. [Pré-requisitos](#-pré-requisitos)
5. [Instalação](#-instalação)
6. [Variáveis de Ambiente](#-variáveis-de-ambiente)
7. [Executando](#-executando-a-aplicação)
8. [Autenticação](#-autenticação)
9. [Endpoints da API](#-endpoints-da-api)
   - [Auth](#auth)
   - [OCR & Análise](#ocr--análise)
   - [Insights (Histórico)](#insights-histórico)
10. [Formatos e Limites](#-formatos-e-limites)
11. [Mascaramento de PII](#-mascaramento-de-pii)
12. [Respostas de Erro](#-respostas-de-erro)
13. [Troubleshooting](#-troubleshooting)
14. [FAQ](#-faq)

---

## 👁️ Visão Geral

O FinanceAI permite que usuários façam upload de faturas e contas em formato de imagem ou PDF e recebam automaticamente:

✅ **Extração de texto** via OCR (Tesseract) ou parsing direto de PDF
✅ **Mascaramento automático** de dados sensíveis (CPF, CNPJ, cartão, e-mail, telefone)
✅ **Análise financeira** estruturada por IA (categorização de entradas e saídas)
✅ **Insights comportamentais** e conselhos práticos gerados pelo modelo LLaMA 3.3 70B
✅ **Histórico persistido** por usuário para consulta futura
✅ **Autenticação segura** com JWT

---

## 🛠️ Stack Tecnológico

| Camada | Tecnologia | Versão |
|--------|-----------|--------|
| **Frontend** | Angular | 19 |
| **Backend** | Spring Boot | 4.0.2 |
| **Linguagem** | Java | 21 |
| **Banco de Dados** | MySQL | 9.4 |
| **OCR** | Tesseract + Tess4J | 4.x |
| **Parsing PDF** | Apache PDFBox | — |
| **IA** | Groq API (LLaMA 3.3 70B) | — |
| **Autenticação** | JWT (JJWT) | — |
| **Deploy** | Railway (Docker) | — |

---

## 🏗️ Arquitetura

```
frontend/          → Angular 19 (SPA)
backend/           → Spring Boot 4.0.2 (API REST)
  ├── controller/  → Camada HTTP (AuthController, OcrController, InsightController)
  ├── service/     → Regras de negócio (OcrService, GroqService, PiiMaskingService...)
  ├── infrastructure/
  │   ├── dto/     → Objetos de transferência de dados
  │   ├── entitys/ → Entidades JPA (User, Insight)
  │   ├── repository/ → Spring Data JPA
  │   └── configuration/ → Security, CORS, JWT filter
  └── exception/   → Handlers globais de erro
```

**Fluxo de análise de documento:**

```
Upload (PNG/JPG/PDF)
    → Validação de formato e extensão
    → Extração de texto (PDFBox para PDFs digitais / Tesseract para imagens e PDFs escaneados)
    → Mascaramento de PII (CPF, CNPJ, RG, cartão, e-mail, telefone)
    → Validação de conteúdo mínimo (≥ 80 caracteres)
    → Envio à API Groq (LLaMA 3.3 70B) com prompt estruturado
    → Recálculo de totais no backend (a IA categoriza, Java soma)
    → Persistência no banco vinculada ao usuário
    → Retorno do FinancialAnalysisDTO
```

---

## ⚙️ Pré-requisitos

- [Java 21](https://adoptium.net/)
- [Maven 3.9+](https://maven.apache.org/download.cgi)
- [Node.js 20+](https://nodejs.org/) e [Angular CLI 19](https://angular.io/cli)
- [MySQL 8+](https://dev.mysql.com/downloads/mysql/)
- [Tesseract OCR 4.x](https://github.com/tesseract-ocr/tesseract) com dados do idioma português (`por`)
- [Git](https://git-scm.com/)
- Chave de API da [Groq](https://console.groq.com/)

---

## 🚀 Instalação

### 1. Clonar o Repositório

```bash
git clone https://github.com/EdisonCintra/start-app.git
cd start-app
```

### 2. Backend

```bash
cd backend
cp src/main/resources/application.properties.example src/main/resources/application.properties
# Configure as variáveis (veja a seção abaixo)
mvn clean install -DskipTests
```

### 3. Frontend

```bash
cd frontend
npm install
```

### 4. Tesseract OCR (local)

**Ubuntu/Debian:**
```bash
sudo apt-get install tesseract-ocr tesseract-ocr-por
```

**macOS:**
```bash
brew install tesseract tesseract-lang
```

**Windows:**
Baixe o instalador em https://github.com/UB-Mannheim/tesseract/wiki e adicione ao PATH.
Os dados do idioma português (`por.traineddata`) devem estar na pasta `tessdata`.

---

## 🔑 Variáveis de Ambiente

Configure no `application.properties` (local) ou nas variáveis do Railway (produção):

| Variável | Descrição | Exemplo |
|----------|-----------|---------|
| `SPRING_DATASOURCE_URL` | URL JDBC do banco | `jdbc:mysql://localhost:3306/financeai` |
| `SPRING_DATASOURCE_USERNAME` | Usuário do banco | `root` |
| `SPRING_DATASOURCE_PASSWORD` | Senha do banco | `senha123` |
| `JWT_SECRET` | Segredo para assinar tokens JWT | string longa e aleatória |
| `GROQ_API_KEY` | Chave da API Groq | `gsk_...` |
| `TESSERACT_PATH` | Caminho para a pasta `tessdata` | `/usr/share/tesseract-ocr/4.00/tessdata` |
| `TESSERACT_LANG` | Idioma do Tesseract | `por` |
| `PORT` | Porta do servidor (opcional) | `8080` |

> ⚠️ **Nunca commite credenciais reais.** Use variáveis de ambiente ou um `.env` ignorado pelo `.gitignore`.

---

## ▶️ Executando a Aplicação

### Backend

```bash
cd backend
mvn spring-boot:run
```

API disponível em `http://localhost:8080`.

### Frontend

```bash
cd frontend
ng serve
```

Frontend disponível em `http://localhost:4200`.

### Docker (produção)

```bash
cd backend
docker build -t financeai-backend .
docker run -p 8080:8080 \
  -e SPRING_DATASOURCE_URL=... \
  -e JWT_SECRET=... \
  -e GROQ_API_KEY=... \
  -e TESSERACT_PATH=/tessdata \
  -e TESSERACT_LANG=por \
  financeai-backend
```

---

## 🔐 Autenticação

A API usa **JWT (Bearer Token)**. Após o login, inclua o token em todas as requisições protegidas:

```
Authorization: Bearer <seu_token>
```

Rotas **públicas** (sem autenticação):
- `POST /auth/login`
- `POST /auth/register`

Rotas **protegidas** (requerem token): todas as demais.

Rota exclusiva para **ADMIN**:
- `POST /create`

---

## 📡 Endpoints da API

> Substitua `{HOST}` por `http://localhost:8080` (local) ou `https://start-app-production-29a4.up.railway.app` (produção).

---

### Auth

#### Registrar Usuário

- **Endpoint:** `POST /auth/register`
- **Autenticação:** Não requerida
- **Body:**
  ```json
  {
    "login": "usuario@email.com",
    "password": "senha123",
    "role": "USER"
  }
  ```
  > `role` aceita: `"USER"` ou `"ADMIN"`

- **Exemplo `curl`:**
  ```bash
  curl -X POST {HOST}/auth/register \
    -H "Content-Type: application/json" \
    -d '{
      "login": "usuario@email.com",
      "password": "senha123",
      "role": "USER"
    }'
  ```

- **Resposta de Sucesso (200 OK):** corpo vazio

- **Resposta de Erro (400 Bad Request):** e-mail já cadastrado
  Corpo vazio — verifique o e-mail informado.

---

#### Login

- **Endpoint:** `POST /auth/login`
- **Autenticação:** Não requerida
- **Body:**
  ```json
  {
    "login": "usuario@email.com",
    "password": "senha123"
  }
  ```

- **Exemplo `curl`:**
  ```bash
  curl -X POST {HOST}/auth/login \
    -H "Content-Type: application/json" \
    -d '{
      "login": "usuario@email.com",
      "password": "senha123"
    }'
  ```

- **Resposta de Sucesso (200 OK):**
  ```json
  {
    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
  }
  ```

- **Resposta de Erro (401 Unauthorized):**
  ```json
  {
    "message": "E-mail ou senha inválidos."
  }
  ```

---

### OCR & Análise

#### Extrair Texto (somente OCR)

Extrai e retorna o texto bruto do documento com PII mascarado. Não chama a IA e não salva nada.

- **Endpoint:** `POST /ocr`
- **Autenticação:** Requerida
- **Content-Type:** `multipart/form-data`
- **Parâmetro:** `file` — arquivo PNG, JPG, JPEG ou PDF (máx. 20 MB)

- **Exemplo `curl`:**
  ```bash
  curl -X POST {HOST}/ocr \
    -H "Authorization: Bearer {TOKEN}" \
    -F "file=@/caminho/para/fatura.pdf"
  ```

- **Resposta de Sucesso (200 OK):** texto puro (plain text)
  ```
  FATURA DE CARTÃO DE CRÉDITO
  Vencimento: 10/04/2026
  CPF: ******
  ...
  ```

- **Respostas de Erro:**

  | Código | Situação |
  |--------|----------|
  | 422 | Formato inválido, imagem corrompida ou texto insuficiente |

---

#### Analisar Documento (OCR + IA + Salvar)

Pipeline completo: OCR → mascaramento de PII → análise pela IA → persistência no banco.

- **Endpoint:** `POST /ocr/analyze`
- **Autenticação:** Requerida
- **Content-Type:** `multipart/form-data`
- **Parâmetro:** `file` — arquivo PNG, JPG, JPEG ou PDF (máx. 20 MB)

- **Exemplo `curl`:**
  ```bash
  curl -X POST {HOST}/ocr/analyze \
    -H "Authorization: Bearer {TOKEN}" \
    -F "file=@/caminho/para/fatura.png"
  ```

- **Resposta de Sucesso (200 OK):**
  ```json
  {
    "entries": {
      "total": "R$ 194,85",
      "classNote": null,
      "items": [
        {
          "description": "Pagamento em 10 MAR",
          "value": "R$ 194,85",
          "note": null
        }
      ]
    },
    "expenses": {
      "total": "R$ 194,85",
      "items": [
        {
          "category": "Compras com Cartão",
          "value": "R$ 27,68",
          "description": "iFood - NuPay - 05 MAR"
        },
        {
          "category": "Compras com Cartão",
          "value": "R$ 43,49",
          "description": "Uber - 07 MAR"
        }
      ]
    },
    "insights": [
      "Gastos concentrados em alimentação e transporte por aplicativo.",
      "Ausência de juros ou encargos na fatura."
    ],
    "advice": [
      "Considere estabelecer um limite mensal para gastos com delivery.",
      "Agrupe corridas de app para reduzir a frequência de uso."
    ]
  }
  ```

- **Respostas de Erro:**

  | Código | Situação |
  |--------|----------|
  | 422 | Formato inválido, imagem corrompida, texto insuficiente ou documento não é uma fatura reconhecida |
  | 502 | Falha na comunicação com a API da IA (Groq) |

> **Documentos aceitos:** fatura de cartão de crédito, conta de energia elétrica, água/saneamento, telefone/internet/TV, gás.
> **Documentos recusados:** contratos, boletos sem discriminação, notas fiscais avulsas, relatórios e outros documentos sem lançamentos financeiros.

---

### Insights (Histórico)

#### Listar Histórico de Análises

Retorna todas as análises salvas do usuário autenticado, da mais recente para a mais antiga.

- **Endpoint:** `GET /insights`
- **Autenticação:** Requerida

- **Exemplo `curl`:**
  ```bash
  curl -X GET {HOST}/insights \
    -H "Authorization: Bearer {TOKEN}"
  ```

- **Resposta de Sucesso (200 OK):**
  ```json
  [
    {
      "id": 1,
      "createdAt": "2026-03-19T18:45:00",
      "analysis": {
        "entries": {
          "total": "R$ 194,85",
          "classNote": null,
          "items": [...]
        },
        "expenses": {
          "total": "R$ 194,85",
          "items": [...]
        },
        "insights": ["..."],
        "advice": ["..."]
      }
    }
  ]
  ```

---

#### Deletar um Insight

Remove um insight do histórico. Só é possível deletar insights do próprio usuário autenticado.

- **Endpoint:** `DELETE /insights/{id}`
- **Autenticação:** Requerida

- **Exemplo `curl`:**
  ```bash
  curl -X DELETE {HOST}/insights/1 \
    -H "Authorization: Bearer {TOKEN}"
  ```

- **Resposta de Sucesso (204 No Content):** corpo vazio

- **Resposta de Erro (404 Not Found):** insight não existe ou não pertence ao usuário

---

### Admin

#### Verificar Acesso Admin

Rota de teste para confirmar que o token possui role `ADMIN`.

- **Endpoint:** `POST /create`
- **Autenticação:** Requerida — role `ADMIN`

- **Resposta de Sucesso (200 OK):**
  ```
  Acesso Liberado! O filtro leu seu Token e confirmou que você é ADMIN.
  ```

- **Resposta de Erro (403 Forbidden):** token válido mas sem role ADMIN

---

## 📎 Formatos e Limites

| Item | Valor |
|------|-------|
| **Formatos aceitos** | PNG, JPG, JPEG, PDF |
| **Tamanho máximo** | 20 MB por arquivo |
| **Arquivos por request** | 1 (múltiplos são rejeitados com 422) |
| **Texto mínimo extraído** | 80 caracteres |
| **Modelo de IA** | LLaMA 3.3 70B via Groq |

---

## 🛡️ Mascaramento de PII

Antes de qualquer dado ser enviado à IA, o serviço `PiiMaskingService` substitui automaticamente os seguintes dados por `******`:

| Dado | Exemplos mascarados |
|------|-------------------|
| CPF | `123.456.789-00` → `******` |
| CNPJ | `12.345.678/0001-99` → `******` |
| RG | `12.345.678-9` → `******` |
| Número de cartão | `4111 1111 1111 1111` → `******` |
| E-mail | `usuario@email.com` → `******` |
| Telefone | `(11) 91234-5678` → `******` |
| 0800 | `0800 123 4567` → `******` |

A IA nunca recebe dados pessoais identificáveis do documento.

---

## ⚠️ Respostas de Erro

Todos os erros retornam JSON no formato:

```json
{
  "message": "Descrição do erro."
}
```

| Código | Significado |
|--------|-------------|
| `400` | E-mail já cadastrado no registro |
| `401` | Credenciais inválidas no login |
| `403` | Acesso negado (role insuficiente) |
| `404` | Recurso não encontrado |
| `422` | Documento inválido, formato não suportado ou conteúdo insuficiente |
| `500` | Erro interno não tratado |
| `502` | Falha na comunicação com a API Groq |

---

## 🆘 Troubleshooting

### `NoClassDefFoundError: TessAPI` / SIGSEGV ao processar imagem
- **Causa:** Tesseract não instalado ou biblioteca nativa não encontrada pelo JNA.
- **Solução:**
  1. Instale `tesseract-ocr` e `libtesseract-dev`.
  2. Adicione `-Djna.library.path=/usr/lib/x86_64-linux-gnu` à JVM.
  3. Verifique se `TESSERACT_PATH` aponta para a pasta `tessdata` (ex: `/usr/share/tesseract-ocr/4.00/tessdata`).

### `Error opening data file .../por.traineddata`
- **Causa:** `TESSERACT_PATH` aponta para o diretório pai ao invés da pasta `tessdata`, ou o pacote de idioma não foi instalado.
- **Solução:** Ajuste `TESSERACT_PATH` para incluir `/tessdata` no final, ou baixe o `por.traineddata` diretamente do [repositório oficial](https://github.com/tesseract-ocr/tessdata_fast).

### `502 Bad Gateway` com erro de CORS no frontend
- **Causa:** O app backend caiu (SIGSEGV do Tesseract) e o proxy do Railway retornou 502 sem os headers CORS do Spring.
- **Solução:** Corrija o erro que causou o crash (geralmente Tesseract mal configurado) e aguarde o redeploy.

### `422` ao enviar PDF
- **Causa:** O PDF não contém texto embutido e o OCR não extraiu conteúdo suficiente, ou o documento não é uma fatura reconhecida.
- **Solução:** Envie uma fatura com texto legível. PDFs escaneados com baixa resolução podem falhar no OCR.

### `401` após login bem-sucedido
- **Causa:** Token não está sendo enviado no header `Authorization`.
- **Solução:** Certifique-se de incluir `Authorization: Bearer <token>` em todas as requisições protegidas.

### `400` no registro
- **Causa:** O e-mail já está cadastrado.
- **Solução:** Use um e-mail diferente ou faça login com as credenciais existentes.

---

## ❓ FAQ

### Meus dados pessoais são enviados para a IA?
Não. O `PiiMaskingService` substitui CPF, CNPJ, RG, número de cartão, e-mail e telefone por `******` **antes** de qualquer dado ser transmitido à API Groq.

### Quais documentos são aceitos para análise?
Faturas de cartão de crédito e contas de serviços (energia, água, gás, telefone, internet). Outros documentos (contratos, boletos simples, notas fiscais avulsas) são rejeitados com `422`.

### Como funciona o OCR em PDFs?
PDFs digitais (gerados por sistema) têm o texto extraído diretamente pelo PDFBox sem usar o Tesseract. O Tesseract só é acionado para imagens (PNG/JPG) e PDFs escaneados (sem texto embutido).

### Os totais são calculados pela IA?
Não. A IA identifica e categoriza os lançamentos. Os totais são sempre recalculados pelo backend em Java com `BigDecimal` para garantir precisão aritmética.

### Posso enviar múltiplos arquivos de uma vez?
Não. O endpoint `/ocr/analyze` aceita exatamente um arquivo por requisição. Envios múltiplos são rejeitados com `422`.

### O histórico de análises é compartilhado entre usuários?
Não. Cada usuário só acessa seus próprios insights. A listagem em `GET /insights` é filtrada automaticamente pelo usuário autenticado.

### Qual é o tamanho máximo de arquivo aceito?
20 MB, configurado via `spring.servlet.multipart.max-file-size`.

### Por que o endpoint retorna 502?
O `502 Bad Gateway` indica falha na comunicação com a API Groq (timeout, rate limit ou erro do serviço). Aguarde alguns instantes e tente novamente.

---

## 📁 Estrutura de Arquivos Relevantes

```
backend/
  src/main/java/com/start/start_app/
    controller/          → AutheticationController, OcrController, InsightController
    service/             → OcrService, GroqService, PiiMaskingService, InsightService, TokenService
    infrastructure/
      dto/               → FinancialAnalysisDTO, InsightResponseDTO, LoginResponseDTO...
      entitys/           → User, Insight, UserRole
      repository/        → UserRepository, InsightRepository
      configuration/     → SecurityConfiguration, SecurityFilter
    exception/
      business/          → OcrException, InvalidDocumentException, GroqException...
      handler/           → RestExceptionHandler
  src/main/resources/
    application.properties
  Dockerfile

frontend/
  src/app/
    pages/               → home, dashboard, login, register
    components/          → file-upload-zone, results-preview, header, features
    services/            → auth.service.ts, ocr.service.ts
    guards/              → auth.guard.ts
    interceptors/        → auth.interceptor.ts
  src/environments/      → environment.ts, environment.development.ts
```

---

**Desenvolvido com ☕ Java + 🅰️ Angular**
**Última atualização:** Março de 2026