package co.fleetsec.vapp.security;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

/**
 * Validación anti-SSRF para URLs provistas por el usuario (V-03 remediado).
 *
 * <p>Rechaza esquemas no-HTTP(S) y destinos que resuelvan a rangos internos: loopback,
 * link-local (incluye el IMDS de AWS {@code 169.254.169.254}), privados (RFC 1918),
 * CGNAT (100.64/10), multicast y wildcard. Se validan <b>todas</b> las direcciones a las
 * que resuelve el host, no solo la primera.
 *
 * <p><b>Residual conocido (TOCTOU / DNS rebinding):</b> el {@link java.net.http.HttpClient}
 * vuelve a resolver el host al conectar, por lo que un DNS que cambie entre la validación y
 * la conexión podría evadir el control. Para la app de demo se documenta como residual; el
 * cierre completo exige conectar a la IP ya validada fijando el Host header, o una allowlist
 * de destinos. Ver ficha V-03.
 */
public final class SsrfGuard {

    private SsrfGuard() {
    }

    /** Se lanza cuando la URL apunta a un destino no permitido. */
    public static class BlockedTargetException extends RuntimeException {
        public BlockedTargetException(String message) {
            super(message);
        }
    }

    /**
     * Valida que {@code rawUrl} sea HTTP(S) y resuelva únicamente a direcciones públicas.
     *
     * @throws BlockedTargetException si el esquema no es http/https, el host no resuelve, o
     *                                alguna dirección cae en un rango interno/reservado.
     */
    public static void assertAllowed(String rawUrl) {
        URI uri;
        try {
            uri = URI.create(rawUrl);
        } catch (IllegalArgumentException e) {
            throw new BlockedTargetException("URL inválida");
        }

        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            throw new BlockedTargetException("esquema no permitido: " + scheme);
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new BlockedTargetException("host ausente o malformado");
        }

        InetAddress[] resolved;
        try {
            resolved = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            throw new BlockedTargetException("host no resoluble: " + host);
        }

        for (InetAddress addr : resolved) {
            if (isInternal(addr)) {
                throw new BlockedTargetException("destino interno bloqueado: " + addr.getHostAddress());
            }
        }
    }

    private static boolean isInternal(InetAddress a) {
        if (a.isLoopbackAddress() || a.isAnyLocalAddress() || a.isLinkLocalAddress()
                || a.isSiteLocalAddress() || a.isMulticastAddress()) {
            return true;
        }
        // Refuerzo explícito para rangos que las heurísticas de java.net no cubren siempre.
        byte[] b = a.getAddress();
        if (b.length == 4) {
            int o0 = b[0] & 0xff;
            int o1 = b[1] & 0xff;
            // IMDS / link-local 169.254/16 (redundante con isLinkLocalAddress, defensivo).
            if (o0 == 169 && o1 == 254) {
                return true;
            }
            // CGNAT 100.64.0.0/10.
            if (o0 == 100 && o1 >= 64 && o1 <= 127) {
                return true;
            }
        }
        return false;
    }
}
