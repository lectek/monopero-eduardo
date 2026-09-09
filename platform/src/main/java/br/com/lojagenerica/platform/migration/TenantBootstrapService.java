package br.com.lojagenerica.platform.migration;

import br.com.lojagenerica.core.acesso.Papel;
import br.com.lojagenerica.core.acesso.PapelRepository;
import br.com.lojagenerica.core.acesso.PermissaoCatalogSyncService;
import br.com.lojagenerica.core.acesso.Usuario;
import br.com.lojagenerica.core.acesso.UsuarioRepository;
import br.com.lojagenerica.core.cadastro.TipoMovimentacaoRepository;
import br.com.lojagenerica.core.cadastro.TipoMovimentacaoSistema;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * O que acontece dentro de um schema de tenant recém-criado, na primeira
 * vez — chamado por {@link br.com.lojagenerica.platform.ProvisionamentoTenantService}
 * com {@link br.com.lojagenerica.multitenancy.TenantContext} JÁ setado pro
 * schema novo. É um bean separado (não um método privado do orquestrador)
 * de propósito: precisa da sua própria transação/sessão Hibernate pra
 * resolver o tenant identifier corretamente — ver docs/CONTEXTO.md sobre a
 * armadilha de reaproveitar sessão entre dois schemas na mesma transação.
 *
 * <p>Nenhuma categoria/unidade/produto/forma de pagamento é criada aqui —
 * só o que o código referencia por código (tipos de movimentação de
 * sistema) e o mínimo pra existir acesso (papel ADMINISTRADOR + 1 usuário).
 */
@Service
public class TenantBootstrapService {

    private final PapelRepository papelRepository;
    private final UsuarioRepository usuarioRepository;
    private final TipoMovimentacaoRepository tipoMovimentacaoRepository;
    private final PermissaoCatalogSyncService permissaoCatalogSyncService;

    public TenantBootstrapService(
            PapelRepository papelRepository,
            UsuarioRepository usuarioRepository,
            TipoMovimentacaoRepository tipoMovimentacaoRepository,
            PermissaoCatalogSyncService permissaoCatalogSyncService) {
        this.papelRepository = papelRepository;
        this.usuarioRepository = usuarioRepository;
        this.tipoMovimentacaoRepository = tipoMovimentacaoRepository;
        this.permissaoCatalogSyncService = permissaoCatalogSyncService;
    }

    @Transactional
    public Long criarAdministradorInicial(String nome, String email) {
        for (TipoMovimentacaoSistema seed : TipoMovimentacaoSistema.values()) {
            if (tipoMovimentacaoRepository.findByCodigo(seed.codigo()).isEmpty()) {
                tipoMovimentacaoRepository.save(seed.paraEntidade());
            }
        }

        Papel administrador = papelRepository.findByNome("ADMINISTRADOR")
                .orElseGet(() -> papelRepository.save(new Papel("ADMINISTRADOR", "Acesso total ao sistema", true)));

        // Sincroniza DEPOIS de o papel existir — é ele quem ganha todas as
        // permissões do catálogo (ver PermissaoCatalogSyncService).
        permissaoCatalogSyncService.sincronizarSchemaAtual();

        Usuario usuario = new Usuario(nome, email);
        usuario.getPapeis().add(administrador);
        usuario = usuarioRepository.save(usuario);
        return usuario.getId();
    }
}
