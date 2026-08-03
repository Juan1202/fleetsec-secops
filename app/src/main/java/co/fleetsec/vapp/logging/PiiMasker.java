package co.fleetsec.vapp.logging;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Redacción de PII en cadenas de log (V-08 remediado / Ley 1581).
 *
 * <p>Enmascara correos electrónicos y secuencias numéricas largas (cédulas, teléfonos, ids de
 * documento) para que la PII no quede en texto plano en consola ni en el archivo de log. Es una
 * utilidad pura y determinista, reutilizada por {@link PiiMaskingConverter} para aplicarse de
 * forma transversal a todos los eventos de logging, independientemente del call site.
 */
public final class PiiMasker {

    private static final Pattern EMAIL =
            Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    // Secuencias de 7+ dígitos: cédulas (6-10), teléfonos, ids de documento.
    private static final Pattern LONG_DIGITS = Pattern.compile("\\d{7,}");

    private PiiMasker() {
    }

    /** Devuelve {@code input} con los correos y las secuencias numéricas largas redactados. */
    public static String mask(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        String out = EMAIL.matcher(input).replaceAll("***@***");
        Matcher m = LONG_DIGITS.matcher(out);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(sb, Matcher.quoteReplacement(maskDigits(m.group())));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String maskDigits(String digits) {
        int keep = 2;
        if (digits.length() <= keep) {
            return digits;
        }
        return "*".repeat(digits.length() - keep) + digits.substring(digits.length() - keep);
    }
}
