# Documentação e Formalização da Gramática LL(1) - CachaçaScript

Este documento apresenta a análise formal da gramática da linguagem **CachaçaScript**, detalhando as transformações realizadas para torná-la estritamente **LL(1)**. Essa conformidade garante que o analisador sintático (parser) seja preditivo descendente e funcione de forma eficiente com apenas 1 token de visualização prévia (*lookahead* de 1), sem necessidade de *backtracking*.

---

## 1. O que é uma Gramática LL(1)?

Uma gramática livre de contexto é classificada como **LL(1)** se for possível construir um analisador sintático preditivo descendente que tome todas as decisões de desvio baseando-se apenas no token atual da entrada. A sigla significa:
* **L** (Left-to-right scan): Varredura da entrada da esquerda para a direita.
* **L** (Leftmost derivation): Derivação mais à esquerda.
* **1**: Lookahead de 1 token.

### Condições Formais para LL(1)
Para qualquer não-terminal $A$ que possua regras de produção alternativas $A \to \alpha_1 \mid \alpha_2 \mid \dots \mid \alpha_n$:
1. Os conjuntos de símbolos iniciais de cada alternativa devem ser disjuntos:
   $$First(\alpha_i) \cap First(\alpha_j) = \emptyset \quad \forall i \neq j$$
2. Se uma das produções puder derivar a cadeia vazia ($\alpha_i \Rightarrow^* \epsilon$):
   * O conjunto $First$ das outras alternativas não pode colidir com o conjunto $Follow(A)$:
     $$First(\alpha_j) \cap Follow(A) = \emptyset \quad \forall j \neq i$$
   * A cadeia vazia não pode ser derivada por mais de uma alternativa.

---

## 2. Conflitos Identificados e Resoluções

Na definição inicial e intuitiva da linguagem, surgiram dois tipos clássicos de problemas que violam a propriedade LL(1): **ambiguidade de prefixo comum** (conflitos no primeiro símbolo) e **recursão à esquerda**.

### Conflito A: Ambiguidade de Prefixo Comum com `<ID>`
Na linguagem CachaçaScript, um identificador (`<ID>`) pode iniciar diferentes comandos e expressões:
1. **Comando de Atribuição:** `x engarrafar 10 viraDose` (Começa com `<ID>`)
2. **Chamada de Função (Comando):** `soma abreCopo 5 maisUmaDose 3 fechaCopo viraDose` (Começa com `<ID>`)
3. **Uso de Variável (Expressão):** `x` (Começa com `<ID>`)
4. **Chamada de Função (Expressão):** `soma abreCopo 5 maisUmaDose 3 fechaCopo` (Começa com `<ID>`)

Se a gramática contivesse alternativas diretas como:
```bnf
<COMANDO>    ::= <ID> "engarrafar" <EXPR> "viraDose"
               | <ID> "abreCopo" <ARGS_OPT> "fechaCopo" "viraDose"
<PRIMARIA>   ::= <ID>
               | <ID> "abreCopo" <ARGS_OPT> "fechaCopo"
```
Ao ler um token do tipo `<ID>`, o parser LL(1) não saberia qual alternativa escolher, pois o conjunto $First$ de todas elas seria $\{ <ID> \}$, violando a regra de interseção vazia.

#### Resolução: Fatoração à Esquerda (Left-Factoring)
Para resolver a ambiguidade, fatoramos o `<ID>` para fora das alternativas, introduzindo produções de sufixo:

**1. Nos comandos:**
O comando iniciado por ID é representado pela regra `<STMT_FROM_ID>`, que extrai o ID e delega a decisão do sufixo à regra `<STMT_ID_SUFFIX>`:
```bnf
<STMT_FROM_ID>   ::= <ID> <STMT_ID_SUFFIX>
<STMT_ID_SUFFIX> ::= "engarrafar" <EXPR>
                   | "abreCopo" <ARGS_OPT> "fechaCopo"
```
* **$First(\text{"engarrafar"} \dots) = \{ \text{"engarrafar"} \}$**
* **$First(\text{"abreCopo"} \dots) = \{ \text{"abreCopo"} \}$**
Como os conjuntos iniciais de sufixo são disjuntos ($\{ \text{"engarrafar"} \} \cap \{ \text{"abreCopo"} \} = \emptyset$), o conflito é sanado.

**2. Nas expressões primárias:**
Fatoramos o uso de identificador na regra `<PRIM>` através de `<PRIM_ID_SUFFIX>`:
```bnf
<PRIM>           ::= <NUMERO_INT> | <NUMERO_FLOAT> | ...
                   | <ID> <PRIM_ID_SUFFIX>
<PRIM_ID_SUFFIX> ::= ε
                   | "abreCopo" <ARGS_OPT> "fechaCopo"
```
Como a primeira alternativa de `<PRIM_ID_SUFFIX>` é vazia ($\epsilon$), validamos a segunda condição do LL(1):
* **$Follow(\text{<PRIM\_ID\_SUFFIX>})$** não pode conter `"abreCopo"`. 
Dado que parênteses de chamadas de funções abrem imediatamente após o ID, e expressões não exigem abertura de parênteses subsequente solta em outras situações sintáticas válidas após um identificador ordinário, o $Follow$ não colide com o $First$ das alternativas, mantendo a integridade LL(1).

---

