package com.clinica.recordatorios.repository;

import com.clinica.recordatorios.domain.CanalNotificacion;
import com.clinica.recordatorios.domain.Cita;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
class CitaRepositoryTest {

    @Autowired
    private CitaRepository citaRepository;

    @Test
    void findByFechaHoraCitaBetweenAndRecordatorioEnviadoFalse_soloRetornaCitasPendientesEnVentana() {
        LocalDateTime ahora = LocalDateTime.of(2026, 8, 10, 9, 0);
        LocalDateTime en24Horas = ahora.plusHours(24);

        // 1. En ventana y pendiente (debe incluirse)
        Cita citaEnVentanaPendiente = Cita.builder()
                .pacienteNombre("Ana Torres")
                .pacienteContacto("ana@example.com")
                .fechaHoraCita(ahora.plusHours(5))
                .canalPreferido(CanalNotificacion.EMAIL)
                .recordatorioEnviado(false)
                .build();

        // 2. En ventana pero ya enviada (no debe incluirse)
        Cita citaEnVentanaYaEnviada = Cita.builder()
                .pacienteNombre("Luis Ramirez")
                .pacienteContacto("+573111234567")
                .fechaHoraCita(ahora.plusHours(10))
                .canalPreferido(CanalNotificacion.SMS)
                .recordatorioEnviado(true)
                .build();

        // 3. Fuera de ventana (después de las 24h) y pendiente (no debe incluirse)
        Cita citaFueraDeVentanaPendiente = Cita.builder()
                .pacienteNombre("Pedro Castro")
                .pacienteContacto("pedro@example.com")
                .fechaHoraCita(ahora.plusHours(48))
                .canalPreferido(CanalNotificacion.EMAIL)
                .recordatorioEnviado(false)
                .build();

        citaRepository.saveAll(List.of(citaEnVentanaPendiente, citaEnVentanaYaEnviada, citaFueraDeVentanaPendiente));

        List<Cita> resultado = citaRepository.findByFechaHoraCitaBetweenAndRecordatorioEnviadoFalse(ahora, en24Horas);

        assertEquals(1, resultado.size());
        Cita citaObtenida = resultado.get(0);
        assertEquals("Ana Torres", citaObtenida.getPacienteNombre());
        assertFalse(citaObtenida.isRecordatorioEnviado());
        assertTrue(citaObtenida.getFechaHoraCita().isAfter(ahora) && citaObtenida.getFechaHoraCita().isBefore(en24Horas));
    }
}

