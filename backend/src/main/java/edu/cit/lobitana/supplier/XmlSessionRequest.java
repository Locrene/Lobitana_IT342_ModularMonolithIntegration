package edu.cit.lobitana.supplier;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;

// FILL IN: root element and field names from https://legacysupply.onrender.com/docs
@XmlRootElement(name = "AuthRequest")
@XmlAccessorType(XmlAccessType.FIELD)
class XmlSessionRequest {

    @XmlElement(name = "ClientId")
    private String clientId;

    @XmlElement(name = "ApiKey")
    private String apiKey;

    XmlSessionRequest() {}

    XmlSessionRequest(String clientId, String apiKey) {
        this.clientId = clientId;
        this.apiKey = apiKey;
    }
}
