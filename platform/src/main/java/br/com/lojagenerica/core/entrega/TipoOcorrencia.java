package br.com.lojagenerica.core.entrega;

/**
 * Imprevisto que o motoboy relata durante a rota — não é a mesma coisa
 * que {@link StatusEntregaParada#TENTATIVA_SEM_SUCESSO}/{@code REAGENDAR}
 * (aquilo é "não consegui concluir ESTA entrega", muda o estado da
 * parada); isto é um relato informativo, não bloqueia nem avança a rota,
 * e alguns tipos são de segurança de verdade — {@link #isGrave()} marca
 * os que precisam aparecer como alerta pro admin, não só um texto perdido
 * numa observação.
 */
public enum TipoOcorrencia {
    CLIENTE_AUSENTE(false),
    ENDERECO_NAO_ENCONTRADO(false),
    TRANSITO_OU_CONGESTIONAMENTO(false),
    PROBLEMA_NO_VEICULO(false),
    CLIMA_RUIM(false),
    CLIENTE_RECUSOU_RECEBER(false),
    CLIENTE_AGRESSIVO_OU_AMEACA(true),
    LOCAL_INSEGURO(true),
    ROUBO_OU_ASSALTO(true),
    ACIDENTE_DE_TRANSITO(true),
    EMERGENCIA_MEDICA(true),
    OUTRO(false);

    private final boolean grave;

    TipoOcorrencia(boolean grave) {
        this.grave = grave;
    }

    /** Tipos graves viram alerta ativo pro admin (ver EntregaRotaService#listarOcorrenciasGravesAbertas). */
    public boolean isGrave() {
        return grave;
    }
}
