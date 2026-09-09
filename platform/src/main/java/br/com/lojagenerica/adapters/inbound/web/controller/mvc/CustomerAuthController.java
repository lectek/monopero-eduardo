package br.com.lojagenerica.adapters.inbound.web.controller.mvc;

import br.com.lojagenerica.adapters.outbound.persistence.entity.CustomerEntity;
import br.com.lojagenerica.adapters.outbound.persistence.repository.CustomerRepository;
import br.com.lojagenerica.adapters.outbound.persistence.repository.PedidoRepository;
import br.com.lojagenerica.application.service.customer.CustomerAuthService;
import org.springframework.core.env.Environment;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class CustomerAuthController {

    private final CustomerAuthService customerAuthService;
    private final CustomerRepository customerRepository;
    private final PedidoRepository pedidoRepository;
    private final Environment environment;

    public CustomerAuthController(
            CustomerAuthService customerAuthService,
            CustomerRepository customerRepository,
            PedidoRepository pedidoRepository,
            Environment environment
    ) {
        this.customerAuthService = customerAuthService;
        this.customerRepository = customerRepository;
        this.pedidoRepository = pedidoRepository;
        this.environment = environment;
    }

    @ModelAttribute("googleLoginEnabled")
    public boolean googleLoginEnabled() {
        return StringUtils.hasText(environment.getProperty("spring.security.oauth2.client.registration.google.client-id"));
    }

    @GetMapping("/login")
    public String login() {
        return "pages/auth/login";
    }

    @GetMapping("/cadastro")
    public String cadastroForm() {
        return "pages/auth/cadastro";
    }

    @PostMapping("/cadastro")
    public String cadastrar(
            @RequestParam String nome,
            @RequestParam String email,
            @RequestParam(required = false) String telefone,
            @RequestParam String senha,
            @RequestParam String confirmarSenha,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        if (senha == null || senha.length() < 6) {
            model.addAttribute("erro", "A senha precisa ter pelo menos 6 caracteres.");
            return "pages/auth/cadastro";
        }
        if (!senha.equals(confirmarSenha)) {
            model.addAttribute("erro", "As senhas não coincidem.");
            return "pages/auth/cadastro";
        }
        try {
            customerAuthService.cadastrar(nome, email, telefone, senha);
        } catch (IllegalArgumentException ex) {
            model.addAttribute("erro", ex.getMessage());
            return "pages/auth/cadastro";
        }
        redirectAttributes.addFlashAttribute("cadastroOk", true);
        return "redirect:/login";
    }

    @GetMapping("/minha-conta")
    public String minhaConta(Authentication authentication, Model model) {
        CustomerEntity cliente = customerRepository.findByEmailIgnoreCase(authentication.getName())
                .orElseThrow(() -> new IllegalStateException("Cliente autenticado não encontrado: " + authentication.getName()));
        model.addAttribute("cliente", cliente);
        model.addAttribute("pedidos", pedidoRepository.listarPorClienteComItensOrderByDataDesc(cliente.getId()));
        return "pages/cliente/conta";
    }
}
