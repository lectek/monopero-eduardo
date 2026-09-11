package br.com.lojagenerica.core.acesso;

import br.com.lojagenerica.multitenancy.TenantContext;
import br.com.lojagenerica.platform.domain.Empresa;
import br.com.lojagenerica.platform.domain.EmpresaRepository;
import br.com.lojagenerica.platform.domain.IdentidadeUsuario;
import br.com.lojagenerica.platform.domain.IdentidadeUsuarioRepository;
import java.util.HashSet;
import java.util.NoSuchElementException;
import java.util.Set;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Criar/gerenciar um usuário de {@code /gestao/**} escreve em DOIS
 * schemas: o {@code usuario} do tenant (nome, papéis) e o
 * {@code plataforma.identidade_usuario} (e-mail, senha — é o índice de
 * login global, ver {@link br.com.lojagenerica.platform.IdentidadeService}).
 * Deliberadamente sem {@code @Transactional} no nível do serviço — mesma
 * razão de {@code ProvisionamentoTenantService}: os dois schemas precisam
 * de sessões Hibernate resolvidas com o {@link TenantContext} certo no
 * momento em que são abertas, e uma transação ambiente reaproveitaria a
 * mesma sessão (e o mesmo schema resolvido) pros dois.
 */
@Service
public class UsuarioGestaoService {

    private final UsuarioRepository usuarioRepository;
    private final PapelRepository papelRepository;
    private final EmpresaRepository empresaRepository;
    private final IdentidadeUsuarioRepository identidadeUsuarioRepository;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public UsuarioGestaoService(UsuarioRepository usuarioRepository, PapelRepository papelRepository,
                                 EmpresaRepository empresaRepository, IdentidadeUsuarioRepository identidadeUsuarioRepository) {
        this.usuarioRepository = usuarioRepository;
        this.papelRepository = papelRepository;
        this.empresaRepository = empresaRepository;
        this.identidadeUsuarioRepository = identidadeUsuarioRepository;
    }

    /** Chamado com {@link TenantContext} já setado pro schema do tenant (ver AdminTenantSessionFilter). */
    public Long criarUsuario(String nome, String email, String senha, Set<Long> papelIds) {
        String schemaAtual = TenantContext.require();

        Set<Papel> papeis = new HashSet<>(papelRepository.findAllById(papelIds));
        Usuario usuario = new Usuario(nome, email);
        usuario.getPapeis().addAll(papeis);
        usuario = usuarioRepository.save(usuario);
        Long usuarioIdTenant = usuario.getId();

        TenantContext.clear();
        try {
            Empresa empresa = empresaDoSchema(schemaAtual);
            if (!identidadeUsuarioRepository.findByEmailIgnoreCaseAndAtivoTrue(email).stream()
                    .filter(i -> i.getEmpresaId().equals(empresa.getId())).toList().isEmpty()) {
                throw new IllegalArgumentException("Já existe um usuário com este e-mail nesta empresa.");
            }
            identidadeUsuarioRepository.save(new IdentidadeUsuario(email, empresa.getId(), usuarioIdTenant,
                    passwordEncoder.encode(senha)));
        } finally {
            TenantContext.set(schemaAtual);
        }
        return usuarioIdTenant;
    }

    public void atualizarPapeis(Long usuarioId, Set<Long> papelIds) {
        Usuario usuario = usuarioRepository.findByIdComPapeisEPermissoes(usuarioId)
                .orElseThrow(() -> new NoSuchElementException("Usuário " + usuarioId + " não encontrado"));
        Set<Papel> novosPapeis = new HashSet<>(papelRepository.findAllById(papelIds));
        usuario.getPapeis().clear();
        usuario.getPapeis().addAll(novosPapeis);
        usuarioRepository.save(usuario);
    }

    public void alternarAtivo(Long usuarioId, boolean ativo) {
        Usuario usuario = buscarOuFalhar(usuarioId);
        usuario.setAtivo(ativo);
        usuarioRepository.save(usuario);
    }

    public void redefinirSenha(Long usuarioIdTenant, String novaSenha) {
        String schemaAtual = TenantContext.require();
        TenantContext.clear();
        try {
            Empresa empresa = empresaDoSchema(schemaAtual);
            IdentidadeUsuario identidade = identidadeUsuarioRepository
                    .findByUsuarioIdTenantAndEmpresaId(usuarioIdTenant, empresa.getId())
                    .orElseThrow(() -> new NoSuchElementException(
                            "Índice de login não encontrado pro usuário " + usuarioIdTenant));
            identidade.setSenhaHash(passwordEncoder.encode(novaSenha));
            identidadeUsuarioRepository.save(identidade);
        } finally {
            TenantContext.set(schemaAtual);
        }
    }

    private Empresa empresaDoSchema(String schema) {
        return empresaRepository.findBySchemaNome(schema)
                .orElseThrow(() -> new IllegalStateException("Empresa do schema " + schema + " não encontrada"));
    }

    private Usuario buscarOuFalhar(Long id) {
        return usuarioRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Usuário " + id + " não encontrado"));
    }
}
