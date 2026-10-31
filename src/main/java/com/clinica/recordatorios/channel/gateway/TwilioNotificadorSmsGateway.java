package com.clinica.recordatorios.channel.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class TwilioNotificadorSmsGateway implements NotificadorSmsGateway {

    private static final Logger log = LoggerFactory.getLogger(TwilioNotificadorSmsGateway.class);

    @Override
    public void enviarSms(String numeroDestino, String texto) {
        log.info(String.format("[SMS a %s] %s", numeroDestino, texto));
    }
}

