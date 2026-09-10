package br.com.lojagenerica.core.compra;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Única implementação até o módulo Financeiro existir (Fase E). */
@Component
public class NoopGeradorContaPagar implements GeradorContaPagar {

    private static final Logger log = LoggerFactory.getLogger(NoopGeradorContaPagar.class);

    @Override
    public void gerar(Compra compraConfirmada) {
        log.info("Compra {} confirmada, total {} — conta a pagar ainda não gerada (Financeiro é Fase E).",
                compraConfirmada.getId(), compraConfirmada.getTotal());
    }
}
