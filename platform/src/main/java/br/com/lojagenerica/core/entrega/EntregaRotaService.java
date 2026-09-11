package br.com.lojagenerica.core.entrega;

import br.com.lojagenerica.application.core.settings.AppSettingService;
import br.com.lojagenerica.application.service.delivery.DeliveryRouteService;
import br.com.lojagenerica.core.acesso.Usuario;
import br.com.lojagenerica.core.venda.StatusVenda;
import br.com.lojagenerica.core.venda.Venda;
import br.com.lojagenerica.core.venda.VendaRepository;
import br.com.lojagenerica.domain.enums.ModoEntrega;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

/**
 * Orquestra o módulo de entrega: roteirização (via
 * {@link DeliveryRouteService}, TSP exato já existente, reaproveitado sem
 * reescrever), acompanhamento pelo motoboy e cálculo de comissão. Modelado
 * em cima do design mais maduro encontrado nas referências (multlektec/
 * AdminEntregaRouteService), adaptado a {@code core.venda.Venda}/
 * {@code core.acesso.Usuario} deste projeto.
 *
 * <p>Regra de comissão: sem {@link #SETTING_COMISSAO_PERCENTUAL} configurado,
 * a comissão é zero — nunca um percentual comercial fictício "chutado" (ver
 * docs/CONTEXTO.md, regra contra valores de exemplo).
 */
@Service
public class EntregaRotaService {

    private static final String SETTING_COMISSAO_PERCENTUAL = "entrega.motoboy.comissao_percentual";
    private static final int MIN_VENDAS_ROTA = 2;
    private static final int MAX_VENDAS_ROTA = 12;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final VendaRepository vendaRepository;
    private final EntregaRotaRepository entregaRotaRepository;
    private final EntregaParadaRepository entregaParadaRepository;
    private final DeliveryRouteService deliveryRouteService;
    private final AppSettingService appSettingService;

    public EntregaRotaService(VendaRepository vendaRepository, EntregaRotaRepository entregaRotaRepository,
                               EntregaParadaRepository entregaParadaRepository,
                               DeliveryRouteService deliveryRouteService, AppSettingService appSettingService) {
        this.vendaRepository = vendaRepository;
        this.entregaRotaRepository = entregaRotaRepository;
        this.entregaParadaRepository = entregaParadaRepository;
        this.deliveryRouteService = deliveryRouteService;
        this.appSettingService = appSettingService;
    }

    @Transactional(readOnly = true)
    public List<Venda> listarVendasElegiveis() {
        Set<Long> comParadaAtiva = Set.copyOf(entregaParadaRepository.findVendaIdsComParadaAtiva());
        return vendaRepository.findByModoEntregaAndStatusOrderByDataAsc(ModoEntrega.ENTREGA, StatusVenda.CONFIRMADA)
                .stream()
                .filter(v -> !comParadaAtiva.contains(v.getId()))
                .toList();
    }

    @Transactional(readOnly = true)
    public DeliveryRouteService.PlannedRoute previsualizar(List<Long> vendaIds, String origem) {
        List<Venda> vendas = carregarVendasParaRota(vendaIds);
        return deliveryRouteService.plan(origem, paraEntradasDaRota(vendas));
    }

    @Transactional
    public EntregaRota criarRota(List<Long> vendaIds, String origem, Long criadoPorUsuarioId) {
        List<Venda> vendas = carregarVendasParaRota(vendaIds);
        DeliveryRouteService.PlannedRoute plano = deliveryRouteService.plan(origem, paraEntradasDaRota(vendas));

        BigDecimal percentualComissao = appSettingService.getDecimal(SETTING_COMISSAO_PERCENTUAL, BigDecimal.ZERO);
        EntregaRota rota = new EntregaRota(plano.origem(), criadoPorUsuarioId, percentualComissao);
        rota.definirCalculo(plano.distanciaTotalKm(), plano.mapaUrl());

        for (DeliveryRouteService.DeliveryStopPlan stopPlan : plano.paradas()) {
            Venda venda = vendas.stream().filter(v -> v.getId().equals(stopPlan.pedidoId())).findFirst()
                    .orElseThrow();
            EntregaParada parada = new EntregaParada(venda, stopPlan.ordem(), stopPlan.clienteNome(),
                    stopPlan.enderecoEntrega(), gerarCodigoEntrega(), venda.getValorFrete());
            parada.definirDistancias(stopPlan.distanciaAnteriorKm(), stopPlan.distanciaAcumuladaKm());
            rota.adicionarParada(parada);
        }
        return entregaRotaRepository.save(rota);
    }

    @Transactional(readOnly = true)
    public List<EntregaRota> listarRotasRecentes() {
        return entregaRotaRepository.buscarRecentesComParadas();
    }

