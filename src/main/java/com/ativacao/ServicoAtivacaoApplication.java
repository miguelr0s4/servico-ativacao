package com.ativacao;

import com.ativacao.integracao.ProviderProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@EnableConfigurationProperties(ProviderProperties.class)
@SpringBootApplication
public class ServicoAtivacaoApplication {

    public static void main(String[] args) {
        SpringApplication.run(ServicoAtivacaoApplication.class, args);
    }
}
