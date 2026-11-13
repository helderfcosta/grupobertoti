package br.com.fatec.aac.dto;

public class BoardRequest {

    private String tema;

    /** "online" (padrão) ou "offline". Opcional. */
    private String modo;

    public String getTema() {
        return tema;
    }

    public void setTema(String tema) {
        this.tema = tema;
    }

    public String getModo() {
        return modo;
    }

    public void setModo(String modo) {
        this.modo = modo;
    }
}
