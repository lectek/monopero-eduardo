package br.com.minimercadinho.saas;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class MiniMercadinhoSaasApplication {

    public static void main(String[] args) {
        SpringApplication.run(MiniMercadinhoSaasApplication.class, args);
    }
}
