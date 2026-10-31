package com.clinica.recordatorios.service;

import com.clinica.recordatorios.channel.ReminderChannel;
import com.clinica.recordatorios.channel.ReminderChannelFactory;
import com.clinica.recordatorios.domain.CanalNotificacion;
import com.clinica.recordatorios.domain.Cita;
import com.clinica.recordatorios.repository.CitaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CitaReminderServiceImplTest {

    @Mock
    private CitaRepository citaRepository;

    @Mock
    private ReminderChannelFactory reminderChannelFactory;

    @Mock
    private ReminderChannel reminderChannelMock;

    @Mock
    private ReminderChannel canalUrgenteMock;

    private final Clock clockFijo = Clock.fixed(Instant.parse("2026-08-10T09:00:00Z"), ZoneId.of("UTC"));

    private CitaReminderServiceImpl citaReminderService;

    @BeforeEach
    void setUp() {
        citaReminderService = new CitaReminderServiceImpl(
                citaRepository,
                reminderChannelFactory,
                canalUrgenteMock,
                clockFijo
        );
    }

    @Test
    void enviarRecordatoriosPendientes_citaEnVentana_usaElCanalPreferido() {
        Cita cita = Cita.builder()
                .id(1L)
                .pacienteNombre("Carlos Gomez")
                .pacienteContacto("carlos@example.com")
                .fechaHoraCita(LocalDateTime.of(2026, 8, 10, 14, 0))
                .canalPreferido(CanalNotificacion.EMAIL)
                .recordatorioEnviado(false)
                .build();

        when(citaRepository.findByFechaHoraCitaBetweenAndRecordatorioEnviadoFalse(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(cita));
        when(reminderChannelFactory.obtenerCanal(CanalNotificacion.EMAIL))
                .thenReturn(reminderChannelMock);

        int procesadas = citaReminderService.enviarRecordatoriosPendientes();

        assertEquals(1, procesadas);
        verify(reminderChannelFactory).obtenerCanal(CanalNotificacion.EMAIL);
        verify(reminderChannelMock).enviar(eq(cita), eq("Recordatorio: tiene una cita el 2026-08-10T14:00"));
        assertTrue(cita.isRecordatorioEnviado());
        verify(citaRepository).save(cita);
    }

    @Test
    void enviarRecordatoriosPendientes_ventanaDeterministaSegunClockFijo_consultaLosLimitesExactos() {
        ArgumentCaptor<LocalDateTime> desdeCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> hastaCaptor = ArgumentCaptor.forClass(LocalDateTime.class);

        when(citaRepository.findByFechaHoraCitaBetweenAndRecordatorioEnviadoFalse(desdeCaptor.capture(), hastaCaptor.capture()))
                .thenReturn(Collections.emptyList());

        citaReminderService.enviarRecordatoriosPendientes();

        LocalDateTime esperadoDesde = LocalDateTime.of(2026, 8, 10, 9, 0);
        LocalDateTime esperadoHasta = LocalDateTime.of(2026, 8, 11, 9, 0);

        assertEquals(esperadoDesde, desdeCaptor.getValue());
        assertEquals(esperadoHasta, hastaCaptor.getValue());
    }

    @Test
    void enviarRecordatoriosPendientes_sinCitasPendientes_noConsultaLaFactory() {
        when(citaRepository.findByFechaHoraCitaBetweenAndRecordatorioEnviadoFalse(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());

        int procesadas = citaReminderService.enviarRecordatoriosPendientes();

        assertEquals(0, procesadas);
        verifyNoInteractions(reminderChannelFactory);
        verify(citaRepository, never()).save(any());
    }

    @Test
    void enviarRecordatorioUrgente_citaExistente_usaCanalUrgenteInyectado() {
        Cita cita = Cita.builder()
                .id(10L)
                .pacienteNombre("Maria Lopez")
                .pacienteContacto("+573001234567")
                .fechaHoraCita(LocalDateTime.of(2026, 8, 10, 10, 30))
                .canalPreferido(CanalNotificacion.SMS)
                .recordatorioEnviado(false)
                .build();

        when(citaRepository.findById(10L)).thenReturn(Optional.of(cita));

        citaReminderService.enviarRecordatorioUrgente(10L);

        verify(canalUrgenteMock).enviar(cita, "URGENTE: su cita es en menos de 2 horas y no ha sido confirmada");
        assertTrue(cita.isRecordatorioEnviado());
        verify(citaRepository).save(cita);
    }

    @Test
    void enviarRecordatorioUrgente_gatewayFalla_noMarcaComoEnviado() {
        Cita cita = Cita.builder()
                .id(20L)
                .pacienteNombre("Juan Perez")
                .pacienteContacto("+573009876543")
                .fechaHoraCita(LocalDateTime.of(2026, 8, 10, 10, 0))
                .canalPreferido(CanalNotificacion.SMS)
                .recordatorioEnviado(false)
                .build();

        when(citaRepository.findById(20L)).thenReturn(Optional.of(cita));
        doThrow(new RuntimeException("Fallo en proveedor SMS")).when(canalUrgenteMock).enviar(any(Cita.class), anyString());

        assertThrows(RuntimeException.class, () -> citaReminderService.enviarRecordatorioUrgente(20L));

        assertFalse(cita.isRecordatorioEnviado());
        verify(citaRepository, never()).save(any());
    }

    @Test
    void enviarRecordatorioUrgente_citaInexistente_lanzaCitaNoEncontradaException() {
        when(citaRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(CitaNoEncontradaException.class, () -> citaReminderService.enviarRecordatorioUrgente(99L));

        verifyNoInteractions(canalUrgenteMock);
        verify(citaRepository, never()).save(any());
    }
}

