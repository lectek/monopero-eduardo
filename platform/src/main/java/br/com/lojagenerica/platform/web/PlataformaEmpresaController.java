package br.com.lojagenerica.platform.web;

import br.com.lojagenerica.platform.ProvisionamentoTenantService;
import br.com.lojagenerica.platform.ProvisionarEmpresaCommand;
import br.com.lojagenerica.platform.domain.Empresa;
import br.com.lojagenerica.platform.domain.StatusEmpresa;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Provisionamento de empresa nova — operação rara e sensível (cria schema,
 * roda migration, cria o primeiro admin). Protegida por uma chave
 * compartilhada (env {@code PLATFORM_ADMIN_KEY}) até existir um mecanismo
 * de superadmin da plataforma de verdade; sem a chave configurada, o
 * endpoint fica desligado (nunca aberto por acidente).
 */
@RestController
@RequestMapping("/api/plataforma/empresas")
public class PlataformaEmpresaController {

    private final ProvisionamentoTenantService provisionamentoTenantService;
    private final String chaveEsperada;

    public PlataformaEmpresaController(
            ProvisionamentoTenantService provisionamentoTenantService,
            @Value("${platform.admin-key:}") String chaveEsperada) {
        this.provisionamentoTenantService = provisionamentoTenantService;
        this.chaveEsperada = chaveEsperada;
    }

    @PostMapping
    public ResponseEntity<EmpresaResponse> provisionar(
            @RequestBody ProvisionarEmpresaRequest request,
            @RequestHeader(value = "X-Platform-Admin-Key", required = false) String chaveRecebida) {
        exigirChaveValida(chaveRecebida);

        Empresa empresa = provisionamentoTenantService.provisionar(new ProvisionarEmpresaCommand(
                request.razaoSocial(), request.nomeFantasia(), request.documento(), request.subdominio(),
                request.administradorNome(), request.administradorEmail(), request.administradorSenha()));

        return ResponseEntity.status(HttpStatus.CREATED).body(EmpresaResponse.from(empresa));
    }

    private void exigirChaveValida(String chaveRecebida) {
        if (chaveEsperada == null || chaveEsperada.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Provisionamento desligado: PLATFORM_ADMIN_KEY não configurada");
        }
        if (!chaveEsperada.equals(chaveRecebida)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Chave de plataforma inválida");
        }
    }

    public record ProvisionarEmpresaRequest(
            @NotBlank String razaoSocial,
            String nomeFantasia,
            String documento,
            @NotBlank String subdominio,
            @NotBlank String administradorNome,
            @NotBlank @Email String administradorEmail,
            @NotBlank @Size(min = 8) String administradorSenha) {
    }

    public record EmpresaResponse(Long id, String schemaNome, String nomeFantasia, String subdominio,
                                   StatusEmpresa status, Instant criadoEm) {
        static EmpresaResponse from(Empresa empresa) {
            return new EmpresaResponse(empresa.getId(), empresa.getSchemaNome(), empresa.getNomeFantasia(),
                    empresa.getSubdominio(), empresa.getStatus(), empresa.getCriadoEm());
        }
    }
}
