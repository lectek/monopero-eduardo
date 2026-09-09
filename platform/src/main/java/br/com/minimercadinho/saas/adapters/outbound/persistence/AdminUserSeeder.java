package br.com.minimercadinho.saas.adapters.outbound.persistence;

import br.com.minimercadinho.saas.adapters.outbound.persistence.entity.AdminUserEntity;
import br.com.minimercadinho.saas.adapters.outbound.persistence.jpa.AdminUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Cria um admin de conveniência em desenvolvimento, lendo ADMIN_EMAIL/
 * ADMIN_PASSWORD do ambiente (com default só para dev). Roda depois do
 * {@link SchemaInitializer} (que cria a tabela).
 *
 * Só ativo no profile "dev" — em produção o acesso administrativo é criado
 * exclusivamente pelo IMS (tela "Acesso admin do site"), gravando direto em
 * admin_users no rbp.db compartilhado. Sem isso, uma instância em produção
 * sem ADMIN_EMAIL/ADMIN_PASSWORD configurados acabaria criando um admin com
 * senha padrão conhecida — o oposto do que se quer ("só o dono tem acesso").
 */
@Component
@Order(1)
@Profile("dev")
public class AdminUserSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminUserSeeder.class);

    private final AdminUserRepository adminUserRepository;
    private final Environment environment;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AdminUserSeeder(AdminUserRepository adminUserRepository, Environment environment) {
        this.adminUserRepository = adminUserRepository;
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (adminUserRepository.count() > 0) {
            return;
        }
        String email = environment.getProperty("ADMIN_EMAIL", "admin@minimercadinho.local");
        String password = environment.getProperty("ADMIN_PASSWORD", "admin123");

        AdminUserEntity admin = new AdminUserEntity();
        admin.setNome("Administrador");
        admin.setEmail(email);
        admin.setSenhaHash(passwordEncoder.encode(password));
        admin.setRole("ADMIN");
        admin.setAtivo(true);
        adminUserRepository.save(admin);

        log.warn("Admin inicial criado ({}). Troque a senha assim que possível — "
                + "em produção defina ADMIN_EMAIL/ADMIN_PASSWORD antes do primeiro boot.", email);
    }
}
