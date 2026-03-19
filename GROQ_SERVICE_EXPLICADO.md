# GroqService — Guia Completo para Devs Júnior

> Este documento explica **cada linha** do `GroqService.java`, o raciocínio por trás de cada
> decisão de design e os conceitos de Java e arquitetura que aparecem no caminho.
> O foco é o **porquê**, não apenas o que o código faz.

---

## Índice

1. [O que o GroqService faz?](#1-o-que-o-groqservice-faz)
2. [Imports — por que cada um está aqui](#2-imports--por-que-cada-um-está-aqui)
3. [Campos e constantes](#3-campos-e-constantes)
4. [Construtor — injeção vs instanciação direta](#4-construtor--injeção-vs-instanciação-direta)
5. [analyze() — o método principal](#5-analyze--o-método-principal)
6. [Map.of() — construindo o corpo da requisição](#6-mapof--construindo-o-corpo-da-requisição)
7. [RestClient — a chamada HTTP](#7-restclient--a-chamada-http)
8. [parseGroqResponse() — navegando o JSON da IA](#8-parsegroqresponse--navegando-o-json-da-ia)
9. [Por que a IA não deve fazer cálculos?](#9-por-que-a-ia-não-deve-fazer-cálculos)
10. [recalcularTotais() — Java assume a aritmética](#10-recalculartotais--java-assume-a-aritmética)
11. [Stream + map + reduce — passo a passo](#11-stream--map--reduce--passo-a-passo)
12. [Por que BigDecimal e não double?](#12-por-que-bigdecimal-e-não-double)
13. [parseMoeda() — de String para número](#13-parsemoeda--de-string-para-número)
14. [formatMoeda() — de número para String](#14-formatmoeda--de-número-para-string)
15. [Records imutáveis — como criar nova instância](#15-records-imutáveis--como-criar-nova-instância)
16. [buildPrompt() — engenharia de prompt](#16-buildprompt--engenharia-de-prompt)
17. [Tratamento de erros — GroqException](#17-tratamento-de-erros--groqexception)
18. [Fluxo completo do método analyze()](#18-fluxo-completo-do-método-analyze)
19. [Conceitos para aprofundar](#19-conceitos-para-aprofundar)

---

## 1. O que o GroqService faz?

É o serviço responsável por **enviar o texto do documento para a IA** e **devolver a análise
financeira estruturada** para o resto da aplicação.

Mas há uma divisão de responsabilidades importante nele:

```
Groq API (LLM Llama 3.3)          GroqService (Java)
────────────────────────          ──────────────────
Ler o texto → identificar          Montar a requisição HTTP
  lançamentos financeiros           Serializar o corpo em JSON
Categorizar gastos                 Desserializar a resposta
Identificar padrões                Calcular totais com BigDecimal
Gerar insights qualitativos        Formatar valores em R$
Dar conselhos                      Persistir no banco (via InsightService)
```

A IA faz o que ela faz bem: **linguagem e raciocínio qualitativo**.
O Java faz o que ele faz bem: **aritmética exata**.

---

## 2. Imports — por que cada um está aqui

```java
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
```
Jackson 3.x (Spring Boot 4.x mudou o pacote de `com.fasterxml` para `tools.jackson`).
- `ObjectMapper` — serializa/desserializa JSON
- `JsonNode` — permite navegar em uma árvore JSON sem uma classe mapeada
- `JacksonException` — exceção unchecked para erros de parse JSON

```java
import java.math.BigDecimal;
import java.math.RoundingMode;
```
Para somas financeiras precisas. Detalhado na seção 12.

```java
import java.text.NumberFormat;
import java.util.Locale;
```
Para formatar o número no padrão brasileiro (R$ 1.234,56).

```java
import java.util.List;
import java.util.Map;
```
Para construir o corpo da requisição HTTP sem criar classes auxiliares.

---

## 3. Campos e constantes

```java
@Value("${groq.api.key}")
private String apiKey;
```

`@Value` é uma anotação do Spring que **injeta o valor de uma propriedade** do
`application.properties` diretamente no campo.

Quando o Spring inicializa o bean, ele lê:
```properties
groq.api.key=gsk_3Wp8cII...
```
e coloca essa String dentro de `apiKey`. O código nunca vê a chave hardcoded.

```java
private static final String GROQ_URL = "https://api.groq.com/openai/v1/chat/completions";
private static final String MODEL = "llama-3.3-70b-versatile";
```

`static final` = constante de classe. `static` significa que existe uma única cópia
compartilhada por todas as instâncias (não precisa de objeto criado para existir).
`final` significa que o valor nunca muda após a atribuição.
Usar constantes nomeadas evita "magic strings" espalhadas pelo código.

---

## 4. Construtor — injeção vs instanciação direta

```java
private final RestClient restClient;
private final ObjectMapper objectMapper;

public GroqService(ObjectMapper objectMapper) {
    this.restClient = RestClient.create();  // instanciação direta
    this.objectMapper = objectMapper;       // injeção pelo Spring
}
```

**Por que `ObjectMapper` é injetado mas `RestClient` é criado diretamente?**

`ObjectMapper` precisa das configurações que o Spring Boot aplica automaticamente
(suporte a Java Records, módulo de datas Java 8, etc.).
Se criássemos `new ObjectMapper()`, perderíamos essas configurações.

`RestClient.create()` cria uma instância padrão sem estado — não há configurações
globais importantes aqui, então a fábrica estática é suficiente e mais simples.

**Por que `final` nos campos?**
Garante que nenhum método poderá trocar a referência depois da construção.
É uma boa prática para campos que não devem mudar ao longo da vida do objeto.

---

## 5. analyze() — o método principal

```java
public FinancialAnalysisDTO analyze(String maskedText) {
    Map<String, Object> requestBody = Map.of(...);

    try {
        String rawResponse = restClient.post()
                ...
                .body(String.class);

        return recalcularTotais(parseGroqResponse(rawResponse));

    } catch (RestClientException e) {
        throw new GroqException("Falha na comunicação com a IA: " + e.getMessage());
    }
}
```

O método faz três coisas em sequência:

```
1. Montar o corpo da requisição (Map.of)
2. Enviar via HTTP e receber a resposta bruta (RestClient)
3. Parsear + recalcular totais (parseGroqResponse + recalcularTotais)
```

Note a composição na linha de retorno:
```java
return recalcularTotais(parseGroqResponse(rawResponse));
```
`parseGroqResponse` roda primeiro (de dentro para fora), devolve um `FinancialAnalysisDTO`,
e esse resultado é passado direto para `recalcularTotais`. Uma linha, dois passos.

---

## 6. Map.of() — construindo o corpo da requisição

```java
Map<String, Object> requestBody = Map.of(
    "model",           MODEL,
    "response_format", Map.of("type", "json_object"),
    "messages",        List.of(Map.of("role", "user", "content", buildPrompt(maskedText)))
);
```

`Map.of()` cria um **Map imutável** com os pares chave-valor fornecidos — sem precisar
de `new HashMap<>()` e múltiplos `.put()`.

O resultado em JSON (que o RestClient serializa automaticamente) é:

```json
{
  "model": "llama-3.3-70b-versatile",
  "response_format": { "type": "json_object" },
  "messages": [
    { "role": "user", "content": "Você é um analista financeiro..." }
  ]
}
```

**`response_format: json_object`** é uma instrução para a API da Groq:
força o modelo a sempre retornar JSON válido — nunca texto livre ou markdown.
Sem isso, o LLM poderia responder com "Aqui está a análise: ```json {...}```"
e o parse quebraria.

**`Map<String, Object>`** — por que `Object` como tipo do valor?
Os valores têm tipos diferentes: `String` para "model", outro `Map` para "response_format",
uma `List` para "messages". `Object` é o tipo comum que engloba todos.

---

## 7. RestClient — a chamada HTTP

```java
String rawResponse = restClient.post()
        .uri(GROQ_URL)
        .header("Authorization", "Bearer " + apiKey)
        .contentType(MediaType.APPLICATION_JSON)
        .body(requestBody)
        .retrieve()
        .body(String.class);
```

`RestClient` usa uma **API fluente** — cada método retorna o próprio objeto,
permitindo encadear chamadas em sequência. A leitura fica quase como uma frase:

```
faça um POST
  para essa URL
  com esse header
  com esse content-type
  com esse corpo
  recupere a resposta
  como String
```

**Por que receber como `String` e não direto como `FinancialAnalysisDTO`?**

A resposta da Groq não é o DTO direto. Ela tem um envelope assim:

```json
{
  "id": "chatcmpl-abc123",
  "choices": [
    {
      "message": {
        "role": "assistant",
        "content": "{\"entries\":{...},\"expenses\":{...}}"
      }
    }
  ]
}
```

O `FinancialAnalysisDTO` está **dentro** do campo `choices[0].message.content`,
como uma String JSON dentro de outro JSON. Por isso precisamos parsear em duas etapas.

**`Bearer` no header:**
É o esquema de autenticação HTTP mais comum para APIs.
`Authorization: Bearer <token>` diz ao servidor "sou eu, aqui está meu token".

---

## 8. parseGroqResponse() — navegando o JSON da IA

```java
private FinancialAnalysisDTO parseGroqResponse(String rawResponse) {
    try {
        JsonNode root = objectMapper.readTree(rawResponse);
        String content = root.path("choices").get(0).path("message").path("content").asString();
        return objectMapper.readValue(content, FinancialAnalysisDTO.class);
    } catch (JacksonException | NullPointerException e) {
        throw new GroqException("Falha ao interpretar a resposta da IA: " + e.getMessage());
    }
}
```

**`readTree()`** — carrega o JSON em uma estrutura de árvore (`JsonNode`) sem precisar
de uma classe mapeada. Útil quando o JSON tem uma estrutura que não nos pertence (vem de API externa).

**Navegação na árvore:**

```java
root.path("choices")   // nó "choices" (retorna MissingNode se não existir, nunca null)
    .get(0)            // primeiro elemento do array
    .path("message")   // nó "message"
    .path("content")   // nó "content"
    .asString()        // valor como String (Jackson 3.x — substitui asText() depreciado)
```

`path()` vs `get()`:
- `path("chave")` nunca lança exceção — retorna um `MissingNode` se a chave não existir
- `get(0)` retorna `null` se o array estiver vazio — por isso capturamos `NullPointerException`

**Segunda desserialização:**
```java
return objectMapper.readValue(content, FinancialAnalysisDTO.class);
```
`content` é uma String JSON (`"{\"entries\":{...}}"`) e `readValue` a converte
para um objeto `FinancialAnalysisDTO` — preenchendo cada campo pelo nome da chave.

---

## 9. Por que a IA não deve fazer cálculos?

LLMs (Large Language Models) são modelos de linguagem — eles **preveem tokens**
(pedaços de texto), não executam aritmética de verdade.

Exemplo do problema:

```
Texto: "Débito R$ 27,68 — Débito R$ 27,68 — Débito R$ 27,68"

LLM calcula: R$ 82,04  ← errado (erro de arredondamento interno)
Java calcula: R$ 83,04 ← correto (27.68 × 3 = 83.04)
```

O modelo pode errar em:
- Multiplicações com centavos
- Somas longas (10+ itens)
- Arredondamentos intermediários

Por isso o prompt instrui explicitamente:
```
"Sempre defina 'total' como null — ele será calculado pelo sistema"
```

E o Java recalcula os totais com `BigDecimal` após receber a resposta.

---

## 10. recalcularTotais() — Java assume a aritmética

```java
private FinancialAnalysisDTO recalcularTotais(FinancialAnalysisDTO dto) {
    FinancialAnalysisDTO.EntriesSection entries = dto.entries();
    FinancialAnalysisDTO.ExpensesSection expenses = dto.expenses();

    FinancialAnalysisDTO.EntriesSection entriesRecalc = entries;
    if (entries != null && entries.items() != null) {
        BigDecimal total = entries.items().stream()
                .map(item -> parseMoeda(item.value()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        entriesRecalc = new FinancialAnalysisDTO.EntriesSection(
                formatMoeda(total), entries.classNote(), entries.items());
    }

    // mesmo padrão para expenses...

    return new FinancialAnalysisDTO(entriesRecalc, expensesRecalc, dto.insights(), dto.advice());
}
```

O método:
1. Pega os itens da IA (que estão corretos — ela é boa em identificar)
2. Soma os valores com `BigDecimal` (precisão garantida)
3. Devolve um novo DTO com o total correto substituído

A verificação `entries != null && entries.items() != null` protege contra documentos
onde a IA não encontrou entradas ou a lista veio vazia.

---

## 11. Stream + map + reduce — passo a passo

Esta é a parte mais densa para quem está começando. Vamos dissecar linha por linha:

```java
BigDecimal total = entries.items().stream()
        .map(item -> parseMoeda(item.value()))
        .reduce(BigDecimal.ZERO, BigDecimal::add);
```

**`.stream()`**
Converte a `List<EntryItem>` em um Stream — uma sequência de elementos que podem
ser processados com operações encadeadas. O Stream não modifica a lista original.

```
List<EntryItem> → Stream<EntryItem>

[ EntryItem("Salário", "R$ 5.000,00", null),
  EntryItem("Freelance", "R$ 800,00", null) ]
```

**`.map(item -> parseMoeda(item.value()))`**
`map` transforma cada elemento do Stream em outro tipo.
A função `item -> parseMoeda(item.value())` recebe um `EntryItem` e devolve um `BigDecimal`.

```
Stream<EntryItem>  →  Stream<BigDecimal>

[ "R$ 5.000,00", "R$ 800,00" ]
     ↓ parseMoeda     ↓ parseMoeda
[ 5000.00,        800.00     ]
```

`item -> parseMoeda(item.value())` é uma **lambda** — uma função anônima.
`item` é o parâmetro (o ElementItem atual), `parseMoeda(item.value())` é o que retorna.

**`.reduce(BigDecimal.ZERO, BigDecimal::add)`**
`reduce` "reduz" todos os elementos do Stream a um único valor, acumulando.

```
BigDecimal.ZERO   ← valor inicial (acumulador começa em 0)
BigDecimal::add   ← operação: soma o acumulador com o próximo elemento
```

Passo a passo com os valores acima:

```
acumulador = 0,00
passo 1: acumulador = 0,00 + 5000,00 = 5000,00
passo 2: acumulador = 5000,00 + 800,00 = 5800,00
resultado final: 5800,00
```

`BigDecimal::add` é uma **method reference** — equivale à lambda `(a, b) -> a.add(b)`.
O `::` é uma forma curta de referenciar um método existente.

**O pipeline completo visualizado:**

```
entries.items()
  ["Salário R$ 5.000,00", "Freelance R$ 800,00"]
        |
        | .stream()
        ↓
  Stream<EntryItem>
        |
        | .map(item -> parseMoeda(item.value()))
        ↓
  Stream<BigDecimal>  [5000.00, 800.00]
        |
        | .reduce(ZERO, BigDecimal::add)
        ↓
  BigDecimal 5800.00
```

---

## 12. Por que BigDecimal e não double?

Este é um dos erros mais clássicos em sistemas financeiros. Observe:

```java
// Com double (tipo primitivo de ponto flutuante)
double a = 0.1;
double b = 0.2;
System.out.println(a + b); // imprime: 0.30000000000000004 ← ERRADO

// Com BigDecimal (precisão decimal exata)
BigDecimal a = new BigDecimal("0.1");
BigDecimal b = new BigDecimal("0.2");
System.out.println(a.add(b)); // imprime: 0.3 ← CORRETO
```

**Por que double falha?**
`double` usa representação binária (base 2). Alguns decimais (como 0.1) não têm
representação binária exata — assim como 1/3 não tem representação decimal exata.
O computador armazena uma aproximação, e o erro se acumula nas operações.

**`BigDecimal` resolve isso** armazenando o número como inteiro + escala decimal,
sem conversão para binário. É mais lento e verboso, mas financeiramente correto.

**`BigDecimal.ZERO`** — constante pré-definida que representa 0.
Equivale a `new BigDecimal("0")`, mas mais eficiente pois é reutilizada.

**`RoundingMode.HALF_UP`** — modo de arredondamento "comercial":
- 2.5 → 3 (arredonda para cima)
- 2.4 → 2 (arredonda para baixo)

É o arredondamento que as pessoas esperam no dia a dia (diferente do `HALF_EVEN`
usado em contextos bancários mais precisos).

---

## 13. parseMoeda() — de String para número

```java
private BigDecimal parseMoeda(String valor) {
    if (valor == null || valor.isBlank()) return BigDecimal.ZERO;
    String limpo = valor
            .replace("R$", "")
            .replaceAll("\\s", "")
            .replace(".", "")
            .replace(",", ".");
    try {
        return new BigDecimal(limpo);
    } catch (NumberFormatException e) {
        return BigDecimal.ZERO;
    }
}
```

A IA retorna valores como `"R$ 1.234,56"`. Precisamos transformar isso em
`BigDecimal(1234.56)` para poder somar. A cadeia de `replace` faz isso:

```
"R$ 1.234,56"
    ↓ .replace("R$", "")
" 1.234,56"
    ↓ .replaceAll("\\s", "")    ← regex: remove qualquer espaço/tab/newline
"1.234,56"
    ↓ .replace(".", "")         ← remove separador de milhar
"1234,56"
    ↓ .replace(",", ".")        ← vírgula decimal → ponto (padrão do BigDecimal)
"1234.56"
    ↓ new BigDecimal("1234.56")
1234.56
```

**Por que a ordem importa?**
Se invertêssemos os dois últimos passos:
- `replace(",", ".")` primeiro: `"1.234.56"` ← dois pontos, `new BigDecimal` lança exceção
- `replace(".", "")` depois: removeria o ponto decimal junto

A sequência correta é sempre: remover milhar → converter vírgula.

**`\\s` no `replaceAll`:**
`replaceAll` aceita **regex** (expressão regular). No regex, `\s` significa
"qualquer caractere de espaço em branco" (espaço, tab, newline).
Em Java, a barra precisa ser escapada dentro de String, então `\s` vira `\\s`.

**`try/catch NumberFormatException`:**
Se a IA retornar um valor inesperado como `"N/A"` ou `"—"`, a cadeia de replace
pode gerar uma String inválida para `BigDecimal`. O catch retorna `ZERO` em vez
de deixar a aplicação inteira quebrar por um valor inesperado.

---

## 14. formatMoeda() — de número para String

```java
private String formatMoeda(BigDecimal valor) {
    NumberFormat nf = NumberFormat.getNumberInstance(Locale.of("pt", "BR"));
    nf.setMinimumFractionDigits(2);
    nf.setMaximumFractionDigits(2);
    return "R$ " + nf.format(valor.setScale(2, RoundingMode.HALF_UP));
}
```

O caminho inverso: `BigDecimal(5800.00)` → `"R$ 5.800,00"`.

**`NumberFormat.getNumberInstance(Locale.of("pt", "BR"))`**
Cria um formatador de números no padrão brasileiro:
- Separador de milhar: `.` (ponto)
- Separador decimal: `,` (vírgula)

`Locale.of("pt", "BR")` é a API moderna do Java 19+ para criar Locales.
O construtor `new Locale("pt", "BR")` foi depreciado no Java 19.

**`setMinimumFractionDigits(2)` e `setMaximumFractionDigits(2)`**
Garante que sempre haverá exatamente 2 casas decimais.
Sem isso: `5800` seria formatado como `"5.800"` em vez de `"5.800,00"`.

**`valor.setScale(2, RoundingMode.HALF_UP)`**
`setScale` define quantas casas decimais o `BigDecimal` deve ter.
Se o valor tiver mais casas (ex: `5800.005`), arredonda usando `HALF_UP`.

**Resultado visual:**

```
BigDecimal(5800.00)
    ↓ setScale(2, HALF_UP)
5800.00
    ↓ nf.format(...)
"5.800,00"
    ↓ "R$ " + ...
"R$ 5.800,00"
```

---

## 15. Records imutáveis — como criar nova instância

`FinancialAnalysisDTO` é um Java Record:

```java
public record FinancialAnalysisDTO(
    EntriesSection entries,
    ExpensesSection expenses,
    List<String> insights,
    List<String> advice
) {}
```

Records são **imutáveis por design** — não há setters. Uma vez criado, nenhum campo
pode ser alterado. Isso é intencional: garante que o objeto não seja modificado
de forma inesperada em outra parte do código.

Para "alterar" um campo, a única opção é **criar um novo Record** com os valores desejados:

```java
// Queremos apenas trocar o campo "total" de entries — todo o resto permanece igual
entriesRecalc = new FinancialAnalysisDTO.EntriesSection(
        formatMoeda(total),      // ← total novo, calculado pelo Java
        entries.classNote(),     // ← mantém o classNote que a IA gerou
        entries.items()          // ← mantém os itens que a IA identificou
);
```

`entries.classNote()` e `entries.items()` são os **accessors** do Record —
equivalem a getters, mas sem o prefixo "get".

No final, um novo `FinancialAnalysisDTO` é criado com tudo atualizado:

```java
return new FinancialAnalysisDTO(
    entriesRecalc,   // seção de entradas com total corrigido
    expensesRecalc,  // seção de saídas com total corrigido
    dto.insights(),  // insights da IA — mantidos sem alteração
    dto.advice()     // conselhos da IA — mantidos sem alteração
);
```

---

## 16. buildPrompt() — engenharia de prompt

O prompt é o "contrato" entre o Java e a IA. Cada instrução existe por uma razão:

```
"Sua função é IDENTIFICAR e CATEGORIZAR os lançamentos — não calcular totais."
```
Define explicitamente o papel da IA. LLMs respondem melhor quando o papel é claro.

```
"Sempre defina 'total' como null — ele será calculado pelo sistema"
```
Impede que o modelo tente calcular. Mesmo que ele tente, `recalcularTotais` sobrescreve.

```
"Liste cada lançamento individualmente em items — nunca agrupe valores"
```
Garante que a lista de itens tenha granularidade suficiente para o Java somar corretamente.

```
"RETORNE APENAS o JSON abaixo, sem texto, comentário ou markdown"
```
Sem isso, o modelo pode responder com `Aqui está: \`\`\`json {...}\`\`\`` e o parse quebra.

**`"total": null` hardcoded no template do JSON:**
Mostrar o campo como `null` no exemplo é mais forte do que só explicar em texto —
o modelo imita a estrutura do exemplo.

**Text Block `"""`:**
Sintaxe do Java 15+ para Strings multilinha. Evita concatenação com `\n`.
O compilador trata o conteúdo como uma String normal após a compilação.

---

## 17. Tratamento de erros — GroqException

```java
} catch (RestClientException e) {
    throw new GroqException("Falha na comunicação com a IA: " + e.getMessage());
}
```

```java
} catch (JacksonException | NullPointerException e) {
    throw new GroqException("Falha ao interpretar a resposta da IA: " + e.getMessage());
}
```

**Por que relançar como `GroqException`?**

`RestClientException` e `JacksonException` são exceções de bibliotecas — dizem
"algo deu errado no HTTP" ou "o JSON é inválido". Elas não significam nada para
o controller que chamou `analyze()`.

`GroqException` é uma exceção de **domínio** — diz "a integração com a IA falhou".
O `RestExceptionHandler` sabe o que fazer com ela: retornar HTTP 502 Bad Gateway.

É o princípio de não vazar detalhes de implementação para as camadas superiores.

**`JacksonException | NullPointerException`** — multi-catch:
Captura dois tipos de exceção em um único bloco. `NullPointerException` cobre o caso
de `root.path("choices").get(0)` retornar `null` quando o array estiver vazio.

---

## 18. Fluxo completo do método analyze()

```
analyze("texto mascarado do documento")
    |
    | 1. Monta o corpo da requisição
    |    Map.of("model", MODEL, "messages", [...buildPrompt...])
    |
    | 2. Envia via HTTP POST para Groq API
    |    RestClient → Authorization: Bearer {apiKey}
    |
    | 3. Recebe String JSON bruta da Groq:
    |    {"choices":[{"message":{"content":"{\"entries\":{...}}"}}]}
    |
    | 4. parseGroqResponse()
    |    readTree() → navega até choices[0].message.content
    |    readValue() → converte o content em FinancialAnalysisDTO
    |                  (com totais null — a IA não calculou)
    |
    | 5. recalcularTotais()
    |    items.stream()
    |      .map(parseMoeda)         "R$ 27,68" → 27.68
    |      .reduce(ZERO, add)       0 + 27.68 + 27.68 + 27.68 = 83.04
    |    formatMoeda(83.04)         83.04 → "R$ 83,04"
    |    new EntriesSection(total corrigido, classNote, items)
    |    new FinancialAnalysisDTO(entriesRecalc, expensesRecalc, insights, advice)
    |
    ↓
FinancialAnalysisDTO com totais aritmeticamente corretos
```

---

## 19. Conceitos para aprofundar

| Conceito | Onde aparece |
|---|---|
| `@Value` — injeção de propriedades | `apiKey` |
| `static final` — constantes de classe | `GROQ_URL`, `MODEL` |
| `Map.of()` — Map imutável | `requestBody` |
| `List.of()` — List imutável | `messages` |
| `RestClient` fluent API | `analyze()` |
| `Bearer token` — autenticação HTTP | header `Authorization` |
| `response_format: json_object` — prompt constraint | `requestBody` |
| `JsonNode` — árvore JSON sem classe mapeada | `parseGroqResponse()` |
| `path()` vs `get()` — navegação segura em JsonNode | `parseGroqResponse()` |
| `asString()` — Jackson 3.x (substitui `asText()`) | `parseGroqResponse()` |
| Multi-catch `ExceptionA \| ExceptionB` | `parseGroqResponse()` |
| `BigDecimal` vs `double` — precisão financeira | `recalcularTotais()` |
| `BigDecimal.ZERO` — constante | `reduce` |
| `RoundingMode.HALF_UP` — arredondamento comercial | `formatMoeda()` |
| `.stream()` — pipeline funcional sobre coleções | `recalcularTotais()` |
| `.map()` — transforma cada elemento | `recalcularTotais()` |
| `.reduce()` — acumula todos em um valor | `recalcularTotais()` |
| Lambda `item -> parseMoeda(item.value())` | `.map()` |
| Method reference `BigDecimal::add` | `.reduce()` |
| Records imutáveis — criar nova instância para "alterar" | `recalcularTotais()` |
| Accessors de Record — `entries.classNote()` (sem "get") | `recalcularTotais()` |
| `NumberFormat` + `Locale` — formatação regional | `formatMoeda()` |
| `Locale.of()` — API moderna Java 19+ | `formatMoeda()` |
| `replaceAll("\\s", "")` — regex em Java | `parseMoeda()` |
| Ordem das operações em String manipulation | `parseMoeda()` |
| Exceções de domínio vs exceções de biblioteca | `GroqException` |
| Text Block `"""` — String multilinha Java 15+ | `buildPrompt()` |
| Prompt Engineering — instruções para LLM | `buildPrompt()` |
| Divisão de responsabilidades IA vs Java | arquitetura geral |