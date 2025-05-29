package model;

import java.io.Serializable;

public class RetornoDrone implements Serializable {
    private static final long serialVersionUID = 1L;

    String mensagem;
    Posicao posicao;

    public RetornoDrone(String mensagem, Posicao posicao) {
        this.mensagem = mensagem;
        this.posicao = posicao;
    }

    public String getMensagem() {
        return mensagem;
    }

    public void setMensagem(String mensagem) {
        this.mensagem = mensagem;
    }

    public Posicao getPosicao() {
        return posicao;
    }

    public void setPosicao(Posicao posicao) {
        this.posicao = posicao;
    }
}
