package br.com.lojagenerica.platform;

import br.com.lojagenerica.platform.domain.Empresa;
import br.com.lojagenerica.platform.domain.EmpresaRepository;
import br.com.lojagenerica.platform.domain.IdentidadeUsuario;
import br.com.lojagenerica.platform.domain.IdentidadeUsuarioRepository;
import java.util.List;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Passo 1 do login: resolve QUAL empresa, antes de existir qualquer
 * {@link br.com.lojagenerica.multitenancy.TenantContext} — consulta só o
 * schema "plataforma" (índice {@code identidade_usuario}), nunca o schema
 * de um tenant.
 */
@Service
public class IdentidadeService {

    private final IdentidadeUsuarioRepository identidadeUsuarioRepository;
    private final EmpresaRepository empresaRepository;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public IdentidadeService(IdentidadeUsuarioRepository identidadeUsuarioRepository, EmpresaRepository empresaRepository) {
        this.identidadeUsuarioRepository = identidadeUsuarioRepository;
        this.empresaRepository = empresaRepository;
    }

    public ResultadoAutenticacao autenticar(String email, String senha, Long empresaIdEscolhida) {
        List<IdentidadeUsuario> candidatas = identidadeUsuarioRepository.findByEmailIgnoreCaseAndAtivoTrue(email);
        if (empresaIdEscolhida != null) {
            candidatas = candidatas.stream().filter(i -> i.getEmpresaId().equals(empresaIdEscolhida)).toList();
        }

        List<IdentidadeUsuario> comSenhaValida = candidatas.stream()
                .filter(i -> passwordEncoder.matches(senha == null ? "" : senha, i.getSenhaHash()))
                .toList();

        if (comSenhaValida.isEmpty()) {
            return new ResultadoAutenticacao.Falha();
        }

        if (comSenhaValida.size() > 1) {
            List<Empresa> empresas = comSenhaValida.stream()
                    .map(i -> empresaRepository.findById(i.getEmpresaId()).orElseThrow())
                    .toList();
            return new ResultadoAutenticacao.PrecisaEscolherEmpresa(empresas);
        }

        IdentidadeUsuario identidade = comSenhaValida.get(0);
        Empresa empresa = empresaRepository.findById(identidade.getEmpresaId())
                .orElseThrow(() -> new IllegalStateException("Empresa " + identidade.getEmpresaId() + " não encontrada"));
        return new ResultadoAutenticacao.Sucesso(empresa, identidade.getUsuarioIdTenant());
    }
}
