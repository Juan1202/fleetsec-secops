package co.fleetsec.vapp.service;

import java.io.StringReader;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

/**
 * Parser XML para importación de vehículos — <b>intencionalmente vulnerable</b>.
 *
 * <p><b>V04 · XXE (CWE-611).</b> {@link #parse(String)} usa {@link DocumentBuilderFactory} con
 * la configuración por defecto, que <b>no</b> deshabilita DTDs ni entidades externas. Un
 * documento con una entidad externa {@code SYSTEM "file:///etc/passwd"} se expande al parsear,
 * permitiendo exfiltrar archivos locales o hacer SSRF vía {@code http://}.
 *
 * <p>La remediación (Sprint 2) activará
 * {@code FEATURE_SECURE_PROCESSING} y {@code disallow-doctype-decl}.
 */
@Service
public class XmlParserService {

    /**
     * Parsea el XML y devuelve el texto del elemento raíz.
     *
     * <p>VULN V04: entidades externas habilitadas → su contenido queda embebido en el texto.
     */
    public String parse(String xml) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        // ── V04 · XXE ───────────────────────────────────────────────────────────
        // NO se establece dbf.setFeature("...disallow-doctype-decl", true) ni
        // dbf.setExpandEntityReferences(false): la factory queda insegura a propósito.
        DocumentBuilder builder = dbf.newDocumentBuilder();

        Document doc = builder.parse(new InputSource(new StringReader(xml)));
        doc.getDocumentElement().normalize();

        // El textContent incluye las entidades externas ya expandidas (exfiltración).
        return doc.getDocumentElement().getTextContent();
    }
}
