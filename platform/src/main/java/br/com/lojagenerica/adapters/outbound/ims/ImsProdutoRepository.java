package br.com.lojagenerica.adapters.outbound.ims;

import br.com.lojagenerica.application.core.settings.AppSettingService;
import br.com.lojagenerica.domain.catalogo.Produto;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * Leitura e escrita direta na tabela {@code products} do {@code rbp.db} —
 * o MESMO arquivo de banco que o IMS usa em produção agora, não uma cópia
 * nem um sistema separado. Este repositório NUNCA cria/altera o schema
 * dessa tabela e espelha exatamente as colunas que o IMS já usa em
 * {@code Db.java}, pra ler e gravar estoque em total consistência com o
 * caixa.
 *
 * TODO(Fase A do plano "Loja Genérica"): esta classe inteira é deletada —
 * chave natural nome+cor+peso não é genérica (ver docs/ARQUITETURA — modelo
 * de domínio genérico, módulo Produtos).
 *
 * pqt é armazenado como TEXT na tabela original (não INTEGER) — o próprio
 * IMS faz {@code Integer.parseInt(rs.getString("pqt"))}; aqui usamos
 * CAST(pqt AS INTEGER) para poder comparar/ordenar no SQL.
 */
@Repository
public class ImsProdutoRepository {

    /**
     * Markup do preço online sobre o preço presencial (o mesmo pprice que o IMS
     * usa no caixa) — cobre a taxa da plataforma (venda + fatia da entrega) sem
     * o cliente ver nenhuma taxa separada no checkout. Configurável via
     * app_settings; "cerca de 7%" foi o valor pedido, 7.0 é o default.
     */
    private static final String SETTING_MARKUP_PERCENTUAL = "catalogo.markup_percentual";
    private static final BigDecimal DEFAULT_MARKUP_PERCENTUAL = BigDecimal.valueOf(7.0);

    private static final RowMapper<Produto> ROW_MAPPER = (rs, rowNum) -> new Produto(
            rs.getString("pname"),
            rs.getString("pclr"),
            rs.getString("pwt"),
            rs.getInt("pqt"),
            rs.getString("pcode"),
            rs.getString("pdesc"),
            rs.getDouble("pprice")
    );

    private final JdbcTemplate jdbcTemplate;
    private final AppSettingService appSettingService;

    public ImsProdutoRepository(JdbcTemplate jdbcTemplate, AppSettingService appSettingService) {
        this.jdbcTemplate = jdbcTemplate;
        this.appSettingService = appSettingService;
    }

    /**
     * Todos os produtos com o preço EXATO que o IMS usa no caixa presencial
     * (sem markup) — uso administrativo, pra bater com o que o lojista vê lá.
     */
    public List<Produto> fetchTodos() {
        return jdbcTemplate.query(
                "SELECT pname, pclr, pwt, CAST(pqt AS INTEGER) AS pqt, pcode, pdesc, COALESCE(pprice, 0) AS pprice " +
                "FROM products ORDER BY pname;",
                ROW_MAPPER);
    }

    /**
     * Só o que pode aparecer na vitrine pública: preço > 0 e estoque > 0 (mesma
     * regra do ParaisoPet). Preço JÁ com o markup online — é o que o cliente vê e paga.
     */
    public List<Produto> fetchVitrine() {
        return jdbcTemplate.query(
                "SELECT pname, pclr, pwt, CAST(pqt AS INTEGER) AS pqt, pcode, pdesc, pprice " +
                "FROM products " +
                "WHERE pprice IS NOT NULL AND pprice > 0 AND CAST(pqt AS INTEGER) > 0 " +
                "ORDER BY pname;",
                ROW_MAPPER
        ).stream().map(this::comMarkup).toList();
    }

    /** Usado pelo checkout — preço com markup, é o valor cobrado do cliente de verdade. */
    public Optional<Produto> fetchPorChave(String nome, String cor, String peso) {
        return jdbcTemplate.query(
                "SELECT pname, pclr, pwt, CAST(pqt AS INTEGER) AS pqt, pcode, pdesc, COALESCE(pprice, 0) AS pprice " +
                "FROM products WHERE pname = ? AND pclr = ? AND pwt = ?;",
                ROW_MAPPER, nome, cor, peso
        ).stream().findFirst().map(this::comMarkup);
    }

    public Optional<Produto> fetchPorCodigoBarras(String codigo) {
        return jdbcTemplate.query(
                "SELECT pname, pclr, pwt, CAST(pqt AS INTEGER) AS pqt, pcode, pdesc, COALESCE(pprice, 0) AS pprice " +
                "FROM products WHERE pcode = ?;",
                ROW_MAPPER, codigo
        ).stream().findFirst().map(this::comMarkup);
    }

    private Produto comMarkup(Produto produto) {
        BigDecimal markup = appSettingService.getDecimal(SETTING_MARKUP_PERCENTUAL, DEFAULT_MARKUP_PERCENTUAL);
        BigDecimal fator = BigDecimal.ONE.add(markup.divide(BigDecimal.valueOf(100)));
        double precoComMarkup = BigDecimal.valueOf(produto.preco())
                .multiply(fator)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
        return new Produto(
                produto.nome(), produto.cor(), produto.peso(), produto.quantidade(),
                produto.codigoBarras(), produto.descricao(), precoComMarkup
        );
    }

    /**
     * Dá baixa no estoque e grava em {@code sold_records} — a MESMA tabela que o
     * IMS usa nas telas de Venda/Relatórios (mesmo formato de timestamp
     * "dd/MM/yyyy HH:mm:ss" que {@code Db.sellProduct} já usa), pra uma venda
     * feita pelo site aparecer no faturamento do caixa sem precisar mexer no IMS.
     * Lança {@link IllegalStateException} se o produto não existe ou o estoque é insuficiente.
     */
    public void venderEstoque(String nome, String cor, String peso, int quantidade, double precoUnitario) {
        Integer estoqueAtual = jdbcTemplate.queryForObject(
                "SELECT CAST(pqt AS INTEGER) FROM products WHERE pname = ? AND pclr = ? AND pwt = ?;",
                Integer.class, nome, cor, peso
        );
        if (estoqueAtual == null) {
            throw new IllegalStateException("Produto não encontrado: " + nome);
        }
        if (estoqueAtual < quantidade) {
            throw new IllegalStateException("Estoque insuficiente para " + nome + " (disponível: " + estoqueAtual + ")");
        }
        jdbcTemplate.update(
                "UPDATE products SET pqt = ? WHERE pname = ? AND pclr = ? AND pwt = ?;",
                estoqueAtual - quantidade, nome, cor, peso
        );
        jdbcTemplate.update(
                "INSERT INTO sold_records (timestamp, product, colour, weight, quantity, pprice) " +
                "VALUES (strftime('%d/%m/%Y %H:%M:%S','now','localtime'), ?, ?, ?, ?, ?);",
                nome, cor, peso, quantidade, precoUnitario
        );
    }
}
