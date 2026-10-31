package com.clinica.recordatorios.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "citas")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Cita {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String pacienteNombre;

    @Column(nullable = false)
    private String pacienteContacto;

    @Column(nullable = false)
    private LocalDateTime fechaHoraCita;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CanalNotificacion canalPreferido;

    @Column(nullable = false)
    private boolean recordatorioEnviado;

    public Cita() {
    }

    public Cita(Long id, String pacienteNombre, String pacienteContacto, LocalDateTime fechaHoraCita, CanalNotificacion canalPreferido, boolean recordatorioEnviado) {
        this.id = id;
        this.pacienteNombre = pacienteNombre;
        this.pacienteContacto = pacienteContacto;
        this.fechaHoraCita = fechaHoraCita;
        this.canalPreferido = canalPreferido;
        this.recordatorioEnviado = recordatorioEnviado;
    }

    public static CitaBuilder builder() {
        return new CitaBuilder();
    }

    public static class CitaBuilder {
        private Long id;
        private String pacienteNombre;
        private String pacienteContacto;
        private LocalDateTime fechaHoraCita;
        private CanalNotificacion canalPreferido;
        private boolean recordatorioEnviado;

        public CitaBuilder id(Long id) {
            this.id = id;
            return this;
        }

        public CitaBuilder pacienteNombre(String pacienteNombre) {
            this.pacienteNombre = pacienteNombre;
            return this;
        }

        public CitaBuilder pacienteContacto(String pacienteContacto) {
            this.pacienteContacto = pacienteContacto;
            return this;
        }

        public CitaBuilder fechaHoraCita(LocalDateTime fechaHoraCita) {
            this.fechaHoraCita = fechaHoraCita;
            return this;
        }

        public CitaBuilder canalPreferido(CanalNotificacion canalPreferido) {
            this.canalPreferido = canalPreferido;
            return this;
        }

        public CitaBuilder recordatorioEnviado(boolean recordatorioEnviado) {
            this.recordatorioEnviado = recordatorioEnviado;
            return this;
        }

        public Cita build() {
            return new Cita(id, pacienteNombre, pacienteContacto, fechaHoraCita, canalPreferido, recordatorioEnviado);
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getPacienteNombre() {
        return pacienteNombre;
    }

    public void setPacienteNombre(String pacienteNombre) {
        this.pacienteNombre = pacienteNombre;
    }

    public String getPacienteContacto() {
        return pacienteContacto;
    }

    public void setPacienteContacto(String pacienteContacto) {
        this.pacienteContacto = pacienteContacto;
    }

    public LocalDateTime getFechaHoraCita() {
        return fechaHoraCita;
    }

    public void setFechaHoraCita(LocalDateTime fechaHoraCita) {
        this.fechaHoraCita = fechaHoraCita;
    }

    public CanalNotificacion getCanalPreferido() {
        return canalPreferido;
    }

    public void setCanalPreferido(CanalNotificacion canalPreferido) {
        this.canalPreferido = canalPreferido;
    }

    public boolean isRecordatorioEnviado() {
        return recordatorioEnviado;
    }

    public void setRecordatorioEnviado(boolean recordatorioEnviado) {
        this.recordatorioEnviado = recordatorioEnviado;
    }
}
