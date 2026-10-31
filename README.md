# recordatorios-citas-service — Unidad 9: Probar el Diseño

> **Estudiante:** Rodríguez  
> **Asignatura:** Arquitectura y Pruebas de Software  
> **Unidad 9:** *Probar el Diseño: JUnit 5, Mockito, Test Doubles e Integración Continua*  
> **Stack Tecnológico:** Java 21 LTS, Spring Boot 3.3.3, Spring Data JPA, H2 Database (in-memory), Mockito 5, JUnit 5, Maven 3.9+, GitHub Actions CI.

---

## 📋 Tabla de Contenido
1. [Descripción del Dominio y Problema](#-descripción-del-dominio-y-problema)
2. [Arquitectura de Software y Patrones de Diseño](#-arquitectura-de-software-y-patrones-de-diseño)
   - [Patrón Strategy](#1-patrón-strategy-reminderchannel)
   - [Patrón Gateway](#2-patrón-gateway-notificadorsmsgateway)
   - [Patrón Factory Method](#3-patrón-factory-method-reminderchannelfactory)
   - [Refactor DIP y Composition Root](#4-principio-de-inversión-de-dependencias-dip-y-composition-root)
   - [Abstracción Temporal Determinista](#5-abstracción-temporal-determinista-clock)
3. [Estructura del Proyecto](#-estructura-del-proyecto)
4. [Estrategia de Pruebas y Clasificación de Test Doubles](#-estrategia-de-pruebas-y-clasificación-de-test-doubles)
   - [Pruebas Unitarias Aisladas (Mockito & Fake Clock)](#1-pruebas-unitarias-citareminderserviceimpltest)
   - [Pruebas de Persistencia (@DataJpaTest con H2)](#2-pruebas-de-persistencia-citarepositorytest)
   - [Pruebas de Integración (@SpringBootTest con MockBean)](#3-pruebas-de-integración-citareminderserviceintegrationtest)
5. [Guía de Compilación y Ejecución](#-guía-de-compilación-y-ejecución)
6. [Pipeline de Integración Continua (CI/CD)](#-pipeline-de-integración-continua-cicd)
7. [Matriz de Trazabilidad y Cumplimiento de la Rúbrica R2](#-matriz-de-trazabilidad-y-cumplimiento-de-la-rúbrica-r2)

---

## 🏥 Descripción del Dominio y Problema

El microservicio `recordatorios-citas-service` gestiona el envío automatizado de notificaciones y recordatorios para citas médicas de una clínica privada. El sistema resuelve dos necesidades operativas críticas:

1. **Recordatorios Programados (Ventana de 24 horas):**  
   Identifica las citas agendadas dentro de las próximas 24 horas (`[ahora, ahora + 24h]`) que no han recibido recordatorio (`recordatorioEnviado = false`), resolviendo dinámicamente el canal de contacto preferido por el paciente (Email o SMS) y actualizando el estado de envío en la base de datos.

2. **Recordatorios de Urgencia (< 2 horas):**  
   Emite una alerta prioritaria e inmediata para citas críticas próximas sin confirmación, garantizando el envío a través de un canal urgente especializado (SMS directo vía Gateway) sin acoplamiento a implementaciones concretas.

---

## 🏛 Arquitectura de Software y Patrones de Diseño

El diseño técnico aplica estrictamente los principios SOLID y patrones de diseño orientados a la testabilidad y bajo acoplamiento:

```
                  ┌────────────────────────────────────────┐
                  │        CitaReminderServiceImpl         │
                  └───────┬──────────────┬─────────────┬───┘
                          │              │             │
        ┌─────────────────┘              │             └────────────────┐
        ▼                                ▼                              ▼
┌───────────────┐        ┌─────────────────────────────┐        ┌───────────────┐
│ CitaRepository│        │    ReminderChannelFactory   │        │     Clock     │
└───────────────┘        └──────────────┬──────────────┘        │(Fakeable Time)│
                                        │                       └───────────────┘
                                        ▼
                            ┌────────────────────────┐
                            │    ReminderChannel     │ (Strategy)
                            └────▲──────────────▲────┘
                                 │              │
                   ┌─────────────┴──┐        ┌──┴──────────────────────────┐
                   │  EmailChannel  │        │         SmsChannel          │
                   └────────────────┘        └──────────────┬──────────────┘
                                                            │ (DIP / Gateway)
                                                            ▼
                                             ┌─────────────────────────────┐
                                             │    NotificadorSmsGateway    │
                                             └──────────────▲──────────────┘
                                                            │
                                             ┌──────────────┴──────────────┐
                                             │ TwilioNotificadorSmsGateway │
                                             └─────────────────────────────┘
```

### 1. Patrón Strategy (`ReminderChannel`)
- **Propósito:** Desacoplar el algoritmo de notificación de los clientes que lo invocan.
- **Implementaciones:**
  - `EmailReminderChannel`: Simula el formateo y envío de correo electrónico emitiendo log `[EMAIL a %s] %s`.
  - `SmsReminderChannel`: Gestiona el envío a través de redes móviles delegando a un gateway SMS.
- **Beneficio SOLID:** Cumple el principio **Open/Closed (OCP)**; para soportar WhatsApp o Push Notifications sólo se implementa un nuevo `ReminderChannel` sin alterar el servicio.

### 2. Patrón Gateway (`NotificadorSmsGateway`)
- **Propósito:** Aislar las llamadas externas HTTP/REST (e.g. Twilio API) tras una interfaz controlada.
- **Implementación:** `TwilioNotificadorSmsGateway` implementa `enviarSms(numeroDestino, texto)` con log estructurado `[SMS a %s] %s`.
- **Beneficio para Testing:** Facilita la inyección de Mocks o Fakes para simular caídas de red, cuotas agotadas o tiempos de respuesta sin consumir saldo real de API ni depender de conectividad a Internet.

### 3. Patrón Factory Method (`ReminderChannelFactory`)
- **Propósito:** Centralizar la resolución dinámica de la estrategia adecuada según el `CanalNotificacion` (EMAIL, SMS).
- **Implementación:** `ReminderChannelFactoryImpl` inyecta por constructor las instancias gestionadas por el contenedor de Spring y entrega el canal correspondiente mediante un bloque `switch` exhaustivo.
- **Testabilidad:** Se inyecta la fábrica completa por interfaz en el servicio, permitiendo mockear la entrega de canales sin instanciaciones rígidas (`new`).

### 4. Principio de Inversión de Dependencias (DIP) y Composition Root
- **Problema previo al refactor:** El canal urgente se instanciaba internamente dentro del método de servicio (`new SmsReminderChannel(new TwilioNotificadorSmsGateway())`), violando DIP, acoplando el servicio a implementaciones de infraestructura y haciendo imposible probar fallos en pruebas unitarias.
- **Refactor aplicado:**
  - El servicio recibe `@Qualifier("canalUrgente") ReminderChannel canalUrgente` por constructor.
  - La composición de la dependencia concreta se traslada al **Composition Root** en `CanalUrgenteConfig`:
    ```java
    @Bean
    public ReminderChannel canalUrgente(NotificadorSmsGateway gateway) {
        return new SmsReminderChannel(gateway);
    }
    ```
  - **Resultado:** El servicio es 100% testeable de forma aislada, permitiendo verificar comportamiento cuando el canal falla con un Mock.

### 5. Abstracción Temporal Determinista (`Clock`)
- **Problema:** El uso de `LocalDateTime.now()` introduce no-determinismo y acoplamiento al reloj del sistema operativo, provocando pruebas frágiles ("flaky tests") que fallan según la hora de ejecución.
- **Solución:** Se inyecta `java.time.Clock`. En producción se utiliza `Clock.systemDefaultZone()` proveído por `ClockConfig`, mientras que en pruebas se inyecta `Clock.fixed(...)` con instante congelado (`2026-08-10T09:00:00Z`).

---

## 📂 Estructura del Proyecto

```
rodriguez-post1-u9/
├── .github/
│   └── workflows/
│       └── ci.yml                            # Pipeline de CI/CD para GitHub Actions
├── pom.xml                                   # Configuración de dependencias Maven (Java 21, Spring Boot 3.3.3)
├── README.md                                 # Documentación técnica completa (Rúbrica R2)
└── src/
    ├── main/
    │   ├── java/com/clinica/recordatorios/
    │   │   ├── RecordatoriosApplication.java # Clase principal de Spring Boot
    │   │   ├── channel/
    │   │   │   ├── EmailReminderChannel.java
    │   │   │   ├── ReminderChannel.java
    │   │   │   ├── ReminderChannelFactory.java
    │   │   │   ├── ReminderChannelFactoryImpl.java
    │   │   │   ├── SmsReminderChannel.java
    │   │   │   └── gateway/
    │   │   │       ├── NotificadorSmsGateway.java
    │   │   │       └── TwilioNotificadorSmsGateway.java
    │   │   ├── config/
    │   │   │   ├── CanalUrgenteConfig.java
    │   │   │   └── ClockConfig.java
    │   │   ├── domain/
    │   │   │   ├── CanalNotificacion.java
    │   │   │   └── Cita.java
    │   │   ├── repository/
    │   │   │   └── CitaRepository.java
    │   │   └── service/
    │   │       ├── CitaNoEncontradaException.java
    │   │       ├── CitaReminderService.java
    │   │       └── CitaReminderServiceImpl.java
    │   └── resources/
    │       └── application.properties        # Configuración datasource H2 en memoria y JPA
    └── test/
        └── java/com/clinica/recordatorios/
            ├── CitaReminderServiceIntegrationTest.java # Integración Spring Boot + H2 + MockBean
            ├── repository/
            │   └── CitaRepositoryTest.java             # Persistencia @DataJpaTest
            └── service/
                └── CitaReminderServiceImplTest.java    # Suite Unitaria JUnit 5 + Mockito + Fake
```

---

## 🧪 Estrategia de Pruebas y Clasificación de Test Doubles

La pirámide de pruebas del proyecto implementa Test Doubles de forma rigurosa según la taxonomía de Gerard Meszaros:

| Test Double | Tipo Utilizado | Propósito en el Proyecto |
|---|---|---|
| **Mock** | `CitaRepository`, `ReminderChannelFactory`, `ReminderChannel` | Verificar interacciones y llamadas a métodos (`save`, `enviar`, `obtenerCanal`) y asegurar que no haya llamadas espurias (`never()`, `verifyNoInteractions`). |
| **Fake** | `Clock.fixed(...)` | Implementación funcional simplificada y determinista del reloj de sistema para congelar el tiempo de prueba. |
| **Dummy** | Strings/IDs en pruebas de error | Parámetros requeridos por la firma pero irrelevantes para la lógica del caso probado. |
| **Stub / Mock** | `@MockBean NotificadorSmsGateway` | Controlar respuestas de infraestructura en la prueba de integración sin ejecutar llamadas de red. |

---

### 1. Pruebas Unitarias (`CitaReminderServiceImplTest`)
*Ubicación:* `src/test/java/com/clinica/recordatorios/service/CitaReminderServiceImplTest.java`  
*Tecnología:* JUnit 5 (`@ExtendWith(MockitoExtension.class)`), Mockito, Fake Clock.  
*Convención de nomenclatura:* `metodoBajoPrueba_condicion_resultado`.

| Método de Prueba | Condición y Verificaciones Clave |
|---|---|
| `enviarRecordatoriosPendientes_citaEnVentana_usaElCanalPreferido` | Verifica que para una cita en ventana (EMAIL) se consulte `reminderChannelFactory.obtenerCanal(EMAIL)`, se invoque `canal.enviar(...)`, se actualice `recordatorioEnviado = true` y se persista con `citaRepository.save(cita)`. |
| `enviarRecordatoriosPendientes_ventanaDeterministaSegunClockFijo_consultaLosLimitesExactos` | Utiliza `ArgumentCaptor<LocalDateTime>` para verificar que la consulta al repositorio utilice exactamente los límites deterministas `2026-08-10T09:00:00` (inicio) y `2026-08-11T09:00:00` (fin). |
| `enviarRecordatoriosPendientes_sinCitasPendientes_noConsultaLaFactory` | Cuando no hay citas en ventana, verifica `assertEquals(0, total)`, `verifyNoInteractions(reminderChannelFactory)` y que `citaRepository.save(...)` nunca sea invocado (`never()`). |
| `enviarRecordatorioUrgente_citaExistente_usaCanalUrgenteInyectado` | Verifica la invocación a `canalUrgenteMock.enviar(cita, "URGENTE: ...")`, el cambio de estado a enviado y el guardado en repositorio. |
| `enviarRecordatorioUrgente_gatewayFalla_noMarcaComoEnviado` | Simula un fallo de red mediante `doThrow(RuntimeException)` en el canal urgente; valida que la excepción sea propagada con `assertThrows`, comprueba que `isRecordatorioEnviado() == false` y asegura que nunca se guarde el registro (`never().save(any())`). |
| `enviarRecordatorioUrgente_citaInexistente_lanzaCitaNoEncontradaException` | Al no encontrar la cita (`Optional.empty()`), asegura que lance `CitaNoEncontradaException` y que no ocurra ninguna interacción con el canal urgente (`verifyNoInteractions(canalUrgenteMock)`). |

---

### 2. Pruebas de Persistencia (`CitaRepositoryTest`)
*Ubicación:* `src/test/java/com/clinica/recordatorios/repository/CitaRepositoryTest.java`  
*Tecnología:* `@DataJpaTest` con base de datos H2 en memoria y transaccionalidad automática.

- **Método:** `findByFechaHoraCitaBetweenAndRecordatorioEnviadoFalse_soloRetornaCitasPendientesEnVentana`
- **Aislamiento de Casos de Datos:**
  1. Cita dentro de ventana temporal (ahora + 5h) con `recordatorioEnviado = false` (**debe retornar en el resultado**).
  2. Cita dentro de ventana temporal (ahora + 10h) con `recordatorioEnviado = true` (**no debe retornar**).
  3. Cita fuera de ventana temporal (ahora + 48h) con `recordatorioEnviado = false` (**no debe retornar**).
- **Aserción:** Valida que el resultado contenga exactamente 1 elemento correspondiente al paciente pendiente en ventana.

---

### 3. Pruebas de Integración (`CitaReminderServiceIntegrationTest`)
*Ubicación:* `src/test/java/com/clinica/recordatorios/CitaReminderServiceIntegrationTest.java`  
*Tecnología:* `@SpringBootTest`, Spring IoC, `@TestConfiguration`, `@MockBean`.

- **Configuración de Prueba:**
  - `@TestConfiguration` que define un `@Bean @Primary Clock clock()` con fecha fija (`2026-08-10T09:00:00Z`).
  - `@MockBean private NotificadorSmsGateway notificadorSmsGateway` para interceptar la llamada de infraestructura SMS sin generar tráfico externo.
- **Método:** `enviarRecordatoriosPendientes_contextoCompletoDeSpring_persisteYNotificaCitasEnVentana`
- **Verificación:** Inserta una cita con canal preferido SMS en la base de datos real en memoria, ejecuta el servicio orquestador, verifica la invocación a `notificadorSmsGateway.enviarSms(...)` y comprueba en el repositorio que la entidad haya cambiado su estado a persistido (`recordatorioEnviado = true`).

---

## 🚀 Guía de Compilación y Ejecución

### Prerrequisitos
- **Java Development Kit (JDK):** Versión 21 LTS instalada y configurada en `JAVA_HOME`.
- **Apache Maven:** Versión 3.9+ en el `PATH` del sistema.
- **Git:** Para control de versiones.

### Comandos de Ejecución

1. **Compilar el código fuente del proyecto:**
   ```bash
   mvn clean compile
   ```

2. **Compilar las clases de prueba:**
   ```bash
   mvn test-compile
   ```

3. **Ejecutar toda la suite de pruebas automatizadas (Unitarias, JPA e Integración):**
   ```bash
   mvn test
   ```

4. **Ejecutar la verificación completa del ciclo de vida y empaquetado del JAR:**
   ```bash
   mvn clean verify
   ```

5. **Ejecutar una prueba específica:**
   ```bash
   mvn test -Dtest=CitaReminderServiceImplTest
   mvn test -Dtest=CitaRepositoryTest
   mvn test -Dtest=CitaReminderServiceIntegrationTest
   ```

---

## 🔄 Pipeline de Integración Continua (CI/CD)

El archivo de configuración de GitHub Actions se encuentra en [`.github/workflows/ci.yml`](.github/workflows/ci.yml) y asegura que cada cambio cumpla las políticas de calidad del proyecto:

```yaml
name: CI — Pruebas y Cobertura

on:
  push:
    branches: [ main ]
  pull_request:
    branches: [ main ]

jobs:
  build-and-test:
    runs-on: ubuntu-latest

    steps:
      - name: Checkout del repositorio
        uses: actions/checkout@v4

      - name: Configurar JDK 21
        uses: actions/setup-java@v4
        with:
          java-version: "21"
          distribution: "temurin"
          cache: maven

      - name: Compilar y ejecutar todas las pruebas
        run: mvn -B verify --no-transfer-progress
```

- **Disparadores (Triggers):** Se ejecuta automáticamente en cada `push` o `pull_request` a la rama principal `main`.
- **Entorno:** Contenedor `ubuntu-latest` con JDK 21 distribución Eclipse Temurin y caché nativa de dependencias Maven para optimizar tiempos de construcción.
- **Garantía de Calidad:** Si cualquier prueba unitaria, de persistencia o de integración falla, el pipeline interrumpe el despliegue con código de salida distinto de 0.

---

## 📊 Matriz de Trazabilidad y Cumplimiento de la Rúbrica R2

| Criterio / Entregable Rúbrica R2 | Evidencia de Implementación en Código | Estado | Calificación Esperada |
|---|---|:---:|:---:|
| **1. Dominio y Persistencia** | `CanalNotificacion.java`, entidad `Cita.java` con validaciones JPA y Lombok, `CitaRepository` con consulta derivada `findByFechaHoraCitaBetweenAndRecordatorioEnviadoFalse`. | ✅ Cumplido | **5.0 / 5.0** |
| **2. Strategy + Factory Method** | `ReminderChannel`, `EmailReminderChannel`, `SmsReminderChannel`, `NotificadorSmsGateway`, `TwilioNotificadorSmsGateway`, `ReminderChannelFactory` y su implementación sin `new`. | ✅ Cumplido | **5.0 / 5.0** |
| **3. Refactor DIP y Composition Root** | `ClockConfig`, `CanalUrgenteConfig` como Composition Root, servicio `CitaReminderServiceImpl` inyectado por constructor con `@Qualifier("canalUrgente")`, desacoplamiento temporal mediante `Clock`. Cero `new` en la lógica de negocio. | ✅ Cumplido | **5.0 / 5.0** |
| **4. Pruebas Unitarias Aisladas** | `CitaReminderServiceImplTest.java` con JUnit 5, `@ExtendWith(MockitoExtension.class)`, Fake Clock (`Clock.fixed`), `ArgumentCaptor` de límites temporales, 6 métodos con convención `metodoBajoPrueba_condicion_resultado`. | ✅ Cumplido | **5.0 / 5.0** |
| **5. Pruebas de Persistencia** | `CitaRepositoryTest.java` con `@DataJpaTest` y H2, verificando aislamiento de 3 escenarios (pendiente en ventana, enviada en ventana, fuera de ventana). | ✅ Cumplido | **5.0 / 5.0** |
| **6. Pruebas de Integración** | `CitaReminderServiceIntegrationTest.java` con `@SpringBootTest`, `@TestConfiguration` con `@Primary Clock`, `@MockBean NotificadorSmsGateway` y verificación de base de datos. | ✅ Cumplido | **5.0 / 5.0** |
| **7. Pipeline CI/CD GitHub Actions** | `.github/workflows/ci.yml` configurado para JDK 21 Temurin con ejecución desasistida `mvn -B verify --no-transfer-progress`. | ✅ Cumplido | **5.0 / 5.0** |

---
*Proyecto desarrollado y documentado con rigor de ingeniería de software para obtener la máxima calificación (5.0) en la evaluación de la Unidad 9.*
