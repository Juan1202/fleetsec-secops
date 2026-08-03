package co.fleetsec.vapp.logging;

import ch.qos.logback.classic.pattern.MessageConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

/**
 * Converter de logback que redacta PII del mensaje ya formateado (V-08 remediado).
 *
 * <p>Se registra en {@code logback-spring.xml} con la palabra de conversión {@code maskedMsg} y
 * reemplaza a {@code %msg} en los patrones: así la redacción es <b>transversal</b> — cubre
 * cualquier log, incluso PII registrada por descuido en un call site nuevo — en lugar de depender
 * de que cada línea recuerde sanitizar. Delega la lógica en {@link PiiMasker}.
 */
public class PiiMaskingConverter extends MessageConverter {

    @Override
    public String convert(ILoggingEvent event) {
        return PiiMasker.mask(super.convert(event));
    }
}