    @Transactional(readOnly = true)
    public List<EntregaRota> listarRotasDisponiveisParaMotoboy() {
        return entregaRotaRepository.buscarPorStatusComParadas(StatusEntregaRota.PLANEJADA);
    }

    @Transactional(readOnly = true)
    public List<EntregaRota> listarMinhasRotas(Long motoboyId) {
        return entregaRotaRepository.buscarPorEntregadorComParadas(motoboyId);
    }

    @Transactional(readOnly = true)
    public EntregaRota obterRota(Long rotaId) {
        return entregaRotaRepository.findByIdComParadas(rotaId)
                .orElseThrow(() -> new NoSuchElementException("Rota " + rotaId + " não encontrada"));
    }

    /**
     * Reivindicação atômica (UPDATE condicional): garante que só um motoboy
     * consegue assumir a rota, mesmo com dois clicando "iniciar" ao mesmo
     * tempo — nenhuma das referências analisadas (MiniMercadinhoSaaS,
     * SaúdeMaisFarma, multlektec) resolveu isso; primeira-vez-que-clicar
     * ganhava a rota silenciosamente em todas elas.
     */
    @Transactional
    public EntregaRota iniciarRota(Long rotaId, Usuario motoboy) {
        int atualizadas = entregaRotaRepository.reivindicarEIniciar(rotaId, motoboy);
        if (atualizadas == 0) {
            EntregaRota existente = entregaRotaRepository.findById(rotaId)
                    .orElseThrow(() -> new NoSuchElementException("Rota " + rotaId + " não encontrada"));
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Esta rota já foi assumida por outro motoboy ou não está mais disponível (status atual: "
                            + existente.getStatus() + ")");
        }
        EntregaRota rota = obterRota(rotaId);
        proximaParadaAcionavel(rota).ifPresent(EntregaParada::marcarACaminho);
        return rota;
    }

    @Transactional
    public EntregaRota registrarChegada(Long rotaId, Long paradaId, Usuario motoboy) {
        EntregaRota rota = validarPropriedadeEExecucao(rotaId, motoboy);
        EntregaParada parada = paradaAcionavelOuFalhar(rota, paradaId);
        parada.marcarChegou();
        return rota;
    }

    @Transactional
    public EntregaRota confirmarEntrega(Long rotaId, Long paradaId, Usuario motoboy, String formaPagamentoRecebida,
                                         Integer avaliacaoEntrega, String ocorrencias, String observacao) {
        EntregaRota rota = validarPropriedadeEExecucao(rotaId, motoboy);
        EntregaParada parada = paradaAcionavelOuFalhar(rota, paradaId);
        boolean divergente = pagamentoDivergente(parada, formaPagamentoRecebida);
        parada.confirmarEntrega(formaPagamentoRecebida, divergente, avaliacaoEntrega, ocorrencias, observacao);
        avancarOuConcluir(rota);
        return rota;
    }

    @Transactional
    public EntregaRota registrarFalha(Long rotaId, Long paradaId, Usuario motoboy, StatusEntregaParada falhaStatus,
                                       String motivo, String observacao) {
        EntregaRota rota = validarPropriedadeEExecucao(rotaId, motoboy);
        EntregaParada parada = paradaAcionavelOuFalhar(rota, paradaId);
        parada.registrarFalha(falhaStatus, motivo, observacao);
        avancarOuConcluir(rota);
        return rota;
    }

    @Transactional
    public EntregaParada regenerarCodigo(Long rotaId, Long paradaId) {
        EntregaRota rota = obterRota(rotaId);
        EntregaParada parada = rota.getParadas().stream().filter(p -> p.getId().equals(paradaId)).findFirst()
                .orElseThrow(() -> new NoSuchElementException("Parada " + paradaId + " não encontrada na rota " + rotaId));
        parada.regenerarCodigo(gerarCodigoEntrega());
        return parada;
    }

    /**
     * Sempre mostra os dois números: o que já está garantido (paradas
     * ENTREGUE) e o total previsto se a rota inteira for concluída — o
     * motoboy vê quanto vai ganhar antes de iniciar (tudo "previsto") e
     * continua vendo o total previsto durante a execução, em vez de a
     * comissão parecer "zerar" assim que ele começa a rota.
     */
    @Transactional(readOnly = true)
    public GanhoMotoboyView calcularGanho(Long rotaId) {
        EntregaRota rota = obterRota(rotaId);
        BigDecimal percentual = rota.getPercentualComissaoSnapshot();
        BigDecimal freteConfirmado = somarFrete(rota, p -> p.getStatus() == StatusEntregaParada.ENTREGUE);
        BigDecimal freteProjetadoTotal = somarFrete(rota, p -> p.getStatus() != StatusEntregaParada.CANCELADA);
        return new GanhoMotoboyView(percentual, comissaoSobre(freteConfirmado, percentual),
                comissaoSobre(freteProjetadoTotal, percentual));
    }

