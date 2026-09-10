package br.com.lojagenerica.core.parceiro.web;

import br.com.lojagenerica.core.parceiro.Cliente;
import br.com.lojagenerica.core.parceiro.ClienteRepository;
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
@RequestMapping("/api/v1/clientes")
public class ClienteController {

    private final ClienteRepository clienteRepository;

    public ClienteController(ClienteRepository clienteRepository) {
        this.clienteRepository = clienteRepository;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('CLIENTE_LER')")
    public List<ClienteResponse> listar() {
        return clienteRepository.findAll().stream().map(ClienteResponse::from).toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('CLIENTE_LER')")
    public ClienteResponse buscar(@PathVariable Long id) {
        return clienteRepository.findById(id).map(ClienteResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cliente não encontrado"));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('CLIENTE_ESCREVER')")
    public ResponseEntity<ClienteResponse> criar(@RequestBody CriarClienteRequest request) {
        Cliente cliente = new Cliente(request.nome());
        cliente.setDocumento(request.documento());
        cliente.setTelefone(request.telefone());
        cliente.setEmail(request.email());
        cliente = clienteRepository.save(cliente);
        return ResponseEntity.status(HttpStatus.CREATED).body(ClienteResponse.from(cliente));
    }

    public record CriarClienteRequest(@NotBlank String nome, String documento, String telefone, String email) {
    }

    public record ClienteResponse(Long id, String nome, String documento, String telefone, String email, String status) {
        static ClienteResponse from(Cliente c) {
            return new ClienteResponse(c.getId(), c.getNome(), c.getDocumento(), c.getTelefone(), c.getEmail(),
                    c.getStatus().name());
        }
    }
}