### Conflito B: Recursão à Esquerda em Expressões Aritméticas e Lógicas
Se definíssemos as expressões aritméticas de forma clássica matemática:
```bnf
<EXPR_ADD> ::= <EXPR_ADD> "+" <EXPR_MUL>
             | <EXPR_MUL>
```
Teríamos uma **recursão à esquerda** direta. O parser preditivo entraria em *loop* infinito tentando expandir `<EXPR_ADD>` indefinidamente sem consumir nenhum token da entrada.

#### Resolução: Uso de Loops de Repetição EBNF (Equivalente a remoção de recursão)
Para remover a recursão à esquerda sem alterar a associatividade esquerda dos operadores no compilador, reescrevemos as produções em notação EBNF utilizando repetições de Kleene `( ... )*`. No JavaCC, isso é codificado como laços de repetição (loops `while` em Java):

```bnf
<EXPR_ADD> ::= <EXPR_MUL> ( ( "+" | "-" ) <EXPR_MUL> )*
<EXPR_MUL> ::= <EXPR_UN>  ( ( "*" | "/" ) <EXPR_UN>  )*
```
Essa estrutura remove a recursividade à esquerda na gramática formal:
1. O parser primeiro consome a subexpressão de maior precedência (`<EXPR_MUL>`).
2. Em seguida, verifica se o próximo token é um operador de soma (`+`) ou subtração (`-`).
3. Caso positivo, consome o operador e a próxima subexpressão, iterando no laço. Caso contrário, encerra a regra.
Como a decisão de entrar ou continuar no laço depende unicamente da presença de `+` ou `-`, que não pertencem ao início de `<EXPR_MUL>`, a gramática é perfeitamente LL(1).

---

## 3. Conjuntos First e Follow de Amostra

Abaixo são exibidos os conjuntos de símbolos iniciais ($First$) e de sincronização/fim ($Follow$) calculados para alguns dos não-terminais chave da gramática CachaçaScript, demonstrando a disjunção necessária.

| Não-Terminal | Conjunto $First$ | Conjunto $Follow$ |
| :--- | :--- | :--- |
| **`<PROGRAMA>`** | $\{ \text{"abreAButelada"} \}$ | $\{ \text{EOF} \}$ |
| **`<DECL_FUN>`** | $\{ \text{"alambique"} \}$ | $\{ \text{"alambique"}, \text{"abreBarril"}, \text{EOF} \}$ |
| **`<BLOCO>`** | $\{ \text{"abreBarril"} \}$ | $\{ \text{"fechaAButelada"}, \text{"seDerRuim"}, \text{"viraDose"}, \text{"fechaBarril"}, \text{EOF} \}$ |
| **`<TIPO>`** | $\{ \text{"doseInteira"}, \text{"doseQuebrada"}, \text{"taNoGrau"}, \text{"rotulo"} \}$ | $\{ <ID> \}$ |
| **`<COMANDO>`** | $\{ \text{"doseInteira"}, \text{"doseQuebrada"}, \text{"taNoGrau"}, \text{"rotulo"}, \text{"garrafa"}, <ID>, \text{"serveNoCopo"}, \text{"pedeNoBalcao"}, \text{"seDerBoa"}, \text{"mestreAlambique"}, \text{"enquantoTemCana"}, \text{"tomaUma"}, \text{"rodada"}, \text{"devolveDose"}, \text{"abreBarril"}, \text{"viraDose"} \}$ | $\{ \text{"doseInteira"}, \text{"doseQuebrada"}, \text{"taNoGrau"}, \text{"rotulo"}, \text{"garrafa"}, <ID>, \text{"serveNoCopo"}, \text{"pedeNoBalcao"}, \text{"seDerBoa"}, \text{"mestreAlambique"}, \text{"enquantoTemCana"}, \text{"tomaUma"}, \text{"rodada"}, \text{"devolveDose"}, \text{"abreBarril"}, \text{"viraDose"}, \text{"fechaBarril"} \}$ |
| **`<EXPR>`** | $\{ <NUMERO_INT>, <NUMERO_FLOAT>, \text{"simPatrao"}, \text{"nemAPau"}, <STR>, <ID>, \text{"abreCopo"}, \text{"!"}, \text{"-"} \}$ | $\{ \text{"viraDose"}, \text{"fechaCopo"}, \text{"maisUmaDose"} \}$ |

### Análise de Disjunção das Alternativas
Analisando a regra principal de seleção de comandos (`<COMANDO>`):
Cada alternativa de comando possui um conjunto $First$ disjunto na gramática fatorada:
* Declaração de Variável (`<DECL>`): Começa com tipo ou `garrafa`.
* Comando por ID (`<STMT_FROM_ID>`): Começa com `<ID>`.
* E/S (`<IO>`): Começa com `serveNoCopo` ou `pedeNoBalcao`.
* Condicional (`<IF>`): Começa com `seDerBoa` ou `mestreAlambique`.
* Laço Enquanto (`<WHILE>`): Começa com `enquantoTemCana`.
* Laço Repita (`<REPEAT_UNTIL>`): Começa com `tomaUma`.
* Laço Para (`<FOR>`): Começa com `rodada`.
* Retorno (`<RET>`): Começa com `devolveDose`.
* Bloco Aninhado: Começa com `abreBarril`.
* Comando Vazio: Começa com `viraDose`.

Nenhum desses conjuntos $First$ colide com outro, o que comprova que a seleção do fluxo de comandos é estritamente **LL(1)**.
