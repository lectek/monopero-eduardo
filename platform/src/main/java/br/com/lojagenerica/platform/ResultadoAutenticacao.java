package br.com.lojagenerica.platform;

import br.com.lojagenerica.platform.domain.Empresa;
import java.util.List;

/**
 * Login não pode resolver schema por e-mail antes de saber o tenant — um
 * mesmo e-mail pode existir em N empresas (ex.: o dono, com acesso a
 * todas). Ver IdentidadeService.
 */
public sealed interface ResultadoAutenticacao {

    record Sucesso(Empresa empresa, Long usuarioIdTenant) implements ResultadoAutenticacao {
    }

    /** Credenciais batem em mais de uma empresa — cliente escolhe qual. */
    record PrecisaEscolherEmpresa(List<Empresa> empresas) implements ResultadoAutenticacao {
    }

    record Falha() implements ResultadoAutenticacao {
    }
}
