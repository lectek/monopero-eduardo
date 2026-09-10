package br.com.lojagenerica.pdvclient.sync;

import br.com.lojagenerica.pdvclient.local.EventoOutbox;
import br.com.lojagenerica.pdvclient.local.FormaPagamentoCache;
import br.com.lojagenerica.pdvclient.local.FormaPagamentoCacheDao;
import br.com.lojagenerica.pdvclient.local.LocalEstoqueCache;
import br.com.lojagenerica.pdvclient.local.LocalEstoqueCacheDao;
import br.com.lojagenerica.pdvclient.local.OutboxDao;
import br.com.lojagenerica.pdvclient.local.ProdutoCache;
import br.com.lojagenerica.pdvclient.local.ProdutoCacheDao;
import br.com.lojagenerica.pdvclient.local.SyncCursorDao;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Thread única em background: drena o outbox (venda/cancelamento) a cada
 * 10s e puxa o catálogo a cada 60s. Nunca bloqueia a EDT do Swing — toda
 * tela só enfileira no {@code LocalDb} (rápido, local) e este scheduler
 * cuida da rede de forma assíncrona, com retry por evento via
 * {@code proxima_tentativa_em} (backoff exponencial, ver {@link OutboxDao}).
 */
public final class SyncScheduler {

    private static final int LOTE_PUSH = 20;
    private static final int PAGINA_PULL = 500;

    private final ApiClient apiClient;
    private final OutboxDao outboxDao;
    private final ProdutoCacheDao produtoCacheDao;
    private final FormaPagamentoCacheDao formaPagamentoCacheDao;
    private final LocalEstoqueCacheDao localEstoqueCacheDao;
    private final SyncCursorDao syncCursorDao;
    private final ScheduledExecutorService executor;
    private volatile Runnable aposCadaTick = () -> { };

    public SyncScheduler(ApiClient apiClient, OutboxDao outboxDao, ProdutoCacheDao produtoCacheDao,
                          FormaPagamentoCacheDao formaPagamentoCacheDao, LocalEstoqueCacheDao localEstoqueCacheDao,
                          SyncCursorDao syncCursorDao) {
        this.apiClient = apiClient;
        this.outboxDao = outboxDao;
        this.produtoCacheDao = produtoCacheDao;
        this.formaPagamentoCacheDao = formaPagamentoCacheDao;
        this.localEstoqueCacheDao = localEstoqueCacheDao;
        this.syncCursorDao = syncCursorDao;
        this.executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "pdv-sync");
            t.setDaemon(true);
            return t;
        });
    }

    /** Chamado (na EDT, via SwingUtilities.invokeLater dentro do listener) depois de cada tentativa de drenar o outbox — pra atualizar um indicador de "N pendentes" na tela. */
    public void aoFinalizarCadaTick(Runnable callback) {
        this.aposCadaTick = callback;
    }

    public void iniciar() {
        executor.scheduleWithFixedDelay(this::tickOutbox, 0, 10, TimeUnit.SECONDS);
        executor.scheduleWithFixedDelay(this::tickCatalogo, 5, 60, TimeUnit.SECONDS);
    }

    public void parar() {
        executor.shutdownNow();
    }

    private void tickOutbox() {
        try {
            drenarOutbox();
        } finally {
            aposCadaTick.run();
        }
    }

    private void drenarOutbox() {
        List<EventoOutbox> pendentes;
        try {
            pendentes = outboxDao.proximosPendentes(LOTE_PUSH);
        } catch (SQLException e) {
            return;
        }
        if (pendentes.isEmpty()) {
            return;
        }
        try {
            List<ResultadoEvento> resultados = apiClient.push(pendentes);
            for (int i = 0; i < pendentes.size() && i < resultados.size(); i++) {
                aplicarResultado(pendentes.get(i), resultados.get(i));
            }
        } catch (Exception e) {
            for (EventoOutbox evento : pendentes) {
                marcarErroSilencioso(evento, mensagem(e));
            }
        }
    }

    private void aplicarResultado(EventoOutbox evento, ResultadoEvento resultado) {
        try {
            if (resultado.aceitoOuDuplicado()) {
                outboxDao.marcarEnviado(evento.id(), resultado.servidorId());
            } else {
                outboxDao.marcarRejeitado(evento.id(), resultado.erro());
            }
        } catch (SQLException e) {
            // banco local falhou ao gravar o resultado — o evento continua PENDENTE, próximo tick repete o push
            // (o servidor já deduplica por evento_uuid, então repetir aqui é seguro).
        }
    }

    private void marcarErroSilencioso(EventoOutbox evento, String mensagem) {
        try {
            outboxDao.marcarErroTemporario(evento.id(), mensagem, evento.tentativas());
        } catch (SQLException ignored) {
        }
    }

    private void tickCatalogo() {
        try {
            String cursor = syncCursorDao.obter("produto");
            boolean temMais = true;
            while (temMais) {
                PullResultado resultado = apiClient.pull("produto", cursor, PAGINA_PULL);
                for (ProdutoRemoto p : resultado.itens()) {
                    produtoCacheDao.upsert(new ProdutoCache(p.id(), p.nome(), p.codigoInterno(), p.precoVenda(),
                            p.unidadeEstoqueId(), p.controlaEstoque(), p.status(), p.atualizadoEm()));
                }
                if (resultado.proximoCursor() != null) {
                    syncCursorDao.salvar("produto", resultado.proximoCursor());
                    cursor = resultado.proximoCursor();
                }
                temMais = resultado.temMais();
            }
        } catch (Exception e) {
            // rede indisponível — próximo tick tenta de novo; pull é idempotente por natureza (upsert por id), sem estado de erro a guardar.
        }

        try {
            for (FormaPagamentoRemoto f : apiClient.pullFormasPagamento()) {
                formaPagamentoCacheDao.upsert(new FormaPagamentoCache(f.id(), f.nome(), f.natureza(), f.afetaCaixa(), f.ativo()));
            }
        } catch (Exception e) {
            // idem — cadastro pequeno, próximo tick tenta de novo.
        }

        try {
            for (LocalEstoqueRemoto l : apiClient.pullLocaisEstoque()) {
                localEstoqueCacheDao.upsert(new LocalEstoqueCache(l.id(), l.nome(), l.tipo(), l.principal(), l.ativo()));
            }
        } catch (Exception e) {
            // idem.
        }
    }

    private String mensagem(Exception e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }
}
