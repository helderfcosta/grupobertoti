package br.com.fatec.aac.service;

public class OllamaIndisponivelException extends RuntimeException {

    public OllamaIndisponivelException(String mensagem) {
        super(mensagem);
    }
}
