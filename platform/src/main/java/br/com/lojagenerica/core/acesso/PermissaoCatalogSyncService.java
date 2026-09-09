package br.com.lojagenerica.core.acesso;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Garante que todo código de {@link PermissaoCatalogo} existe na tabela
 * {@code permissao} do schema de tenant atualmente resolvido — e que
 * ADMINISTRADOR (papel de sistema) tem todos eles. Chamado tanto no boot,
 * uma vez por tenant ativo ({@link PermissaoSyncRunner}), quanto durante o
 * provisionamento de um tenant novo (antes dele existir em "ATIVA").
 *
 * <p>Roda sempre com o {@link br.com.lojagenerica.multitenancy.TenantContext}
 * já setado pelo chamador — este serviço não decide qual schema, só age no
 * que está resolvido.
 */
@Service
public class PermissaoCatalogSyncService {

    private final PermissaoRepository permissaoRepository;
    private final PapelRepository papelRepository;

    public PermissaoCatalogSyncService(PermissaoRepository permissaoRepository, PapelRepository papelRepository) {
        this.permissaoRepository = permissaoRepository;
        this.papelRepository = papelRepository;
    }

    @Transactional
    public void sincronizarSchemaAtual() {
        for (PermissaoCatalogo item : PermissaoCatalogo.values()) {
            permissaoRepository.findByCodigo(item.codigo())
                    .orElseGet(() -> permissaoRepository.save(
                            new Permissao(item.codigo(), item.modulo(), item.descricao())));
        }

        papelRepository.findByNome("ADMINISTRADOR").ifPresent(administrador -> {
            if (administrador.isSistema()) {
                administrador.getPermissoes().addAll(permissaoRepository.findAll());
                papelRepository.save(administrador);
            }
        });
    }
}
