# AAC System — Gerador de Pranchas de CAA

Sistema web em **Java (Spring Boot)** que gera **pranchas de Comunicação Aumentativa e Alternativa (CAA)**: conjuntos de palavras/símbolos usados por pessoas com dificuldade de fala para se comunicar.

O usuário digita um tema (ex.: "brincar no parquinho") e o sistema devolve uma lista de itens relacionados, com a classe gramatical e um sinônimo de cada um.

> Versão anterior do projeto (aplicação Java de console): [branch `projeto-v1-aac`](https://github.com/helderfcosta/grupobertoti/tree/projeto-v1-aac)

## Como funciona

1. O usuário digita um tema na página web.
2. O front-end envia o tema para o back-end (`POST /aac/gerar`).
3. O `OllamaService` monta um prompt e o envia a um **modelo de IA rodando localmente** via [Ollama](https://ollama.com).
4. O modelo responde com 12 linhas no formato `palavra|tipo|sinonimos`.
5. O back-end corrige sinônimos repetidos (quando o modelo devolve o sinônimo igual à palavra) e retorna o resultado.
6. A página exibe os itens em uma tabela.

### Formato da resposta do modelo

Cada linha segue o padrão `palavra|tipo|sinonimos`, onde `tipo` é uma destas letras:

| Letra | Tipo        |
|-------|-------------|
| `v`   | verbo       |
| `s`   | substantivo |
| `a`   | adjetivo    |
| `e`   | expressão   |
| `l`   | local       |
| `p`   | pronome     |

Exemplo:

```
parquinho|l|playground
escorregador|s|escorrega
balançar|v|balanço
amigo|s|colega
```

## Modelo de IA utilizado

[`aac-board-generator-770m-ptbr-GGUF`](https://huggingface.co/tardellirs/aac-board-generator-770m-ptbr-GGUF) (quantização `Q8_0`): modelo de 770M de parâmetros, baseado no Gemma 3 (Google), treinado especificamente para gerar pranchas de CAA em português do Brasil. Roda 100% localmente, sem depender de internet durante o uso e sem pagar por API externa.

## Stack técnica

- **Java 21**
- **Spring Boot 4.1.1** (Spring Web MVC + Thymeleaf)
- **Maven** (com Maven Wrapper incluído)
- **Lombok**
- **RestTemplate** para comunicação com a API do Ollama
- **Ollama** para executar o modelo localmente
- Front-end em HTML, CSS e JavaScript puro (`fetch`)

## Pré-requisitos

- [JDK 21](https://adoptium.net/) ou superior
- [Ollama](https://ollama.com/download) instalado e em execução
- O modelo baixado no Ollama (veja abaixo)

## Como executar

### 1. Baixe o modelo no Ollama

```bash
ollama pull hf.co/tardellirs/aac-board-generator-770m-ptbr-GGUF:Q8_0
```

Confirme que o Ollama está rodando em `http://localhost:11434` (é a porta padrão).

### 2. Clone o repositório

```bash
git clone https://github.com/helderfcosta/grupobertoti.git
cd grupobertoti/aac-system
```

### 3. Inicie a aplicação

Linux / macOS / Git Bash:

```bash
./mvnw spring-boot:run
```

Windows (Prompt de Comando ou PowerShell):

```bash
mvnw.cmd spring-boot:run
```

### 4. Acesse no navegador

```
http://localhost:8080
```

Digite um tema, clique em **Gerar Prancha** e aguarde o resultado.

## Configuração

O endereço do Ollama fica em `src/main/resources/application.properties`:

```properties
ollama.url=http://localhost:11434/api/generate
```

Altere esse valor se o Ollama estiver rodando em outra máquina ou porta.

## API

### `POST /aac/gerar`

Gera uma prancha a partir de um tema.

**Requisição**

```json
{
  "tema": "brincar no parquinho"
}
```

**Resposta** (texto puro, uma linha por item)

```
parquinho|l|playground
escorregador|s|escorrega
...
```

Se não for possível se comunicar com o Ollama, a resposta é o texto `ERRO: Não foi possível comunicar com o Ollama.`

## Estrutura do projeto

```
aac-system/
├── pom.xml
└── src/
    ├── main/
    │   ├── java/br/com/fatec/aac/
    │   │   ├── AacSystemApplication.java     # classe principal
    │   │   ├── controller/
    │   │   │   ├── HomeController.java       # serve a página inicial (GET /)
    │   │   │   └── AacController.java        # endpoint POST /aac/gerar
    │   │   ├── dto/
    │   │   │   └── BoardRequest.java         # corpo da requisição (tema)
    │   │   └── service/
    │   │       └── OllamaService.java        # prompt, chamada ao Ollama e pós-processamento
    │   └── resources/
    │       ├── application.properties
    │       └── templates/index.html          # interface web
    └── test/
        └── java/br/com/fatec/aac/
            └── AacSystemApplicationTests.java
```

## Status do projeto

- ✅ Modelo instalado e testado
- ✅ Requisitos funcionais e não funcionais definidos
- ✅ Aplicação Spring Boot com interface web
- ✅ Integração com o Ollama
- ✅ Geração de pranchas por tema, com tipo e sinônimo de cada item

## Entrega

Entrega da equipe: **02/10**

## Integrantes

-Daniel da Silva Carvalho Franco
- Hélder Costa
- Yan Vitor Siqueira Bergantin
