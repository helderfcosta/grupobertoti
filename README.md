# Sobre o Projeto — Sistema de AAC

## O que é

Sistema em **Java** que gera **pranchas de Comunicação Aumentativa e Alternativa (CAA)** — conjuntos de símbolos/pictogramas usados por pessoas com dificuldade de fala para se comunicar. O usuário informa um tema (ex: "brincar no parquinho") e o sistema sugere os símbolos relevantes para montar a prancha.

## Como funciona

1. O usuário digita um tema
2. O sistema envia esse tema para um **modelo de IA rodando localmente** (via [Ollama](https://ollama.com))
3. O modelo responde com uma lista de itens/símbolos relacionados ao tema
4. O sistema exibe esses itens ao usuário

## Modelo utilizado

[`aac-board-generator-770m-ptbr-GGUF`](https://huggingface.co/tardellirs/aac-board-generator-770m-ptbr-GGUF) — modelo de 770M de parâmetros (baseado no Gemma 3, do Google), treinado especificamente para gerar pranchas de CAA em português do Brasil. Roda 100% localmente, sem depender de internet ou pagar por API externa.

## Stack técnica

- **Java** + **Maven** (gerenciamento de dependências)
- **Ollama** — roda o modelo de IA localmente
- **Ollama4j** — biblioteca Java que conecta o código ao Ollama

## Status atual

- ✅ Modelo instalado e testado (respostas reais coletadas)
- ✅ Requisitos funcionais e não funcionais definidos
- ✅ Repositório Git configurado
- 🔧 Estrutura Maven/IntelliJ em configuração
- ⏳ Implementação do código Java (`AacBoardService`) — próxima etapa

## Entrega

Enntrega da equipe: **02/10**
