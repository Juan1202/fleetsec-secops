package co.fleetsec.vapp.service;

import java.io.StringReader;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

/**
 * Parser XML para importación de vehículos — <b>V-04 remediado</b>.
 *
 * <p><b>V04 · XXE (CWE-611).</b> {@link #parse(String)} configura la {@link DocumentBuilderFactory}
 * de forma segura: prohíbe la declaración DOCTYPE ({@code disallow-doctype-decl}), deshabilita
 * entidades externas generales y de parámetro, activa {@code FEATURE_SECURE_PROCESSING} y
 * desactiva XInclude y la expansión de referencias a entidades. Un documento con
 * {@code <!DOCTYPE ... SYSTEM "file:///etc/passwd">} es rechazado con excepción al parsear.
 */
@Service
public class XmlParserService {

    // URIs de features JAXP/Xerces para el endurecimiento anti-XXE.
    private static final String DISALLOW_DOCTYPE = "http://apache.org/xml/features/disallow-doctype-decl";
    private static final String EXTERNAL_GENERAL = "http://xml.org/sax/features/external-general-entities";
    private static final String EXTERNAL_PARAM = "http://xml.org/sax/features/external-parameter-entities";
    private static final String LOAD_EXTERNAL_DTD = "http://apache.org/xml/features/nonvalidating/load-external-dtd";

    /**
     * Parsea el XML de forma segura y devuelve el texto del elemento raíz.
     *
     * @throws Exception si el documento contiene un DOCTYPE (XXE) o es XML inválido.
     */
    public String parse(String xml) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        // ── V-04 remediado · endurecimiento anti-XXE ────────────────────────────
        // disallow-doctype-decl=true es el control primario: sin DOCTYPE no hay entidades.
        dbf.setFeature(DISALLOW_DOCTYPE, true);
        dbf.setFeature(EXTERNAL_GENERAL, false);
        dbf.setFeature(EXTERNAL_PARAM, false);
        dbf.setFeature(LOAD_EXTERNAL_DTD, false);
        dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        dbf.setXIncludeAware(false);
        dbf.setExpandEntityReferences(false);

        DocumentBuilder builder = dbf.newDocumentBuilder();
        Document doc = builder.parse(new InputSource(new StringReader(xml)));
        doc.getDocumentElement().normalize();

        return doc.getDocumentElement().getTextContent();
    }
}
