package br.com.lojagenerica.tools.importador;

import java.util.List;

/**
 * Resultado de {@link RbpImportService#analisar} (dry-run, nada escrito) ou
 * {@link RbpImportService#importar} (real — os mesmos números, mas depois
 * de aplicados). Comparar as duas chamadas é a reconciliação exigida antes
 * de qualquer corte real (ver docs/ROADMAP.md, Risco #3).
 */
public record RelatorioImportacao(
        int totalProdutosLegado,
        long totalUnidadesEmEstoque,
        int produtosSemPreco,
        int produtosComCorOuPeso,
        int totalContasAdmin,
        List<String> avisos) {
}
