package br.com.minimercadinho.saas.application.catalogo;

import br.com.minimercadinho.saas.adapters.outbound.ims.ImsProdutoRepository;
import br.com.minimercadinho.saas.domain.catalogo.Produto;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class CatalogoService {

    private final ImsProdutoRepository imsProdutoRepository;

    public CatalogoService(ImsProdutoRepository imsProdutoRepository) {
        this.imsProdutoRepository = imsProdutoRepository;
    }

    /** Vitrine pública: só produtos com preço e estoque cadastrados no rbp.db. */
    public List<Produto> listarVitrine() {
        return imsProdutoRepository.fetchVitrine();
    }
}
