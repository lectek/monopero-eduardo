package br.com.lojagenerica.security.admin;

import br.com.lojagenerica.core.acesso.Permissao;
import br.com.lojagenerica.core.acesso.Usuario;
import br.com.lojagenerica.core.acesso.UsuarioRepository;
import br.com.lojagenerica.multitenancy.TenantContext;
import br.com.lojagenerica.platform.IdentidadeService;
import br.com.lojagenerica.platform.ResultadoAutenticacao;
import br.com.lojagenerica.platform.domain.Empresa;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

/**
 * Login de {@code /gestao/**} reusa o MESMO passo 1 do login da API
 * (e-mail resolve empresa via {@code plataforma.identidade_usuario}, antes
 * de qualquer {@link TenantContext} — ver {@link IdentidadeService}), mas
 * termina numa {@link Authentication} de sessão (Spring Security guarda no
 * {@code HttpSession} sozinho) em vez de um JWT: não tem cliente HTTP
 * externo pra carregar token nenhum aqui, é o próprio navegador com cookie
 * de sessão.
 *
 * <p>Um e-mail cadastrado em mais de uma empresa (caso raro, ex.: o dono
 * com acesso a duas lojas) ainda não tem seletor nesta tela — falha com
 * uma mensagem clara em vez de silenciosamente logar na empresa errada;
 * suporte a escolher a empresa fica pra quando alguém precisar de fato.
 */
@Component
public class AdminAuthenticationProvider implements AuthenticationProvider {

    private final IdentidadeService identidadeService;
    private final UsuarioRepository usuarioRepository;

    public AdminAuthenticationProvider(IdentidadeService identidadeService, UsuarioRepository usuarioRepository) {
        this.identidadeService = identidadeService;
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String email = authentication.getName();
        String senha = String.valueOf(authentication.getCredentials());

        ResultadoAutenticacao resultado = identidadeService.autenticar(email, senha, null);
        if (resultado instanceof ResultadoAutenticacao.PrecisaEscolherEmpresa) {
            throw new BadCredentialsException(
                    "Este e-mail tem acesso a mais de uma empresa. Fale com o suporte pra resolver o login.");
        }
        if (!(resultado instanceof ResultadoAutenticacao.Sucesso sucesso)) {
            throw new BadCredentialsException("E-mail ou senha inválidos.");
        }

        Empresa empresa = sucesso.empresa();
        TenantContext.set(empresa.getSchemaNome());
        try {
            Usuario usuario = usuarioRepository.findByIdComPapeisEPermissoes(sucesso.usuarioIdTenant())
                    .orElseThrow(() -> new NoSuchElementException("Usuário " + sucesso.usuarioIdTenant() + " não encontrado"));
            if (!usuario.isAtivo()) {
                throw new BadCredentialsException("Usuário inativo.");
            }

            List<SimpleGrantedAuthority> authorities = usuario.getPapeis().stream()
                    .flatMap(papel -> papel.getPermissoes().stream())
                    .map(Permissao::getCodigo)
                    .distinct()
                    .map(SimpleGrantedAuthority::new)
                    .toList();

            AdminPrincipal principal = new AdminPrincipal(usuario.getId(), usuario.getNome(), usuario.getEmail(),
                    empresa.getSchemaNome(), empresa.getNomeFantasia());
            return new UsernamePasswordAuthenticationToken(principal, null, authorities);
        } finally {
            TenantContext.clear();
        }
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
