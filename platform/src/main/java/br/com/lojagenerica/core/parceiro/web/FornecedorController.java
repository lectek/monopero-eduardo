package br.com.lojagenerica.core.parceiro.web;

import br.com.lojagenerica.core.parceiro.Fornecedor;
import br.com.lojagenerica.core.parceiro.FornecedorRepository;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/fornecedores")
public class FornecedorController {

    private final FornecedorRepository fornecedorRepository;

    public FornecedorController(FornecedorRepository fornecedorRepository) {
        this.fornecedorRepository = fornecedorRepository;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('FORNECEDOR_LER')")
    public List<FornecedorResponse> listar() {
        return fornecedorRepository.findAll().stream().map(FornecedorResponse::from).toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('FORNECEDOR_LER')")
    public FornecedorResponse buscar(@PathVariable Long id) {
        return fornecedorRepository.findById(id).map(FornecedorResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Fornecedor não encontrado"));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('FORNECEDOR_ESCREVER')")
    public ResponseEntity<FornecedorResponse> criar(@RequestBody CriarFornecedorRequest request) {
        Fornecedor fornecedor = new Fornecedor(request.razaoSocial());
        fornecedor.setNomeFantasia(request.nomeFantasia());
        fornecedor.setDocumento(request.documento());
        fornecedor = fornecedorRepository.save(fornecedor);
        return ResponseEntity.status(HttpStatus.CREATED).body(FornecedorResponse.from(fornecedor));
    }

    public record CriarFornecedorRequest(@NotBlank String razaoSocial, String nomeFantasia, String documento) {
    }

    public record FornecedorResponse(Long id, String razaoSocial, String nomeFantasia, String status) {
        static FornecedorResponse from(Fornecedor f) {
            return new FornecedorResponse(f.getId(), f.getRazaoSocial(), f.getNomeFantasia(), f.getStatus().name());
        }
    }
}
