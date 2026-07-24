package br.com.primeiroprontuario.health;

import br.com.primeiroprontuario.auth.AuthSecurityConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
class HealthSecurityConfiguration {

    @Bean
    SecurityFilterChain healthSecurityFilterChain(
            HttpSecurity http, AuthSecurityConfiguration authSecurityConfiguration) throws Exception {
        return authSecurityConfiguration.configure(http).build();
    }
}
