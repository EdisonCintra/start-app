# OCR + PII Masking + Análise Financeira com IA — Guia Completo

> Este documento explica **cada peça** da feature de extração de documentos financeiros:
> OCR → mascaramento de dados sensíveis → análise com IA → exibição em cards no frontend.
> O foco é o **porquê** de cada decisão, não apenas o que o código faz.

---

## Índice

1. [O que é OCR?](#1-o-que-é-ocr)
2. [OcrService — extração de texto](#2-ocrservice--extração-de-texto)
3. [PiiMaskingService — proteção de dados sensíveis](#3-piimaskingservice--proteção-de-dados-sensíveis)
4. [OcrService atualizado — integrando o mascaramento](#4-ocrservice-atualizado--integrando-o-mascaramento)
5. [FinancialAnalysisDTO — contrato de dados com a IA](#5-financialanalysisdto--contrato-de-dados-com-a-ia)
6. [GroqService — chamando a IA](#6-groqservice--chamando-a-ia)
7. [OcrController atualizado — dois endpoints](#7-ocrcontroller-atualizado--dois-endpoints)
8. [Tratamento de erros — GroqException e RestExceptionHandler](#8-tratamento-de-erros--groqexception-e-restexceptionhandler)
9. [Frontend — OcrService (Angular)](#9-frontend--ocrservice-angular)
10. [Frontend — Dashboard (TypeScript)](#10-frontend--dashboard-typescript)
11. [Frontend — Dashboard (HTML) — os 4 cards](#11-frontend--dashboard-html--os-4-cards)
12. [Fluxo completo do sistema](#12-fluxo-completo-do-sistema)

---

## 1. O que é OCR?

**OCR** (Optical Character Recognition) é a tecnologia que lê uma imagem e **extrai o texto** contido nela.
Exemplo: você tira foto de um extrato bancário, o OCR "lê" a imagem e devolve o texto como string.

Nesse projeto usamos duas bibliotecas:

| Biblioteca | Papel |
|---|---|
| **Tess4J** | Wrapper Java para o Tesseract OCR (o motor de reconhecimento) |
| **PDFBox** | Abre arquivos PDF — extrai texto embutido ou renderiza páginas como imagem |

---

## 2. OcrService — extração de texto

### Configuração via `application.properties`

```java
@Value("${tesseract.datapath}")
private String datapath;

@Value("${tesseract.language:eng}")
private String language;
```

O `OcrService` **não tem** os caminhos do Tesseract escritos no código.
Eles vêm do `application.properties`:

```properties
tesseract.datapath=C:/Program Files/Tesseract-OCR
tesseract.language=eng   # eng = inglês, por = português
```

**Por que usar `@Value` em vez de escrever o caminho direto?**
> Se você escrever `"C:/Program Files/Tesseract-OCR"` no código, qualquer desenvolvedor que tenha o Tesseract instalado em outro lugar precisa **editar o código** para rodar o projeto. Com `@Value`, basta mudar o arquivo de propriedades — o código não muda.

O `:eng` no `@Value` é o **valor padrão** — se a propriedade não existir no `application.properties`, o Spring usa `"eng"` automaticamente.

---

### `extractText()` — Ponto de entrada

```java
public String extractText(MultipartFile file) {
    String ext = getValidatedExtension(file);

    String rawText = ext.equals("pdf")
            ? extractFromPdf(file)
            : extractFromImage(file);

    // Mascara CPF, CNPJ, RG, cartão, e-mail e telefone antes de expor ao frontend
    return piiMaskingService.mask(rawText);
}
```

Este é o único método `public` do serviço. Ele:
1. Valida a extensão
2. Roteia para PDF ou imagem
3. **Mascara dados sensíveis antes de retornar** (adicionado depois — explicado na seção 4)

---

### `getValidatedExtension()` — Validação de formato

```java
private static final List<String> ALLOWED_EXTENSIONS = List.of("png", "jpg", "jpeg", "pdf");

private String getValidatedExtension(MultipartFile file) {
    String filename = file.getOriginalFilename();
    String ext = (filename != null && filename.contains("."))
            ? filename.substring(filename.lastIndexOf('.') + 1).toLowerCase()
            : "";

    if (!ALLOWED_EXTENSIONS.contains(ext)) {
        throw new OcrException("Formato inválido: '" + ext + "'. Formatos suportados: PNG, JPG e PDF.");
    }

    return ext;
}
```

- Pega o nome original do arquivo enviado (`foto.PNG`, `contrato.pdf`, etc.)
- `lastIndexOf('.')` garante que pega a extensão correta mesmo em nomes como `meu.arquivo.final.pdf`
- `.toLowerCase()` garante que `"PNG"` e `"png"` sejam tratados igual
- Se não estiver na lista → lança `OcrException` (HTTP 422)
- Se estiver → **retorna a extensão** para o `extractText()` usar no roteamento

**Por que `List.of()` e não um array?**
> `List.of()` cria uma lista **imutável**. Ninguém pode acidentalmente fazer `ALLOWED_EXTENSIONS.add("exe")` em runtime. É uma boa prática para constantes que representam um conjunto fixo de valores.

---

### `extractFromImage()` — Imagens diretas (PNG, JPG)

```java
private String extractFromImage(MultipartFile file) {
    try {
        BufferedImage img = ImageIO.read(file.getInputStream());
        return runTesseract(img);
    } catch (IOException e) {
        throw new OcrException("Erro ao ler a imagem enviada.");
    }
}
```

Caminho simples:
1. Converte o `MultipartFile` em `BufferedImage` (formato que o Tesseract entende)
2. Passa direto para `runTesseract()`

**O que é `MultipartFile`?**
> É o tipo que o Spring usa para representar um arquivo enviado via `multipart/form-data` (o jeito padrão de enviar arquivos em formulários HTML e requests HTTP). O arquivo vem como bytes — `getInputStream()` dá acesso a esses bytes.

---

### `extractFromPdf()` — PDFs (estratégia dupla)

```java
private String extractFromPdf(MultipartFile file) {
    try (PDDocument document = Loader.loadPDF(file.getBytes())) {

        // Tentativa 1: texto embutido
        String embeddedText = new PDFTextStripper().getText(document);

        if (embeddedText != null && !embeddedText.isBlank()) {
            return embeddedText;
        }

        // Tentativa 2: PDF escaneado → renderiza como imagem → OCR
        PDFRenderer renderer = new PDFRenderer(document);
        StringBuilder result = new StringBuilder();

        for (int page = 0; page < document.getNumberOfPages(); page++) {
            BufferedImage pageImage = renderer.renderImageWithDPI(page, 300);
            result.append(runTesseract(pageImage));

            if (page < document.getNumberOfPages() - 1) {
                result.append("\n\n--- Página ").append(page + 2).append(" ---\n\n");
            }
        }

        return result.toString();

    } catch (IOException e) {
        throw new OcrException("Erro ao ler o arquivo PDF enviado.");
    }
}
```

PDFs existem em dois tipos — e o código trata os dois:

#### Tipo 1: PDF digital (gerado por Word, LibreOffice, banco, etc.)

O PDF já tem o texto **embutido** (não é imagem). O `PDFTextStripper` consegue extraí-lo diretamente, sem nenhum OCR. Rápido e preciso.

```
PDF com texto → PDFTextStripper.getText() → string com o texto
```

#### Tipo 2: PDF escaneado (foto/scan de documento físico)

O PDF é basicamente uma imagem dentro de um container PDF. Não tem texto embutido — `PDFTextStripper` retorna vazio ou em branco. Nesse caso:
1. `PDFRenderer` renderiza cada página como imagem (`BufferedImage`) a **300 DPI**
   - 300 DPI é o mínimo recomendado para OCR com qualidade aceitável
2. Cada página-imagem passa pelo `runTesseract()`
3. O resultado é concatenado com um separador `--- Página X ---`

**O que é `try-with-resources` (o `try (...)` com parênteses)?**
> O `PDDocument` usa memória e abre o arquivo. Precisamos garantir que ele será fechado ao final, mesmo se der erro. O `try-with-resources` fecha automaticamente qualquer objeto que implemente `AutoCloseable` — nesse caso, `PDDocument`. Sem isso, teríamos que usar `finally { document.close(); }` manualmente.

---

### `runTesseract()` — Motor OCR

```java
private String runTesseract(BufferedImage image) {
    try {
        Tesseract tesseract = new Tesseract();
        tesseract.setDatapath(datapath);
        tesseract.setLanguage(language);
        return tesseract.doOCR(image);
    } catch (TesseractException e) {
        throw new OcrException("Falha no motor OCR: " + e.getMessage());
    }
}
```

**Por que cria uma instância nova a cada chamada?**

> O Tesseract **não é thread-safe**. Se fosse um `@Bean` singleton e dois requests chegassem ao mesmo tempo, os dois threads compartilhariam a mesma instância — o resultado seria corrompido ou causaria crash. Criar `new Tesseract()` a cada chamada garante isolamento total entre requests simultâneos.

---

## 3. PiiMaskingService — proteção de dados sensíveis

### O que é PII?

**PII** (Personally Identifiable Information) são dados que identificam uma pessoa: CPF, RG, número de cartão, e-mail, telefone.

**Problema:** o OCR extrai o texto bruto de documentos financeiros. Esses documentos frequentemente têm CPF, cartão de crédito e outros dados sensíveis. Se retornarmos esse texto diretamente ao frontend, estamos expondo dados que deveriam ser protegidos.

**Solução:** antes de devolver o texto, passar por um serviço que **detecta e mascara** esses dados com `******`.

---

### O que é Regex?

**Regex** (Regular Expression) é uma linguagem para descrever padrões em texto.

Exemplo simples: o padrão `\d{3}` significa "exatamente 3 dígitos". Com isso, você consegue encontrar qualquer sequência de 3 números em uma string.

Em Java, regex é compilado em um objeto `Pattern`. Compilar é transformar a string de padrão em uma estrutura interna que o Java usa para fazer buscas de forma eficiente.

```java
// Compilar uma vez (caro em processamento)
private static final Pattern CPF = Pattern.compile("\\d{3}[. ]?\\d{3}[. ]?\\d{3}[- ]?\\d{2}");

// Usar N vezes (barato)
CPF.matcher(text).replaceAll("******");
```

**Por que `static final`?**
> Compilar um Pattern é uma operação custosa. Se ficasse dentro do método `mask()`, seria compilado **a cada chamada** — toda vez que um documento fosse processado. Como `static final`, é compilado **uma única vez** quando a classe carrega, e reutilizado para sempre.

---

### Anatomia dos padrões

#### CPF: `\d{3}[. ]?\d{3}[. ]?\d{3}[- ]?\d{2}`

Vamos ler peça por peça:

| Peça | Significado |
|---|---|
| `\d{3}` | exatamente 3 dígitos (0-9) |
| `[. ]?` | um ponto **ou** um espaço, opcionalmente (o `?` torna opcional) |
| `[. ]?` | mesma coisa (segundo separador) |
| `[- ]?` | um traço **ou** um espaço, opcionalmente |
| `\d{2}` | exatamente 2 dígitos |

O que isso captura:
```
123.456.789-09   ✓  (formato padrão)
123 456 789 09   ✓  (separado por espaço — novo caso)
12345678909      ✓  (sem separadores)
```

**Por que `[. ]` e não `\.?`?**
> `\.?` aceita apenas ponto opcional. `[. ]?` aceita ponto **ou** espaço opcional — necessário porque OCR de documentos físicos às vezes reconhece os pontos do CPF como espaços.

---

#### CNPJ: `\d{2}\.?\d{3}\.?\d{3}/?\d{4}-?\d{2}`

Mesmo conceito, estrutura diferente:
- `\d{2}` → 2 dígitos
- `\.?` → ponto opcional
- `\d{3}` → 3 dígitos
- `\.?` → ponto opcional
- `\d{3}` → 3 dígitos
- `/?` → barra opcional
- `\d{4}` → 4 dígitos
- `-?` → traço opcional
- `\d{2}` → 2 dígitos de verificação

---

#### RG com prefixo de estado: `[A-Z]{2}-\d{1,2}\.?\d{3}\.?\d{3}(-?[0-9Xx])?`

Este padrão cobre o caso `MG-12.345.678`:

| Peça | Significado |
|---|---|
| `[A-Z]{2}` | duas letras maiúsculas (a sigla do estado: MG, SP, RJ...) |
| `-` | hífen **obrigatório** (torna o match inequívoco — sem isso `"DE 123"` poderia ser confundido) |
| `\d{1,2}` | 1 ou 2 dígitos |
| `\.?\d{3}\.?\d{3}` | dois grupos de 3 dígitos com ponto opcional |
| `(-?[0-9Xx])?` | dígito verificador opcional (pode ser X nos RGs antigos) |

**Por que o hífen é obrigatório no prefixo de estado?**
> Se fosse `[A-Z]{2}[-\s]?` (hífen opcional), o padrão poderia capturar qualquer palavra de duas letras antes de um número — como "em 12.345.678" (preposição seguida de número). Com o hífen obrigatório, só capturamos quando a sigla vem explicitamente colada com hífen ao número.

---

#### RG de 7 dígitos com rótulo: `(?i)(rg\s*[-:]?\s*)\d{7}`

Este é o mais complexo. Vamos desmontar:

| Peça | Significado |
|---|---|
| `(?i)` | flag de case-insensitive — `rg`, `RG`, `Rg` são todos capturados |
| `(rg\s*[-:]?\s*)` | grupo 1: a palavra "rg", seguida de espaços opcionais, traço ou dois-pontos opcional, mais espaços opcionais |
| `\d{7}` | exatamente 7 dígitos |

**Por que requer o rótulo "RG"?**
> Um número de 7 dígitos sozinho (`1234567`) é **completamente ambíguo** — pode ser um CEP, um ano, um número de conta, um código interno. Sem contexto, mascarar todo número de 7 dígitos causaria inúmeros falsos positivos (ex: `"2024: R$ 1.234,56"` poderia ter partes mascaradas). O rótulo `RG` dá o contexto necessário.

**O que é um grupo de captura `()`?**
> Parênteses em regex criam um "grupo". O conteúdo do grupo pode ser referenciado depois com `$1`, `$2`, etc. Aqui, o grupo 1 captura o texto `"RG: "` ou `"rg "`. No `replaceAll`, usamos `"$1" + MASK` para **preservar** o rótulo e substituir apenas o número:
> ```
> "RG: 1234567"  →  "RG: ******"
>                      ↑ grupo 1 preservado
> ```

---

#### Cartão: `\b\d{4}[\s-]?\d{4}[\s-]?\d{4}[\s-]?\d{4}\b`

Quatro grupos de 4 dígitos separados por espaço ou hífen opcionais.

**O que é `\b` (word boundary)?**
> `\b` é um "delimitador de palavra" — faz match numa posição entre um caractere alfanumérico e um não-alfanumérico. Isso evita que `12345678901234567` (17 dígitos) seja capturado como um cartão de 16. Sem `\b`, o regex buscaria 16 dígitos dentro de qualquer sequência maior.

---

#### Telefone 0800: `\b0800[\s-]?\d{3}[\s-]?\d{4}\b`

**Por que tem um padrão separado para 0800, se já existe o padrão de telefone?**

O padrão genérico de telefone é:
```
(\+55[\s-]?)?(\(?\d{2}\)?[\s-]?)?\d{4,5}[\s-]?\d{4}
```

Testando contra `"0800 123 4567"`:
- `(\+55)?` → não casa, pula
- `(\(?\d{2}\)?)?` → casa com `08` (dois primeiros dígitos)
- `\d{4,5}` → tenta casar com o que sobra: `00 123` — falha por causa do espaço
- Então o regex "recomeça" e tenta `\d{4,5}` = `0800`, depois `[\s-]?` = espaço, depois `\d{4}` = `1234`
- **Resultado:** captura apenas `"0800 1234"`, deixando `" 567"` sem mascarar

O padrão dedicado `0800[\s-]?\d{3}[\s-]?\d{4}` captura o número inteiro corretamente.

---

### A ordem importa

```java
result = CNPJ            .matcher(result).replaceAll(MASK);  // 1°
result = CPF             .matcher(result).replaceAll(MASK);  // 2°
result = RG_STATE_PREFIX .matcher(result).replaceAll(MASK);  // 3°
result = RG              .matcher(result).replaceAll(MASK);  // 4°
result = RG_LABELED_7   .matcher(result).replaceAll("$1" + MASK); // 5°
result = CARD            .matcher(result).replaceAll(MASK);  // 6°
result = EMAIL           .matcher(result).replaceAll(MASK);  // 7°
result = PHONE_0800      .matcher(result).replaceAll(MASK);  // 8°
result = PHONE           .matcher(result).replaceAll(MASK);  // 9°
```

**Regra 1: CNPJ antes de CPF**

Um CNPJ começa com 14 dígitos. Os primeiros 11 dígitos de um CNPJ batem perfeitamente no padrão do CPF.
```
CNPJ: 12.345.678/0001-90
CPF pattern veria: 12.345.678 → match!
```
Se rodar CPF primeiro, o início do CNPJ vira `****** /0001-90` — mascaramento errado. Rodando CNPJ primeiro, o número inteiro é substituído por `******` e o CPF não encontra mais nada.

**Regra 2: RG_STATE_PREFIX antes de RG**

`"MG-12.345.678"` → o padrão RG genérico capturaria apenas `"12.345.678"`, deixando o prefixo `"MG-"` para trás. O `RG_STATE_PREFIX` captura tudo junto.

**Regra 3: PHONE_0800 antes de PHONE**

Já explicado acima — o PHONE genérico faz match parcial em números 0800.

---

## 4. OcrService atualizado — integrando o mascaramento

```java
// Injeção via construtor: preferida ao @Autowired no campo
private final PiiMaskingService piiMaskingService;

public OcrService(PiiMaskingService piiMaskingService) {
    this.piiMaskingService = piiMaskingService;
}
```

**Por que injeção via construtor e não `@Autowired` no campo?**

Existem duas formas de injetar dependências no Spring:

```java
// Forma 1: campo (não recomendada)
@Autowired
private PiiMaskingService piiMaskingService;

// Forma 2: construtor (recomendada)
private final PiiMaskingService piiMaskingService;

public OcrService(PiiMaskingService piiMaskingService) {
    this.piiMaskingService = piiMaskingService;
}
```

Vantagens do construtor:
1. O campo pode ser `final` — garantia de que nunca será `null` após a construção
2. Em testes, você pode criar `new OcrService(mockPiiMaskingService)` sem precisar de framework
3. Deixa claro quais são as dependências obrigatórias da classe (se o bean não existir, o erro acontece na inicialização, não em runtime)

O Spring detecta automaticamente construtores com parâmetros e injeta as dependências. Não precisa de `@Autowired` no construtor desde o Spring 4.3.

---

```java
public String extractText(MultipartFile file) {
    String ext = getValidatedExtension(file);

    String rawText = ext.equals("pdf")
            ? extractFromPdf(file)
            : extractFromImage(file);

    return piiMaskingService.mask(rawText);  // ← mascaramento aqui
}
```

**Por que o mascaramento fica aqui e não no controller?**

> Princípio de responsabilidade: `OcrService` é responsável por entregar texto extraído de documentos. A proteção de PII faz parte dessa responsabilidade — um documento "processado" deve sempre chegar mascarado. Se ficasse no controller, qualquer outro trecho de código que chamasse `OcrService.extractText()` no futuro receberia dados desprotegidos.

---

## 5. FinancialAnalysisDTO — contrato de dados com a IA

```java
public record FinancialAnalysisDTO(
        EntriesSection entries,
        ExpensesSection expenses,
        List<String> insights,
        List<String> advice
) {
    public record EntriesSection(
            String total,
            String classNote,
            List<EntryItem> items
    ) {}

    public record EntryItem(
            String description,
            String value,
            String note
    ) {}

    public record ExpensesSection(
            String total,
            List<ExpenseItem> items
    ) {}

    public record ExpenseItem(
            String category,
            String value,
            String description
    ) {}
}
```

### O que é um `record` em Java?

Introduzido no Java 16, `record` é uma forma compacta de criar classes que só carregam dados. Comparando:

```java
// Forma antiga (classe normal)
public class EntryItem {
    private final String description;
    private final String value;
    private final String note;

    public EntryItem(String description, String value, String note) {
        this.description = description;
        this.value = value;
        this.note = note;
    }

    public String getDescription() { return description; }
    public String getValue() { return value; }
    public String getNote() { return note; }
    // + equals(), hashCode(), toString() que você teria que escrever
}

// Forma com record (equivalente ao acima)
public record EntryItem(String description, String value, String note) {}
```

O `record` gera automaticamente: construtor, getters, `equals()`, `hashCode()` e `toString()`.

**Por que usar `record` para DTOs?**
> DTOs (Data Transfer Objects) são classes que só transportam dados entre camadas. Eles não têm lógica de negócio — só campos e acesso a eles. O `record` é perfeito para isso: imutável por padrão, zero boilerplate.

**Por que os records são aninhados dentro de `FinancialAnalysisDTO`?**
> `EntriesSection`, `EntryItem`, etc. não fazem sentido fora do contexto da análise financeira. Aninhar mantém tudo organizado e deixa claro o relacionamento entre os tipos. Se fossem classes separadas, o package ficaria poluído com classes que só existem para servir a uma única classe pai.

**Como o Jackson serializa records?**
> O Jackson (biblioteca de JSON do Java) suporta records nativamente desde a versão 2.12. Ele usa os nomes dos parâmetros do construtor como chaves JSON. Então `EntryItem(String description, ...)` gera `{"description": "...", ...}` automaticamente.

### Por que esse DTO tem essa estrutura?

A estrutura espelha o que instruímos a IA a retornar no prompt:
```json
{
  "entries": {
    "total": "R$ 11.000,00",
    "classNote": "classe média alta",
    "items": [{ "description": "...", "value": "...", "note": null }]
  },
  "expenses": {
    "total": "R$ 22.860,73",
    "items": [{ "category": "...", "value": "...", "description": "..." }]
  },
  "insights": ["..."],
  "advice": ["..."]
}
```

O DTO é o **contrato** entre o backend e o frontend. Quando o frontend espera um campo `entries.total`, o backend **garante** que esse campo existe nessa estrutura.

---

## 6. GroqService — chamando a IA

### O que é a API da Groq?

**Groq** é uma empresa de hardware + software que roda modelos de linguagem (LLMs) com velocidade muito alta. Eles têm uma API gratuita (com limites de rate) compatível com o formato da OpenAI — o mesmo JSON de request/response.

O modelo que usamos é `llama-3.3-70b-versatile` — um modelo open-source da Meta com bom desempenho em português, hospedado nos servidores da Groq.

---

### Injeção de dependências e RestClient

```java
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.core.JacksonException;
```

**Por que os imports são `tools.jackson` e não `com.fasterxml.jackson`?**

O Spring Boot 4.x usa **Jackson 3.x**, que mudou o pacote raiz da biblioteca:

| Jackson 2.x (Boot 3.x) | Jackson 3.x (Boot 4.x) |
|---|---|
| `com.fasterxml.jackson.databind.ObjectMapper` | `tools.jackson.databind.ObjectMapper` |
| `com.fasterxml.jackson.databind.JsonNode` | `tools.jackson.databind.JsonNode` |
| `com.fasterxml.jackson.core.JsonProcessingException` | `tools.jackson.core.JacksonException` |

Isso é uma **breaking change** de pacote — o código que usava `com.fasterxml.jackson` não compila mais com Jackson 3.x. A funcionalidade é a mesma; só o namespace mudou.

---

```java
private final RestClient restClient;
private final ObjectMapper objectMapper;

// RestClient.create() cria uma instância padrão sem necessidade de injeção do Builder
public GroqService(ObjectMapper objectMapper) {
    this.restClient = RestClient.create();
    this.objectMapper = objectMapper;
}
```

**O que é `RestClient`?**
> `RestClient` é o cliente HTTP do Spring (introduzido no Spring 6.1 / Boot 3.2). Ele substitui o antigo `RestTemplate` com uma API mais moderna no estilo "builder" — você encadeia chamadas para construir e executar a requisição.

**Por que `RestClient.create()` e não injeção do `RestClient.Builder`?**

Em versões anteriores, o código injetava `RestClient.Builder` pelo construtor:
```java
// Forma anterior (não funciona no Boot 4.x com starter webmvc)
public GroqService(RestClient.Builder restClientBuilder, ObjectMapper objectMapper) {
    this.restClient = restClientBuilder.build();
```

No Spring Boot 4.x com o starter `spring-boot-starter-webmvc`, o bean `RestClient.Builder` **não é auto-configurado** — ao tentar iniciar a aplicação, o Spring falha com `No beans of 'Builder' type found`.

A solução é usar o método de fábrica estático `RestClient.create()`, que cria uma instância diretamente sem precisar de injeção:
```java
this.restClient = RestClient.create();
```

> Para a maioria dos casos como esse — uma chamada externa simples sem customizações de interceptors ou codecs — `RestClient.create()` é suficiente e mais direto.

**O que é `ObjectMapper`?**
> `ObjectMapper` é a classe central do Jackson — a biblioteca de JSON do Java. Ela converte entre objetos Java e JSON. O Spring Boot configura um `ObjectMapper` como bean automaticamente (agora da versão 3.x, pacote `tools.jackson`). Ao injetá-lo, aproveitamos a configuração padrão do projeto em vez de criar um novo do zero.

---

### Construindo e enviando a requisição

```java
Map<String, Object> requestBody = Map.of(
        "model", MODEL,
        "response_format", Map.of("type", "json_object"),
        "messages", List.of(Map.of("role", "user", "content", buildPrompt(maskedText)))
);
```

**O que é esse `Map.of()`?**
> Estamos construindo o JSON de requisição da API da Groq usando Maps Java. O Jackson vai serializar esse Map para JSON automaticamente quando enviarmos. A estrutura segue o formato da API OpenAI-compatível:
> ```json
> {
>   "model": "llama-3.3-70b-versatile",
>   "response_format": { "type": "json_object" },
>   "messages": [{ "role": "user", "content": "..." }]
> }
> ```

**O que é `response_format: json_object`?**
> Esse campo instrui o modelo a retornar **sempre um JSON válido**, sem texto explicativo ao redor. Sem isso, o modelo poderia responder com:
> ```
> Aqui está minha análise:
> ```json
> { "entries": ... }
> ```
> ```
> O que causaria falha no parse. Com `json_object`, a resposta é puro JSON.

```java
String rawResponse = restClient.post()
        .uri(GROQ_URL)
        .header("Authorization", "Bearer " + apiKey)
        .contentType(MediaType.APPLICATION_JSON)
        .body(requestBody)
        .retrieve()
        .body(String.class);
```

Cada linha é uma etapa do "fluent builder":
- `.post()` → método HTTP POST
- `.uri(GROQ_URL)` → URL de destino
- `.header("Authorization", "Bearer " + apiKey)` → autenticação Bearer Token (padrão de APIs modernas)
- `.contentType(MediaType.APPLICATION_JSON)` → diz para o servidor que estamos enviando JSON
- `.body(requestBody)` → o corpo da requisição (Map → JSON via Jackson)
- `.retrieve()` → executa a requisição e torna a resposta disponível
- `.body(String.class)` → lê o corpo da resposta como String

**O que é Bearer Token?**
> É um esquema de autenticação onde você inclui um token no header `Authorization`. O servidor valida o token e decide se autoriza a requisição. "Bearer" significa "portador" — quem porta o token tem acesso.

---

### Parseando a resposta da Groq

```java
private FinancialAnalysisDTO parseGroqResponse(String rawResponse) {
    try {
        JsonNode root = objectMapper.readTree(rawResponse);
        // asString() substitui asText() depreciado no Jackson 3.x
        String content = root.path("choices").get(0).path("message").path("content").asString();
        return objectMapper.readValue(content, FinancialAnalysisDTO.class);
    } catch (JacksonException | NullPointerException e) {
        throw new GroqException("Falha ao interpretar a resposta da IA: " + e.getMessage());
    }
}
```

A resposta bruta da Groq tem essa estrutura:
```json
{
  "choices": [
    {
      "message": {
        "content": "{ \"entries\": { ... }, \"expenses\": { ... } }"
      }
    }
  ]
}
```

O JSON da análise financeira fica dentro de `choices[0].message.content` como uma **string** (JSON dentro de JSON).

**O que é `JsonNode`?**
> Ao invés de criar uma classe Java para representar toda a resposta da Groq (que tem dezenas de campos que não precisamos), usamos `JsonNode` — um nó de árvore JSON. Com `readTree()`, o Jackson parseia o JSON em uma estrutura de árvore que podemos navegar:
> - `.path("choices")` → acessa o campo "choices" (retorna `JsonNode` vazio se não existir, nunca null)
> - `.get(0)` → pega o primeiro elemento do array
> - `.path("message").path("content")` → navega dois níveis
> - `.asString()` → converte o nó para String (`.asText()` foi depreciado no Jackson 3.x)

**Por que `asString()` e não `asText()`?**
> No Jackson 3.x, o método `.asText()` foi marcado como `@Deprecated`. O substituto é `.asString()`, que faz exatamente a mesma coisa — converte o valor do nó JSON para uma String Java. Usar métodos depreciados não causa erro imediato, mas indica que serão removidos em versões futuras.

```java
return objectMapper.readValue(content, FinancialAnalysisDTO.class);
```

Agora que temos a string com o JSON da análise, usamos `readValue()` para desserializar diretamente no nosso DTO.

**Por que duas chamadas ao `objectMapper` e não uma?**
> Porque a resposta da Groq tem dois níveis de JSON: o JSON externo (estrutura da API da Groq) e o JSON interno (nossa análise financeira). Com `readTree` navegamos o externo para extrair o `content`, e com `readValue` desserializamos o interno.

**Por que `JacksonException` e não `JsonProcessingException`?**
> No Jackson 2.x, erros de parse lançavam `JsonProcessingException` (checked exception — obrigava o `try/catch`). No Jackson 3.x, esse tipo foi renomeado para `JacksonException` e passou a ser **unchecked** (extends `RuntimeException`). Isso significa que você não é obrigado a capturá-la, mas fazemos isso aqui intencionalmente para dar uma mensagem de erro amigável ao usuário ao invés de deixar a exception propagar com a mensagem técnica do Jackson.

---

### Prompt Engineering — instruindo a IA

```java
private String buildPrompt(String text) {
    return """
            Você é um analista financeiro especializado em documentos brasileiros.
            Analise o texto de documento financeiro abaixo e retorne APENAS um JSON válido...

            Use exatamente esta estrutura:
            { ... }

            Regras importantes:
            - Agrupe despesas em categorias...
            - Em insights inclua percentuais...
            """ + text;
}
```

**O que é "Prompt Engineering"?**
> É a arte de escrever instruções para um modelo de linguagem de forma que ele produza exatamente o que você quer. Algumas técnicas usadas aqui:

1. **Definir um papel:** `"Você é um analista financeiro"` → o modelo adota esse contexto e adapta o estilo de resposta
2. **Mostrar o formato esperado:** incluir o JSON template deixa o modelo sem ambiguidade sobre a estrutura
3. **Regras explícitas:** `"retorne APENAS um JSON válido, sem nenhum texto"` → evita texto extra que quebraria o parse
4. **Instruções de domínio:** `"agrupe despesas em categorias"` → guia a análise para o que é útil ao usuário
5. **Fallback:** `"use listas vazias e null"` → evita erros quando o documento não tem dados financeiros

**O que é Text Block `"""`?**
> Introduzido no Java 15, text block permite escrever strings multilinha sem concatenação e sem `\n` explícito. O conteúdo é indentado automaticamente relativo às `"""` de fechamento.

---

## 7. OcrController atualizado — dois endpoints

```java
@RestController
@RequestMapping("/ocr")
public class OcrController {

    private final OcrService ocrService;
    private final GroqService groqService;

    public OcrController(OcrService ocrService, GroqService groqService) {
        this.ocrService = ocrService;
        this.groqService = groqService;
    }

    // Endpoint original — texto bruto mascarado
    @PostMapping
    public ResponseEntity<String> extractText(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(ocrService.extractText(file));
    }

    // Endpoint novo — análise financeira completa via IA
    @PostMapping("/analyze")
    public ResponseEntity<FinancialAnalysisDTO> analyzeDocument(@RequestParam("file") MultipartFile file) {
        String maskedText = ocrService.extractText(file);
        FinancialAnalysisDTO analysis = groqService.analyze(maskedText);
        return ResponseEntity.ok(analysis);
    }
}
```

**Por que manter dois endpoints ao invés de substituir o original?**
> `POST /ocr` retorna o texto mascarado — útil para debug, para features futuras ou para quem só quer extrair texto. `POST /ocr/analyze` faz o pipeline completo. Remover o primeiro quebraria qualquer código que já dependesse dele.

**Por que o controller é tão simples?**
> O controller tem uma responsabilidade única: receber a requisição HTTP e delegar para os serviços corretos. Toda a lógica fica nos serviços. Isso é o **padrão de camadas**: Controller → Service → (Repository ou integração externa). Se a lógica ficasse no controller, seria mais difícil testar e reutilizar.

**Por que `ocrService.extractText()` e não chamar OCR diretamente no `analyzeDocument`?**
> `extractText()` já aplica o mascaramento de PII. Chamar o OCR diretamente no analyze endpoint enviaria dados brutos (com CPF, RG, etc.) para a IA — um vazamento de dados desnecessário. A IA não precisa dos dados reais para fazer a análise financeira.

---

## 8. Tratamento de erros — GroqException e RestExceptionHandler

### GroqException

```java
public class GroqException extends RuntimeException {
    public GroqException(String message) {
        super(message);
    }
}
```

Segue o mesmo padrão das outras exceptions do projeto (`OcrException`, `EmailConflictException`). Cada tipo de erro tem sua própria exception para que o `RestExceptionHandler` possa tratá-los diferente.

**Por que não reutilizar `OcrException` para erros da Groq?**
> Porque o HTTP status é diferente. `OcrException` retorna `422 Unprocessable Entity` — o arquivo enviado estava ok mas não conseguimos processar. `GroqException` retorna `502 Bad Gateway` — o arquivo foi processado ok, mas o serviço externo (Groq) falhou. Usar a mesma exception daria o status HTTP errado.

### RestExceptionHandler atualizado

```java
// 502 Bad Gateway = o servidor recebeu uma resposta inválida de um serviço externo
@ExceptionHandler(GroqException.class)
private ResponseEntity<RestErrorMessage> groqHandler(GroqException exception) {
    RestErrorMessage threatResponse = new RestErrorMessage(HttpStatus.BAD_GATEWAY, exception.getMessage());
    return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(threatResponse);
}
```

**O que significa cada status HTTP nesse contexto?**

| Status | Código | Usado quando |
|---|---|---|
| `422 Unprocessable Entity` | formato de arquivo inválido | o arquivo foi recebido mas não conseguimos processar |
| `502 Bad Gateway` | falha na Groq | processamos o arquivo mas o serviço de IA externo falhou |
| `409 Conflict` | email duplicado | a operação conflita com estado existente |
| `404 Not Found` | email não existe | o recurso não foi encontrado |

Usar o status HTTP correto é importante porque o frontend pode ter comportamentos diferentes para cada status. Um `502` sugere "tente novamente mais tarde". Um `422` sugere "o arquivo está errado, envie outro".

---

## 9. Frontend — OcrService (Angular)

```typescript
export interface EntryItem {
  description: string;
  value: string;
  note: string | null;
}

export interface ExpenseItem {
  category: string;
  value: string;
  description: string;
}

export interface FinancialAnalysis {
  entries: {
    total: string | null;
    classNote: string | null;
    items: EntryItem[];
  } | null;
  expenses: {
    total: string | null;
    items: ExpenseItem[];
  } | null;
  insights: string[];
  advice: string[];
}
```

**O que é uma `interface` em TypeScript?**
> Uma interface é um contrato de tipos. Ela descreve o formato de um objeto — quais campos existem e quais são seus tipos. O TypeScript usa isso em **tempo de compilação** (não em runtime) para detectar erros: se o backend retornar `{ entradas: ... }` mas a interface espera `{ entries: ... }`, o TypeScript acusa erro antes do código rodar.

**Por que `string | null`?**
> O operador `|` cria um **union type** — o campo pode ser string ou null. Isso corresponde ao Java `String` que pode ser `null` quando a IA não encontra dados. Sem `| null`, TypeScript reclamaria quando o código tentasse lidar com valores nulos.

**Por que as interfaces ficam no `OcrService` e não num arquivo separado?**
> São dados intimamente ligados ao serviço OCR — faz sentido estarem no mesmo arquivo. Se ficassem em `models.ts`, seria mais um arquivo para navegar sem ganho real. Em projetos maiores, um arquivo de models faz sentido; aqui seria over-engineering.

```typescript
analyzeDocument(file: File) {
  const formData = new FormData();
  formData.append('file', file);
  return this.http.post<FinancialAnalysis>(`${this.apiUrl}/analyze`, formData);
}
```

O `<FinancialAnalysis>` no `post<FinancialAnalysis>()` informa ao TypeScript qual é o tipo esperado da resposta. O Angular não valida em runtime — é só para type-checking em tempo de desenvolvimento.

---

## 10. Frontend — Dashboard (TypeScript)

### Interface OcrResult atualizada

```typescript
export interface OcrResult {
  fileName: string;
  analysis: FinancialAnalysis;  // antes era: text: string
}
```

Antes o resultado era texto bruto. Agora é a análise estruturada. O nome `OcrResult` foi mantido porque ainda representa "o resultado do processamento de um arquivo OCR" — só que com mais informação.

### analyzeFiles() atualizado

```typescript
analyzeFiles() {
    const files = this.selectedFiles();
    if (files.length === 0) return;

    this.isAnalyzing.set(true);
    this.results.set(null);
    this.errorMessage.set(null);

    const requests = files.map((file) => this.ocrService.analyzeDocument(file));

    forkJoin(requests)
      .pipe(finalize(() => this.isAnalyzing.set(false)))
      .subscribe({
        next: (analyses) => {
          const results: OcrResult[] = files.map((file, i) => ({
            fileName: file.name,
            analysis: analyses[i],
          }));
          this.results.set(results);
        },
        error: (err) => {
          const msg = err.error?.message ?? err.error ?? 'Erro ao processar o arquivo.';
          this.errorMessage.set(msg);
        },
      });
  }
```

**O que é `forkJoin`?**
> `forkJoin` é um operador do RxJS (biblioteca de programação reativa do Angular). Ele recebe um array de Observables e:
> - Dispara todos **em paralelo** (não espera um terminar para começar o outro)
> - Aguarda **todos terminarem**
> - Emite um array com os resultados na mesma ordem dos inputs
>
> Sem `forkJoin`, enviar 3 arquivos exigiria 3 chamadas sequenciais — 3x mais lento.

**O que é `finalize()`?**
> `finalize()` é um operador RxJS que executa um callback quando o Observable completa — seja por sucesso ou por erro. Aqui ele garante que `isAnalyzing.set(false)` sempre acontece, desabilitando o spinner independente do resultado.

**O que é um `Signal` (`.set()`, `.set(null)`)?**
> Signals são a forma moderna do Angular (v17+) de gerenciar estado reativo. Um signal é como uma variável que o Angular "observa" — quando seu valor muda, os componentes que a usam re-renderizam automaticamente.
>
> - `signal<File[]>([])` → cria um signal com valor inicial
> - `selectedFiles.set([...novosArquivos])` → atualiza o valor
> - `selectedFiles()` → lê o valor atual (note os parênteses — é uma função)

**O que é `err.error?.message ?? err.error`?**
> Navegação segura com `?.`: se `err.error` for `null` ou `undefined`, a expressão retorna `undefined` sem erro.
> Operador de coalescência nula `??`: se o lado esquerdo for `null` ou `undefined`, usa o lado direito.
> Então: tenta pegar `err.error.message` → se não existir, tenta `err.error` → se não existir, usa o fallback.

---

## 11. Frontend — Dashboard (HTML) — os 4 cards

### Estrutura geral

```html
@if (results()) {
  @for (item of results()!; track item.fileName) {
    <!-- 4 cards por arquivo -->
  }
}
```

**O que é `@if` e `@for` com `@`?**
> São a nova sintaxe de controle de fluxo do Angular 17+ (antes era `*ngIf` e `*ngFor`). São mais legíveis e têm melhor performance porque não precisam de uma diretiva estrutural separada.

**O que é o `!` em `results()!`?**
> É o "non-null assertion operator" do TypeScript. Diz ao compilador: "eu sei que esse valor não é null aqui". Usamos porque o `@if (results())` já garante que só entramos nesse bloco quando `results()` não é null — mas o TypeScript não consegue inferir isso automaticamente.

**O que é `track item.fileName` no `@for`?**
> O `track` ajuda o Angular a identificar quais itens mudaram quando a lista é atualizada. Funciona como uma "chave única". Sem isso, o Angular re-renderiza toda a lista ao menor change. Com `track item.fileName`, ele sabe que o item com o mesmo nome não mudou e pula a re-renderização.

---

### Card 1: Entradas & Receitas (verde)

```html
@if (item.analysis.entries) {
  <div class="bg-card rounded-2xl border border-border p-5 shadow-sm">

    <div class="flex items-start justify-between gap-3 mb-4">
      <div class="flex items-center gap-2">
        <div class="w-8 h-8 rounded-lg bg-green-50 flex items-center justify-center shrink-0">
          <!-- SVG seta para cima -->
        </div>
        <span class="font-semibold text-foreground text-sm">Entradas & Receitas</span>
      </div>
      @if (item.analysis.entries.total) {
        <span class="text-green-600 font-bold text-base shrink-0">{{ item.analysis.entries.total }}</span>
      }
    </div>
    ...
```

**Por que `@if (item.analysis.entries)` antes do card?**
> A seção `entries` pode ser `null` se o documento não tiver entradas (ex: uma fatura simples). Sem esse `@if`, o template tentaria acessar `null.total` e quebraria. O `@if` atua como uma "guarda" — só renderiza o card se a seção existe.

**Por que `shrink-0` no total?**
> `flex-shrink: 0` impede que o Flexbox comprima o elemento quando o espaço é limitado. Sem isso, nomes longos no lado esquerdo poderiam comprimir o valor à direita, quebrando o layout.

```html
@if (item.analysis.entries.classNote) {
  <p class="text-xs text-muted-foreground bg-muted rounded-lg px-3 py-2 mb-3">
    {{ item.analysis.entries.classNote }}
  </p>
}
```

A nota de classificação socioeconômica (`"classe média alta"`) só aparece se a IA conseguiu identificá-la.

```html
@for (entry of item.analysis.entries.items; track entry.description) {
  <div class="flex items-start justify-between gap-3 py-2 border-t border-border first:border-t-0">
    <div class="min-w-0">
      <p class="text-sm text-foreground">{{ entry.description }}</p>
      @if (entry.note) {
        <p class="text-xs text-muted-foreground mt-0.5">{{ entry.note }}</p>
      }
    </div>
    <span class="text-sm font-medium text-green-600 shrink-0">{{ entry.value }}</span>
  </div>
}
```

**O que é `first:border-t-0`?**
> É um modificador do Tailwind que aplica `border-top: none` no **primeiro** item da lista. Isso evita que a linha divisória apareça acima do primeiro item — só queremos separadores entre itens, não antes do primeiro.

**O que é `min-w-0`?**
> `min-width: 0` é um hack de Flexbox. Por padrão, elementos flex têm `min-width: auto`, o que impede que descrições longas sejam truncadas. Com `min-w-0`, o elemento pode ser comprimido e o `truncate` funciona corretamente.

---

### Card 2: Saídas & Gastos (vermelho)

```html
@if (item.analysis.expenses) {
  ...
  @for (expense of item.analysis.expenses.items; track expense.category) {
    <div ...>
      <p class="text-sm font-medium text-foreground">{{ expense.category }}</p>
      <p class="text-xs text-muted-foreground mt-0.5">{{ expense.description }}</p>
      <span class="text-sm font-medium text-red-500 shrink-0">{{ expense.value }}</span>
    </div>
  }
}
```

A diferença de hierarquia visual entre as linhas: `category` em `font-medium` (destaque), `description` em `text-xs text-muted-foreground` (detalhe secundário). Essa hierarquia visual guia o olho do usuário.

---

### Cards 3 e 4: Insights e Conselhos (azul e âmbar)

```html
@if (item.analysis.insights?.length) {
  ...
  @for (insight of item.analysis.insights; track insight) {
    <li class="flex items-start gap-2 text-sm text-foreground">
      <span class="text-blue-500 mt-0.5 shrink-0">•</span>
      {{ insight }}
    </li>
  }
}
```

**Por que `insights?.length` e não apenas `insights`?**
> `insights` pode ser um array vazio `[]`, e em JavaScript `[]` é truthy (avaliado como `true`). Então `@if (insights)` renderizaria o card mesmo sem itens. `insights?.length` é falsy quando o array está vazio (length = 0) ou quando insights é null/undefined.

**Por que o bullet `•` é um `<span>` separado?**
> Para ter controle fino de alinhamento. Com `items-start` no container flex, o bullet fica alinhado ao topo do texto — mesmo quando o texto quebra em múltiplas linhas. O `mt-0.5` empurra o bullet levemente para baixo, alinhando-o visualmente ao centro da primeira linha de texto.

---

## 12. Fluxo completo do sistema

### Backend

```
POST /ocr/analyze (arquivo multipart)
  │
  └─► OcrController.analyzeDocument(file)
        │
        ├─► OcrService.extractText(file)
        │     │
        │     ├─► getValidatedExtension()  ← valida png/jpg/jpeg/pdf
        │     │
        │     ├─► [imagem] extractFromImage()
        │     │     └─► ImageIO.read() → BufferedImage
        │     │               └─► runTesseract() → texto bruto
        │     │
        │     ├─► [pdf] extractFromPdf()
        │     │     ├─► PDFTextStripper.getText() → texto embutido?
        │     │     │         SIM → retorna texto
        │     │     │         NÃO ↓
        │     │     └─► PDFRenderer (300dpi) por página → runTesseract() → texto
        │     │
        │     └─► PiiMaskingService.mask(textobruto) → texto mascarado
        │           │
        │           ├─► substitui CNPJ   → ******
        │           ├─► substitui CPF    → ******
        │           ├─► substitui RG     → ******
        │           ├─► substitui Cartão → ******
        │           ├─► substitui Email  → ******
        │           └─► substitui Telefone → ******
        │
        └─► GroqService.analyze(textoMascarado)
              │
              ├─► buildPrompt(texto) → instrução + texto
              │
              ├─► RestClient.post(groq.com/...) → envia JSON com model + prompt
              │
              └─► parseGroqResponse(rawResponse)
                    ├─► objectMapper.readTree() → navega choices[0].message.content
                    └─► objectMapper.readValue() → FinancialAnalysisDTO

  Resposta JSON:
  {
    "entries": { "total": "R$ X", "classNote": "...", "items": [...] },
    "expenses": { "total": "R$ X", "items": [...] },
    "insights": ["..."],
    "advice": ["..."]
  }
```

### Frontend

```
Usuário seleciona arquivo
  │
  └─► Dashboard.analyzeFiles()
        │
        ├─► forkJoin([ocrService.analyzeDocument(file), ...])
        │     └─► POST /ocr/analyze (multipart/form-data)
        │               └─► aguarda FinancialAnalysis JSON
        │
        └─► results.set([{ fileName, analysis }])
              │
              └─► Template re-renderiza automaticamente (Signal)
                    │
                    ├─► @if (entries)  → Card verde  (Entradas & Receitas)
                    ├─► @if (expenses) → Card vermelho (Saídas & Gastos)
                    ├─► @if (insights?.length) → Card azul (Destaques e Insights)
                    └─► @if (advice?.length)   → Card âmbar (Conselho do Analista)
```

### Erros e seus status HTTP

```
Arquivo inválido (não é pdf/png/jpg) → OcrException → 422 Unprocessable Entity
Arquivo corrompido / OCR falha       → OcrException → 422 Unprocessable Entity
Groq: rede, chave inválida, timeout  → GroqException → 502 Bad Gateway
Groq: JSON malformado na resposta    → GroqException → 502 Bad Gateway
```

---

## Conceitos-chave para revisar

Se algum trecho ficou obscuro, estes são os tópicos para aprofundar:

| Conceito | Onde aparece no código |
|---|---|
| `@Value` e externalização de config | `OcrService`, `GroqService` |
| `try-with-resources` | `extractFromPdf()` |
| Thread-safety | `runTesseract()` — nova instância a cada chamada |
| `static final Pattern` — pré-compilação de regex | `PiiMaskingService` |
| Character classes `[]`, quantifiers `{}`, grupos `()` | todos os Patterns |
| Word boundary `\b` | Pattern CARD |
| Backreference `$1` no replaceAll | `RG_LABELED_7` |
| Java Records — DTOs sem boilerplate | `FinancialAnalysisDTO` |
| Injeção por construtor vs `@Autowired` | `OcrService`, `GroqService`, `OcrController` |
| `RestClient` fluent API | `GroqService.analyze()` |
| `RestClient.create()` — fábrica estática sem injeção do Builder | `GroqService` construtor |
| Jackson 3.x — pacote `tools.jackson` (Boot 4.x) | `GroqService` imports |
| `JacksonException` (unchecked) — substitui `JsonProcessingException` | `GroqService.parseGroqResponse()` |
| `asString()` — substitui `asText()` depreciado no Jackson 3.x | `GroqService.parseGroqResponse()` |
| `JsonNode` — navegação em árvore JSON | `GroqService.parseGroqResponse()` |
| Prompt Engineering — `response_format: json_object` | `GroqService.buildPrompt()` |
| Text Block `"""` | `GroqService.buildPrompt()` |
| Status HTTP semânticos (422 vs 502) | `RestExceptionHandler` |
| TypeScript `interface` e union types `\|` | `ocr.service.ts` |
| `forkJoin` — requests paralelos | `dashboard.ts` |
| Angular Signals — `signal()`, `.set()`, `()` | `dashboard.ts` |
| `@if`, `@for`, `track` — controle de fluxo Angular 17+ | `dashboard.html` |
| `?.length` — truthy/falsy com arrays | `dashboard.html` |
| `min-w-0` — hack de truncate em Flexbox | `dashboard.html` |
| `first:border-t-0` — pseudo-seletor no Tailwind | `dashboard.html` |

---

---

# Persistência de Insights — Histórico de Análises por Usuário

> Esta seção é **independente** da parte de OCR acima.
> Ela explica como o resultado da IA passou a ser **salvo no banco de dados**,
> vinculado ao usuário autenticado, para que ele possa consultar seu histórico depois.
> Tópicos cobertos: relacionamento entre tabelas, serialização/desserialização JSON,
> DTOs de resposta e como o Spring injeta o usuário logado nos controllers.

---

## Índice desta seção

1. [O problema — por que persistir os insights?](#p1-o-problema--por-que-persistir-os-insights)
2. [Entidade Insight — a tabela nova no banco](#p2-entidade-insight--a-tabela-nova-no-banco)
3. [Relacionamento ManyToOne — ligando Insight ao User](#p3-relacionamento-manytoone--ligando-insight-ao-user)
4. [Por que guardar o JSON como TEXT?](#p4-por-que-guardar-o-json-como-text)
5. [Serialização — de objeto Java para String JSON](#p5-serialização--de-objeto-java-para-string-json)
6. [Desserialização — de String JSON de volta para objeto](#p6-desserialização--de-string-json-de-volta-para-objeto)
7. [InsightResponseDTO — o que o frontend recebe](#p7-insightresponsedto--o-que-o-frontend-recebe)
8. [InsightRepository — consulta por usuário](#p8-insightrepository--consulta-por-usuário)
9. [InsightService — orquestrando tudo](#p9-insightservice--orquestrando-tudo)
10. [InsightController — AuthenticationPrincipal](#p10-insightcontroller--authenticationprincipal)
11. [OcrController modificado — salvando após análise](#p11-ocrcontroller-modificado--salvando-após-análise)
12. [Fluxo completo com persistência](#p12-fluxo-completo-com-persistência)
13. [Conceitos para aprofundar](#p13-conceitos-para-aprofundar)

---

## P1. O problema — por que persistir os insights?

Antes dessa feature, o fluxo era **sem memória**:

```
Usuário envia documento → OCR → IA → resposta → fim.
```

Se o usuário fechar a página ou quiser rever a análise de ontem, **perdeu tudo**.

A solução é guardar a resposta no banco vinculada ao usuário logo após a IA responder.
Assim o frontend pode chamar `GET /insights` e listar todo o histórico.

```
POST /ocr/analyze
  └── OCR → IA → salva no banco → retorna FinancialAnalysisDTO

GET /insights
  └── busca no banco por usuário → retorna histórico ordenado
```

---

## P2. Entidade Insight — a tabela nova no banco

```java
@Entity
@Table(name = "insights")
public class Insight {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String analysisJson;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
```

Como o projeto usa `spring.jpa.hibernate.ddl-auto=update`, o Hibernate cria a tabela
automaticamente ao subir a aplicação — sem precisar escrever SQL manualmente:

```sql
CREATE TABLE insights (
    id            BIGINT       AUTO_INCREMENT PRIMARY KEY,
    user_id       INT          NOT NULL,
    analysis_json TEXT         NOT NULL,
    created_at    DATETIME(6)  NOT NULL,
    FOREIGN KEY (user_id) REFERENCES users(id)
);
```

**Por que `Long` para o id e não `Integer` como em `User`?**
`User` usa `Integer` porque a quantidade de usuários é limitada.
`Insight` usa `Long` porque cada usuário pode acumular muitas análises com o tempo —
`Long` aguenta ~9,2 quintilhões de registros vs ~2,1 bilhões do `Integer`.

**`@PrePersist`:**
Callback do JPA executado **automaticamente antes de cada INSERT**.
O JPA chama `prePersist()` sozinho — não precisamos setar `createdAt` na mão em nenhum lugar.

---

## P3. Relacionamento ManyToOne — ligando Insight ao User

O relacionamento entre as tabelas é:

```
users (1) <-------- (N) insights
```

Um usuário pode ter **muitos** insights. Cada insight pertence a **um** usuário.
Isso se chama **Many-to-One** (muitos insights → um usuário).

```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "user_id", nullable = false)
private User user;
```

| Parte da anotação | O que faz |
|---|---|
| `@ManyToOne` | Diz ao JPA que N registros desta tabela apontam para 1 registro em outra |
| `fetch = FetchType.LAZY` | Só carrega o `User` do banco quando o código realmente pedir — evita JOIN desnecessário |
| `@JoinColumn(name = "user_id")` | Define o nome da coluna FK na tabela `insights` |
| `nullable = false` | Garante que nenhum insight fique sem dono no banco |

**O lado `User` não precisa de `@OneToMany`:**
O mapeamento bidirecional (colocar `List<Insight>` dentro de `User`) só é necessário
se precisarmos chamar `user.getInsights()`. Como buscamos insights sempre pelo repository,
esse mapeamento seria peso sem benefício — então foi omitido.

---

## P4. Por que guardar o JSON como TEXT?

`FinancialAnalysisDTO` é um objeto complexo com listas aninhadas:

```
FinancialAnalysisDTO
├── entries
│   ├── total
│   ├── classNote
│   └── items  →  List<EntryItem>
├── expenses
│   ├── total
│   └── items  →  List<ExpenseItem>
├── insights   →  List<String>
└── advice     →  List<String>
```

Existem duas formas de guardar isso no banco:

**Opção A — Normalizar em múltiplas tabelas:**
Criar `insights_entries`, `insights_entry_items`, `insights_expenses`, etc.
Muitas tabelas, muitos JOINs, e qualquer mudança na estrutura da IA quebra o schema inteiro.

**Opção B — Serializar para JSON e guardar como uma coluna TEXT:**
```
analysis_json = '{"entries":{"total":"R$ 5.000,00",...},"insights":[...]}'
```
Um único campo, sem JOINs, e resiliente a mudanças na estrutura da IA.

A Opção B é a correta aqui porque:
- A análise é sempre lida como um bloco inteiro — nunca filtramos por campo interno
- A estrutura vem da IA e pode mudar sem exigir migração de banco
- `TEXT` no MySQL suporta até 65.535 bytes — bem mais que qualquer análise gerada

---

## P5. Serialização — de objeto Java para String JSON

**Serializar** = transformar um objeto Java em texto (String JSON) para guardar ou transmitir.

```java
insight.setAnalysisJson(objectMapper.writeValueAsString(analysis));
//                                   ^
//                       recebe o objeto Java, devolve uma String JSON
```

O que acontece internamente:

```
// Objeto Java na memória:
FinancialAnalysisDTO {
  entries: { total: "R$ 5.000,00", classNote: "Classe C", items: [...] },
  insights: ["Você gastou 64% da renda"],
  advice: ["Reduza gastos com cartão"]
}

// String JSON que vai para o banco:
{"entries":{"total":"R$ 5.000,00","classNote":"Classe C","items":[...]},"insights":["Você gastou 64% da renda"],...}
```

**`ObjectMapper` — quem é esse objeto?**

`ObjectMapper` é a classe central do **Jackson**, a biblioteca de JSON padrão do Java/Spring.
O Spring Boot cria e configura um `ObjectMapper` automaticamente como bean.
Ao declarar no construtor do service, o Spring injeta essa instância pronta:

```java
public InsightService(InsightRepository insightRepository, ObjectMapper objectMapper) {
    this.objectMapper = objectMapper; // Spring injeta o bean configurado pelo Boot
}
```

**Por que injetar em vez de `new ObjectMapper()`?**

| Criar com `new` | Injetar pelo Spring |
|---|---|
| Nova instância a cada chamada — caro | Uma instância reutilizada para toda a aplicação |
| Sem configurações do Boot (datas, Records...) | Já vem configurado com módulos necessários |
| Não é thread-safe durante construção | Thread-safe após configuração |

**Jackson 3.x — pacote `tools.jackson`:**
Spring Boot 4.x usa Jackson 3, que mudou o pacote base de `com.fasterxml.jackson` para `tools.jackson`.
Por isso os imports no projeto são:

```java
// Jackson 2.x  (Spring Boot 3 e anteriores)
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException; // checked — compilador obriga tratar

// Jackson 3.x  (Spring Boot 4.x — este projeto)
import tools.jackson.databind.ObjectMapper;
import tools.jackson.core.JacksonException;               // unchecked — não obriga, mas ainda tratamos
```

A mudança de checked para unchecked significa que o compilador não nos obriga mais a capturar
o erro — mas ainda usamos `try/catch` para fornecer uma mensagem clara em vez de stack trace genérica.

---

## P6. Desserialização — de String JSON de volta para objeto

**Desserializar** = transformar a String JSON salva no banco de volta em objeto Java.

```java
FinancialAnalysisDTO analysis = objectMapper.readValue(
    insight.getAnalysisJson(),    // String do banco: {"entries":{...},...}
    FinancialAnalysisDTO.class    // Tipo alvo: qual classe instanciar
);
```

Jackson lê a String, identifica cada chave do JSON e **preenche os campos** do DTO correspondente.

**Como Jackson desserializa um Java Record se ele não tem setters?**

Records são imutáveis — todos os campos são definidos no construtor:

```java
public record FinancialAnalysisDTO(EntriesSection entries, ExpensesSection expenses, ...) {}
//                                  ^               ^
//                         Jackson precisa saber os nomes desses parâmetros
```

Jackson usa o **construtor canônico** do Record para instanciá-lo.
Para isso, precisa dos **nomes dos parâmetros** em tempo de execução.
O Spring Boot configura o compilador com a flag `-parameters`, que preserva esses nomes no bytecode.
Sem essa flag, o Jackson veria `arg0`, `arg1`... e a desserialização falharia silenciosamente.

---

## P7. InsightResponseDTO — o que o frontend recebe

```java
public record InsightResponseDTO(
    Long id,
    LocalDateTime createdAt,
    FinancialAnalysisDTO analysis
) {}
```

O frontend não precisa saber que existe um campo `analysisJson` — isso é detalhe interno do banco.
O `InsightResponseDTO` entrega o objeto já desserializado e pronto para exibir.

Exemplo do `GET /insights`:

```json
[
  {
    "id": 3,
    "createdAt": "2026-03-16T14:32:00",
    "analysis": {
      "entries": { "total": "R$ 5.000,00", "classNote": "Classe C", "items": [] },
      "expenses": { "total": "R$ 3.200,00", "items": [] },
      "insights": ["Você gastou 64% da renda em despesas fixas"],
      "advice": ["Reduza gastos com cartão de crédito"]
    }
  },
  {
    "id": 1,
    "createdAt": "2026-03-10T09:15:00",
    "analysis": { ... }
  }
]
```

Os resultados chegam do **mais recente para o mais antigo** — a análise de hoje aparece primeiro.

---

## P8. InsightRepository — consulta por usuário

```java
public interface InsightRepository extends JpaRepository<Insight, Long> {
    List<Insight> findByUserOrderByCreatedAtDesc(User user);
}
```

Spring Data JPA **gera a query SQL automaticamente** interpretando o nome do método como uma frase:

```
findBy  User  OrderBy  CreatedAt  Desc
  |      |       |         |        |
SELECT * FROM insights
WHERE user_id = ?
ORDER BY created_at DESC
```

Zero SQL escrito manualmente. O Spring lê a convenção de nomenclatura
e cria toda a implementação em tempo de execução via proxy dinâmico.

---

## P9. InsightService — orquestrando tudo

```java
@Service
public class InsightService {

    // SALVAR — chamado pelo OcrController após receber resposta da IA
    public Insight save(User user, FinancialAnalysisDTO analysis) {
        try {
            Insight insight = new Insight();
            insight.setUser(user);
            insight.setAnalysisJson(objectMapper.writeValueAsString(analysis)); // serializa
            return insightRepository.save(insight); // INSERT no banco
        } catch (JacksonException e) {
            throw new RuntimeException("Erro ao serializar análise para JSON", e);
        }
    }

    // BUSCAR — chamado pelo InsightController no GET /insights
    public List<InsightResponseDTO> findByUser(User user) {
        return insightRepository.findByUserOrderByCreatedAtDesc(user)
                .stream()
                .map(this::toResponseDTO) // desserializa cada linha do banco
                .toList();
    }

    // Converte entidade (banco) → DTO (resposta HTTP)
    private InsightResponseDTO toResponseDTO(Insight insight) {
        try {
            FinancialAnalysisDTO analysis = objectMapper.readValue(
                    insight.getAnalysisJson(),
                    FinancialAnalysisDTO.class  // desserializa
            );
            return new InsightResponseDTO(insight.getId(), insight.getCreatedAt(), analysis);
        } catch (JacksonException e) {
            throw new RuntimeException("Erro ao desserializar análise do banco", e);
        }
    }
}
```

O fluxo de dados dentro do service:

```
save():
  FinancialAnalysisDTO  -->  writeValueAsString()  -->  String JSON  -->  INSERT no banco

findByUser():
  SELECT do banco  -->  String JSON  -->  readValue()  -->  FinancialAnalysisDTO  -->  InsightResponseDTO
```

---

## P10. InsightController — AuthenticationPrincipal

```java
@GetMapping
public ResponseEntity<List<InsightResponseDTO>> getMyInsights(@AuthenticationPrincipal User user) {
    return ResponseEntity.ok(insightService.findByUser(user));
}
```

**Como `@AuthenticationPrincipal` sabe qual usuário injetar?**

Toda requisição autenticada passa pelo `SecurityFilter` antes de chegar ao controller.
O filter valida o JWT, carrega o `User` do banco e o coloca no `SecurityContext`:

```java
// Dentro do SecurityFilter (simplificado):
var login = tokenService.validateToken(token);
var user  = userRepository.findByLogin(login);

SecurityContextHolder.getContext().setAuthentication(
    new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities())
//                                           ^
//                               isso é o "principal" — o objeto User completo
);
```

`@AuthenticationPrincipal` é um atalho para extrair esse principal e injetá-lo no método.
Sem a anotação, o equivalente seria:

```java
// Equivalente manual — mais verboso
User user = (User) SecurityContextHolder.getContext()
                       .getAuthentication()
                       .getPrincipal();
```

O resultado é que **cada usuário vê apenas seus próprios insights** sem nenhuma lógica extra —
o isolamento é garantido pelo sistema de autenticação.

---

## P11. OcrController modificado — salvando após análise

```java
@PostMapping("/analyze")
public ResponseEntity<FinancialAnalysisDTO> analyzeDocument(
        @RequestParam("file") MultipartFile file,
        @AuthenticationPrincipal User user) {

    String maskedText = ocrService.extractText(file);
    FinancialAnalysisDTO analysis = groqService.analyze(maskedText);

    insightService.save(user, analysis); // persiste antes de retornar

    return ResponseEntity.ok(analysis);
}
```

O frontend **não percebe nenhuma diferença** — continua recebendo o mesmo `FinancialAnalysisDTO`.
O `insightService.save()` é um efeito colateral transparente: salva no banco e segue em frente.

---

## P12. Fluxo completo com persistência

```
[Frontend]
    |
    |  POST /ocr/analyze  (arquivo + Bearer token no header)
    v
[SecurityFilter]
    |  valida JWT → carrega User do banco → coloca no SecurityContext
    v
[OcrController]
    |
    +---> OcrService.extractText()
    |         Tesseract → PiiMaskingService → texto mascarado
    |
    +---> GroqService.analyze()
    |         POST Groq API → parse JSON → FinancialAnalysisDTO
    |
    +---> InsightService.save(user, analysis)
    |         writeValueAsString() → String JSON
    |         INSERT INTO insights (user_id, analysis_json, created_at)
    |
    +---> ResponseEntity.ok(analysis) ------> [Frontend] exibe os cards
                                                   |
                                                   |  GET /insights  (Bearer token)
                                                   v
                                             [InsightController]
                                                   |
                                             InsightService.findByUser(user)
                                                   |  SELECT WHERE user_id = ? ORDER BY created_at DESC
                                                   |  readValue() para cada linha
                                                   v
                                             List<InsightResponseDTO> --> [Frontend] histórico
```

---

## P13. Conceitos para aprofundar

| Conceito | Onde aparece no código |
|---|---|
| `@Entity` e `@Table` — mapeamento objeto-relacional | `Insight.java` |
| `@Id` e `@GeneratedValue(IDENTITY)` — PK auto-incremental | `Insight.java` |
| `@ManyToOne` e `@JoinColumn` — FK entre tabelas | `Insight.java` |
| `FetchType.LAZY` vs `EAGER` — quando o JOIN acontece | `Insight.java` |
| `@PrePersist` — callback executado antes do INSERT | `Insight.java` |
| `ddl-auto=update` — Hibernate cria tabelas automaticamente | `application.properties` |
| `TEXT` vs `VARCHAR` no MySQL — limite de tamanho da coluna | `columnDefinition = "TEXT"` |
| Serialização — `writeValueAsString()` objeto → String JSON | `InsightService.save()` |
| Desserialização — `readValue()` String JSON → objeto | `InsightService.toResponseDTO()` |
| `ObjectMapper` thread-safe — por que injetar em vez de `new` | construtor do `InsightService` |
| Jackson 3.x — pacote `tools.jackson` (Spring Boot 4.x) | todos os imports Jackson |
| `JacksonException` unchecked — substitui `JsonProcessingException` | `catch` no `InsightService` |
| Java Records e desserialização — flag `-parameters` do compilador | `FinancialAnalysisDTO` |
| Spring Data — nome do método como query (`findByUserOrderBy...`) | `InsightRepository` |
| `@AuthenticationPrincipal` — extrai o usuário do SecurityContext | `InsightController`, `OcrController` |
| `SecurityContext` — memória da requisição atual no Spring Security | `SecurityFilter` → controllers |
| Controller não acessa Repository direto — separação de camadas | toda a arquitetura do projeto |