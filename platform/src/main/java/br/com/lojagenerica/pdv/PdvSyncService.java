package br.com.lojagenerica.pdv;

import br.com.lojagenerica.core.acesso.Usuario;
import br.com.lojagenerica.core.acesso.UsuarioRepository;
import br.com.lojagenerica.core.cadastro.FormaPagamento;
import br.com.lojagenerica.core.cadastro.FormaPagamentoRepository;
import br.com.lojagenerica.core.cadastro.LocalEstoque;
import br.com.lojagenerica.core.cadastro.LocalEstoqueRepository;
import br.com.lojagenerica.core.produto.Produto;
import br.com.lojagenerica.core.produto.ProdutoRepository;
import br.com.lojagenerica.core.venda.CanalVenda;
import br.com.lojagenerica.core.venda.RegistrarVendaCommand;
import br.com.lojagenerica.core.venda.Venda;
import br.com.lojagenerica.core.venda.VendaRepository;
import br.com.lojagenerica.core.venda.VendaService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/**
 * Processa o lote de eventos que um terminal envia. Cada evento é
 * idempotente por {@code uuid} — reenviar o mesmo evento (reconexão no
 * meio do envio) nunca duplica nem perde a venda: primeiro checa
 * {@code pdv_evento_recebido}; mesmo que essa checagem falhe por uma
 * corrida rara, {@link VendaService#registrar} tem sua PRÓPRIA
 * idempotência por {@code venda.uuid} (usamos o mesmo uuid do evento pra
 * isso — ver {@link EventoPushRequest}), então nunca dá pra vender duas
 * vezes de fato.
 *
 * <p>Deliberadamente não é {@code @Transactional} no nível do lote: cada
 * evento processado (via {@link VendaService}, outro bean) já é atômico
 * por si; se o registro de idempotência falhar depois de a venda já ter
 * sido criada, o pior caso é reprocessar o mesmo evento depois — que o
 * idempotência de `venda.uuid` absorve sem duplicar.
 */
@Service
public class PdvSyncService {

    private static final Logger log = LoggerFactory.getLogger(PdvSyncService.class);

    private final PdvEventoRecebidoRepository eventoRecebidoRepository;
    private final VendaRepository vendaRepository;
    private final VendaService vendaService;
    private final ProdutoRepository produtoRepository;
    private final FormaPagamentoRepository formaPagamentoRepository;
    private final LocalEstoqueRepository localEstoqueRepository;
    private final UsuarioRepository usuarioRepository;
    private final ObjectMapper objectMapper;

