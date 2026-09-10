package br.com.lojagenerica.pdv;

/**
 * Só os tipos que o servidor já sabe processar (Caixa/Financeiro é Fase E —
 * CAIXA_ABERTO/CAIXA_FECHADO/MOVIMENTO_CAIXA entram junto, ver docs/ROADMAP.md).
 */
public enum TipoEventoPdv {
    VENDA_REGISTRADA,
    VENDA_CANCELADA
}
