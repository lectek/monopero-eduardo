package br.com.lojagenerica.gestao.web;

import br.com.lojagenerica.core.acesso.PapelRepository;
import br.com.lojagenerica.core.acesso.Usuario;
import br.com.lojagenerica.core.acesso.UsuarioGestaoService;
import br.com.lojagenerica.core.acesso.UsuarioRepository;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * CRUD de usuário (funcionário com login). Criar/redefinir senha escreve
 * também em {@code plataforma.identidade_usuario} — ver
 * {@link UsuarioGestaoService}, que cuida da travessia entre os dois
 * schemas. E-mail não é editável aqui de propósito: é a chave do índice
 * de login global, mudar exigiria manter os dois schemas em sincronia
 * (fica pra quando alguém precisar de fato).
 */
@Controller
@PreAuthorize("hasAuthority('USUARIO_GERENCIAR')")
public class UsuarioGestaoController {

    private final UsuarioRepository usuarioRepository;
    private final PapelRepository papelRepository;
    private final UsuarioGestaoService usuarioGestaoService;

    public UsuarioGestaoController(UsuarioRepository usuarioRepository, PapelRepository papelRepository,
                                    UsuarioGestaoService usuarioGestaoService) {
        this.usuarioRepository = usuarioRepository;
        this.papelRepository = papelRepository;
        this.usuarioGestaoService = usuarioGestaoService;
    }

    @GetMapping("/gestao/usuarios")
    public String listar(Model model) {
        model.addAttribute("usuarios", usuarioRepository.findAllComPapeis());
        return "pages/gestao/usuarios/lista";
    }

    @GetMapping("/gestao/usuarios/novo")
    public String novoForm(Model model) {
        model.addAttribute("usuario", null);
        model.addAttribute("papeis", papelRepository.findAll());
        model.addAttribute("papelIdsSelecionados", Set.of());
        return "pages/gestao/usuarios/form";
    }

    @GetMapping("/gestao/usuarios/{id}/editar")
    public String editarForm(@PathVariable Long id, Model model) {
        Usuario usuario = usuarioRepository.findByIdComPapeisEPermissoes(id)
                .orElseThrow(() -> new NoSuchElementException("Usuário " + id + " não encontrado"));
        model.addAttribute("usuario", usuario);
        model.addAttribute("papeis", papelRepository.findAll());
        model.addAttribute("papelIdsSelecionados",
                usuario.getPapeis().stream().map(p -> p.getId()).collect(Collectors.toSet()));
        return "pages/gestao/usuarios/form";
    }

    @PostMapping("/gestao/usuarios")
    public String criar(@RequestParam String nome, @RequestParam String email, @RequestParam String senha,
                         @RequestParam(required = false) List<Long> papelIds) {
        usuarioGestaoService.criarUsuario(nome, email, senha, conjunto(papelIds));
        return "redirect:/gestao/usuarios";
    }

    @PostMapping("/gestao/usuarios/{id}")
    public String atualizar(@PathVariable Long id, @RequestParam String nome,
                             @RequestParam(defaultValue = "false") boolean ativo,
                             @RequestParam(required = false) List<Long> papelIds) {
        Usuario usuario = buscarOuFalhar(id);
        usuario.setNome(nome);
        usuarioRepository.save(usuario);
        usuarioGestaoService.atualizarPapeis(id, conjunto(papelIds));
        usuarioGestaoService.alternarAtivo(id, ativo);
        return "redirect:/gestao/usuarios";
    }

    @PostMapping("/gestao/usuarios/{id}/redefinir-senha")
    public String redefinirSenha(@PathVariable Long id, @RequestParam String novaSenha) {
        usuarioGestaoService.redefinirSenha(id, novaSenha);
        return "redirect:/gestao/usuarios/" + id + "/editar";
    }

    private Set<Long> conjunto(List<Long> ids) {
        return ids == null ? Set.of() : Set.copyOf(ids);
    }

    private Usuario buscarOuFalhar(Long id) {
        return usuarioRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Usuário " + id + " não encontrado"));
    }
}
