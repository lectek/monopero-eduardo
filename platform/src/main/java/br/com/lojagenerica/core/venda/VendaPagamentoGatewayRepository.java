package br.com.lojagenerica.core.venda;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VendaPagamentoGatewayRepository extends JpaRepository<VendaPagamentoGateway, Long> {

    Optional<VendaPagamentoGateway> findByPaymentId(String paymentId);

    Optional<VendaPagamentoGateway> findByExternalReference(String externalReference);
}
