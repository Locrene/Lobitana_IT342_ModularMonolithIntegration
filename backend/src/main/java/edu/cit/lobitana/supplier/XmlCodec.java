package edu.cit.lobitana.supplier;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.Unmarshaller;
import org.springframework.stereotype.Component;

import javax.xml.transform.stream.StreamSource;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** JAXB in one place. Package-private, so XML never leaks past the adapter. */
@Component
class XmlCodec {

    private final Map<Class<?>, JAXBContext> contexts = new ConcurrentHashMap<>();

    String marshal(Object value) {
        try {
            Marshaller marshaller = context(value.getClass()).createMarshaller();
            marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
            StringWriter writer = new StringWriter();
            marshaller.marshal(value, writer);
            return writer.toString();
        } catch (JAXBException e) {
            throw new LegacySupplyException("Could not build XML for " + value.getClass().getSimpleName(), false, e);
        }
    }

    /**
     * Unmarshals against the declared type rather than the root element name, so a
     * differently named wrapper element in the real responses still parses.
     */
    <T> T unmarshal(String xml, Class<T> type) {
        if (xml == null || xml.isBlank()) {
            throw new LegacySupplyException("Empty response body where " + type.getSimpleName() + " was expected", true);
        }
        try {
            Unmarshaller unmarshaller = context(type).createUnmarshaller();
            return unmarshaller.unmarshal(new StreamSource(new StringReader(xml)), type).getValue();
        } catch (JAXBException e) {
            throw new LegacySupplyException("Unreadable XML response: " + preview(xml), true, e);
        }
    }

    private JAXBContext context(Class<?> type) {
        return contexts.computeIfAbsent(type, t -> {
            try {
                return JAXBContext.newInstance(t);
            } catch (JAXBException e) {
                throw new LegacySupplyException("No JAXB context for " + t.getSimpleName(), false, e);
            }
        });
    }

    private String preview(String xml) {
        String flat = xml.replaceAll("\\s+", " ").trim();
        return flat.length() > 200 ? flat.substring(0, 200) + "..." : flat;
    }
}
