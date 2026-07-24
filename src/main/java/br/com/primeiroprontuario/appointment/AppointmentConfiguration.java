package br.com.primeiroprontuario.appointment;

import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class AppointmentConfiguration {

    @Bean
    ZoneId appointmentZoneId(@Value("${app.time-zone:America/Sao_Paulo}") String configuredZone) {
        return ZoneId.of(configuredZone);
    }
}