    public PdvSyncService(PdvEventoRecebidoRepository eventoRecebidoRepository, VendaRepository vendaRepository,
                           VendaService vendaService, ProdutoRepository produtoRepository,
                           FormaPagamentoRepository formaPagamentoRepository, LocalEstoqueRepository localEstoqueRepository,
                           UsuarioRepository usuarioRepository, ObjectMapper objectMapper) {
        this.eventoRecebidoRepository = eventoRecebidoRepository;
        this.vendaRepository = vendaRepository;
        this.vendaService = vendaService;
        this.produtoRepository = produtoRepository;
        this.formaPagamentoRepository = formaPagamentoRepository;
        this.localEstoqueRepository = localEstoqueRepository;
        this.usuarioRepository = usuarioRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * "produto" é incremental por cursor (catálogo pode ser grande); "forma_pagamento" e
     * "local_estoque" são cadastros pequenos — o terminal puxa a lista inteira a cada tick,
     * sem cursor. Categoria/unidade/cliente entram quando o cliente Swing precisar de fato.
     */
    public PullResponse<?> pull(String recurso, String desde, int limite) {
        return switch (recurso) {
            case "produto" -> pullProduto(desde, limite);
            case "forma_pagamento" -> pullFormaPagamento();
            case "local_estoque" -> pullLocalEstoque();
            default -> throw new IllegalArgumentException("Recurso de sincronização não suportado: " + recurso);
        };
    }

    private PullResponse<ProdutoSyncDTO> pullProduto(String desde, int limite) {
        Instant cursor = desde != null && !desde.isBlank() ? Instant.parse(desde) : Instant.EPOCH;
        List<Produto> produtos = produtoRepository.findByAtualizadoEmAfterOrderByAtualizadoEmAsc(
                cursor, PageRequest.of(0, limite));

        List<ProdutoSyncDTO> itens = produtos.stream().map(p -> new ProdutoSyncDTO(
                p.getId(), p.getNome(), p.getCodigoInterno(), p.getPrecoVenda(), p.getUnidadeEstoque().getId(),
                p.isControlaEstoque(), p.getStatus().name(), p.getAtualizadoEm())).toList();

        String proximoCursor = itens.isEmpty() ? desde : itens.get(itens.size() - 1).atualizadoEm().toString();
        boolean temMais = itens.size() == limite;
        return new PullResponse<>(itens, proximoCursor, temMais);
    }

    private PullResponse<FormaPagamentoSyncDTO> pullFormaPagamento() {
        List<FormaPagamentoSyncDTO> itens = formaPagamentoRepository.findAll().stream()
                .sorted(java.util.Comparator.comparing(FormaPagamento::getId))
                .map(f -> new FormaPagamentoSyncDTO(f.getId(), f.getNome(), f.getNatureza().name(), f.isAfetaCaixa(), f.isAtivo()))
                .toList();
        return new PullResponse<>(itens, null, false);
    }

    private PullResponse<LocalEstoqueSyncDTO> pullLocalEstoque() {
        List<LocalEstoqueSyncDTO> itens = localEstoqueRepository.findAll().stream()
                .sorted(java.util.Comparator.comparing(LocalEstoque::getId))
                .map(l -> new LocalEstoqueSyncDTO(l.getId(), l.getNome(), l.getTipo(), l.isPrincipal(), l.isAtivo()))
                .toList();
        return new PullResponse<>(itens, null, false);
    }

    public ResultadoEventoResponse processar(Long terminalId, EventoPushRequest evento) {
        var existente = eventoRecebidoRepository.findByEventoUuid(evento.uuid());
        if (existente.isPresent()) {
            return ResultadoEventoResponse.duplicado(evento.uuid(), existente.get().getVendaId());
        }

        try {
            Long vendaId = switch (evento.tipo()) {
                case VENDA_REGISTRADA -> registrarVenda(terminalId, evento).getId();
                case VENDA_CANCELADA -> cancelarVenda(evento).getId();
            };
            eventoRecebidoRepository.save(new PdvEventoRecebido(evento.uuid(), terminalId, evento.tipo(), vendaId));
            return ResultadoEventoResponse.aceito(evento.uuid(), vendaId);
        } catch (RuntimeException e) {
            log.warn("Evento {} ({}) do terminal {} rejeitado: {}", evento.uuid(), evento.tipo(), terminalId, e.getMessage());
            return ResultadoEventoResponse.rejeitado(evento.uuid(), e.getMessage());
        }
    }

    private Venda registrarVenda(Long terminalId, EventoPushRequest evento) {
        VendaRegistradaPayload payload = objectMapper.convertValue(evento.payload(), VendaRegistradaPayload.class);
        List<RegistrarVendaCommand.ItemVendaCommand> itens = payload.itens().stream()
                .map(i -> new RegistrarVendaCommand.ItemVendaCommand(
                        i.produtoId(), i.quantidade(), i.unidadeId(), i.precoUnitario(), i.descontoValor()))
                .toList();
        List<RegistrarVendaCommand.PagamentoVendaCommand> pagamentos = payload.pagamentos() == null ? List.of()
                : payload.pagamentos().stream()
                        .map(p -> new RegistrarVendaCommand.PagamentoVendaCommand(
                                p.formaPagamentoId(), p.valor(), p.valorRecebido(), p.troco()))
                        .toList();

        // validarDesconto (em VendaService) checa permissão de desconto pelo e-mail, não pelo id —
        // o payload do PDV só carrega usuarioId, então resolve aqui. Sem usuarioId (caixa sem login
        // ainda), email fica null e qualquer desconto>0 falha fechado (comportamento correto: sem
        // usuário identificado, nenhum desconto é permitido).
        String usuarioEmail = payload.usuarioId() == null ? null
                : usuarioRepository.findById(payload.usuarioId()).map(Usuario::getEmail).orElse(null);

        return vendaService.registrar(new RegistrarVendaCommand(
                evento.uuid(), CanalVenda.PDV, payload.localEstoqueId(), payload.clienteId(), terminalId,
                payload.usuarioId(), usuarioEmail, payload.descontoValor(), null, itens, pagamentos));
    }

    private Venda cancelarVenda(EventoPushRequest evento) {
        VendaCanceladaPayload payload = objectMapper.convertValue(evento.payload(), VendaCanceladaPayload.class);
        Venda venda = vendaRepository.findByUuid(payload.vendaUuid())
                .orElseThrow(() -> new NoSuchElementException("Venda " + payload.vendaUuid() + " não encontrada pra cancelar"));
        return vendaService.cancelar(venda.getId(), payload.motivo(), payload.usuarioId());
    }
}
