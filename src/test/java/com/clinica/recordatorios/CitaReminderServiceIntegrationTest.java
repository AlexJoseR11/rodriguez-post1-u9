package com.clinica.recordatorios;

import com.clinica.recordatorios.channel.gateway.NotificadorSmsGateway;
import com.clinica.recordatorios.domain.CanalNotificacion;
import com.clinica.recordatorios.domain.Cita;
import com.clinica.recordatorios.repository.CitaRepository;
import com.clinica.recordatorios.service.CitaReminderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@SpringBootTest(properties = "spring.main.allow-bean-definition-overriding=true")
class CitaReminderServiceIntegrationTest {

    @TestConfiguration
    static class TestClockConfig {
        @Bean
        @Primary
        public Clock clock() {
            return Clock.fixed(Instant.parse("2026-08-10T09:00:00Z"), ZoneId.of("UTC"));
        }
    }

    @MockBean
    private NotificadorSmsGateway notificadorSmsGateway;

    @Autowired
    private CitaReminderService citaReminderService;

    @Autowired
    private CitaRepository citaRepository;

    @BeforeEach
    void cleanUp() {
        citaRepository.deleteAll();
    }

    @Test
    void enviarRecordatoriosPendientes_contextoCompletoDeSpring_persisteYNotificaCitasEnVentana() {
        Cita citaSms = Cita.builder()
                .pacienteNombre("Laura Martinez")
                .pacienteContacto("+573005558899")
                .fechaHoraCita(LocalDateTime.of(2026, 8, 10, 16, 0))
                .canalPreferido(CanalNotificacion.SMS)
                .recordatorioEnviado(false)
                .build();

        citaSms = citaRepository.save(citaSms);

        int procesadas = citaReminderService.enviarRecordatoriosPendientes();

        assertEquals(1, procesadas);
        verify(notificadorSmsGateway).enviarSms(eq("+573005558899"), contains("Recordatorio: tiene una cita el"));

        Cita citaActualizada = citaRepository.findById(citaSms.getId()).orElseThrow();
        assertTrue(citaActualizada.isRecordatorioEnviado());
    }
}
