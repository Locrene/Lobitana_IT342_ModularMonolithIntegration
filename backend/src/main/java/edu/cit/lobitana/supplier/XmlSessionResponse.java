package edu.cit.lobitana.supplier;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;

// Confirmed against a live /auth/token response: <AuthResponse><SessionToken>...</SessionToken>
// <IssuedAt>...</IssuedAt></AuthResponse>. No expiry is sent, so the configured TTL is used.
@XmlRootElement(name = "AuthResponse")
@XmlAccessorType(XmlAccessType.FIELD)
class XmlSessionResponse {

    @XmlElement(name = "SessionToken")
    private String sessionToken;

    /** Optional. If LegacySupply does not send one, the configured TTL is used instead. */
    @XmlElement(name = "ExpiresInSeconds")
    private Long expiresInSeconds;

    String getSessionToken() { return sessionToken; }

    Long getExpiresInSeconds() { return expiresInSeconds; }
}