    private BigDecimal somarFrete(EntregaRota rota, java.util.function.Predicate<EntregaParada> filtro) {
        return rota.getParadas().stream().filter(filtro).map(EntregaParada::getValorFreteSnapshot)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal comissaoSobre(BigDecimal frete, BigDecimal percentual) {
        return frete.multiply(percentual).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    private EntregaRota validarPropriedadeEExecucao(Long rotaId, Usuario motoboy) {
        EntregaRota rota = obterRota(rotaId);
        if (rota.getStatus() != StatusEntregaRota.EM_EXECUCAO) {
            throw new IllegalStateException("Rota " + rotaId + " não está em execução (status atual: " + rota.getStatus() + ")");
        }
        if (rota.getEntregador() == null || !rota.getEntregador().getId().equals(motoboy.getId())) {
            throw new AccessDeniedException("Esta rota não está atribuída a você");
        }
        return rota;
    }

    /** Só a próxima parada acionável pode ser alterada — bloqueia toques fora de ordem. */
    private EntregaParada paradaAcionavelOuFalhar(EntregaRota rota, Long paradaId) {
        EntregaParada proxima = proximaParadaAcionavel(rota)
                .orElseThrow(() -> new IllegalStateException("Rota " + rota.getId() + " não tem parada pendente"));
        if (!proxima.getId().equals(paradaId)) {
            throw new IllegalStateException("Só é possível agir na próxima parada da rota (#" + proxima.getOrdem() + ")");
        }
        return proxima;
    }

    private java.util.Optional<EntregaParada> proximaParadaAcionavel(EntregaRota rota) {
        return rota.getParadas().stream().filter(p -> !p.isConcluida()).findFirst();
    }

    /** Sem ação explícita de "concluir rota": ela vira CONCLUIDA sozinha quando não sobra parada acionável. */
    private void avancarOuConcluir(EntregaRota rota) {
        var proxima = proximaParadaAcionavel(rota);
        if (proxima.isPresent()) {
            proxima.get().marcarACaminho();
        } else {
            rota.concluir();
        }
    }

    private boolean pagamentoDivergente(EntregaParada parada, String formaPagamentoRecebida) {
        if (formaPagamentoRecebida == null || formaPagamentoRecebida.isBlank()) {
            return false;
        }
        Venda venda = parada.getVenda();
        return venda.getPagamentos().stream().findFirst()
                .map(p -> !p.getFormaPagamento().getNome().equalsIgnoreCase(formaPagamentoRecebida))
                .orElse(false);
    }

    private List<Venda> carregarVendasParaRota(List<Long> vendaIds) {
        if (vendaIds == null || vendaIds.size() < MIN_VENDAS_ROTA) {
            throw new IllegalArgumentException("Uma rota precisa de ao menos " + MIN_VENDAS_ROTA + " vendas");
        }
        if (vendaIds.size() > MAX_VENDAS_ROTA) {
            throw new IllegalArgumentException("Uma rota aceita no máximo " + MAX_VENDAS_ROTA + " vendas");
        }
        List<Venda> vendas = vendaRepository.findAllById(vendaIds);
        if (vendas.size() != vendaIds.size()) {
            throw new NoSuchElementException("Uma ou mais vendas informadas não foram encontradas");
        }
        for (Venda venda : vendas) {
            if (venda.getModoEntrega() != ModoEntrega.ENTREGA) {
                throw new IllegalArgumentException("Venda " + venda.getId() + " não está em modo ENTREGA");
            }
            if (venda.getEnderecoEntrega() == null || venda.getEnderecoEntrega().isBlank()) {
                throw new IllegalArgumentException("Venda " + venda.getId() + " não tem endereço de entrega");
            }
        }
        return vendas;
    }

    private List<DeliveryRouteService.DeliveryStopInput> paraEntradasDaRota(List<Venda> vendas) {
        List<DeliveryRouteService.DeliveryStopInput> entradas = new ArrayList<>(vendas.size());
        for (Venda venda : vendas) {
            String clienteNome = venda.getCliente() != null ? venda.getCliente().getNome() : "Cliente";
            entradas.add(new DeliveryRouteService.DeliveryStopInput(venda.getId(), clienteNome,
                    venda.getEnderecoEntrega(), venda.getNumero(), venda.getStatus().name()));
        }
        return entradas;
    }

    private String gerarCodigoEntrega() {
        return String.valueOf(100000 + RANDOM.nextInt(900000));
    }

    public record GanhoMotoboyView(BigDecimal percentualComissao, BigDecimal comissaoConfirmada,
                                    BigDecimal comissaoProjetadaTotal) {
    }
}
