# AAC Board System – Pranchas de comunicação com IA local

Aplicação web que gera **pranchas de Comunicação Aumentativa e Alternativa (CAA)**, conjuntos de palavras e símbolos usados por pessoas com dificuldade de fala. O usuário digita um tema, por exemplo "brincar no parquinho", e recebe 12 palavras relacionadas, com classe gramatical e sinônimo.

O modelo de IA roda **100% localmente**, sem internet e sem custo de API externa.

## Como funciona

1. O usuário digita um tema na página web.
2. O front-end envia o tema para a API (`POST /aac/gerar`).
3. O `OllamaService` monta o prompt e consulta o modelo no Ollama.
4. A resposta passa por um pós-processamento: linhas fora do padrão são descartadas e sinônimos repetidos são corrigidos.
5. A página exibe a prancha em uma tabela (palavra, tipo e sinônimo).

## Stack

- **Java 21** e **Spring Boot**: servidor, rotas e injeção de dependências
- **Ollama**: executa o modelo de linguagem localmente
- **RestTemplate**: comunicação HTTP entre a API e o Ollama
- **HTML, CSS e JavaScript puro**: interface web
- **Maven**: gerenciamento de dependências

## Modelo utilizado

[`aac-board-generator-770m-ptbr`](https://huggingface.co/tardellirs/aac-board-generator-770m-ptbr-GGUF), de **tardellirs**: modelo de 770M de parâmetros baseado no Gemma 3 e treinado para gerar pranchas de CAA em português do Brasil.

## Estrutura

| Componente | Função |
| --- | --- |
| `AacController` | Recebe o tema em `POST /aac/gerar` e devolve a prancha |
| `BoardRequest` | Representa o corpo da requisição (`{"tema": "..."}`) |
| `OllamaService` | Monta o prompt, chama o Ollama e trata a resposta |
| `index.html` | Interface onde o usuário digita o tema e vê a prancha |

## Como rodar

1. Instale o [Ollama](https://ollama.com) e baixe o modelo acima.
2. Com o Ollama ligado, rode o projeto: `mvn spring-boot:run`
3. Abra `http://localhost:8080` no navegador.

## Status

- ✅ API e integração com o modelo local
- ✅ Pós-processamento e tratamento de erro quando o Ollama não responde
- ✅ Interface web com a prancha em tabela
- 🔜 Pictogramas para cada palavra, usando a API pública do [ARASAAC](https://arasaac.org)
- 🔜 Exibição em cartões coloridos por classe gramatical
- 🔜 Cache das buscas e testes automatizados

## Contexto

Projeto da disciplina do Prof. Bertoti, Banco de Dados, FATEC São José dos Campos, 2026.
