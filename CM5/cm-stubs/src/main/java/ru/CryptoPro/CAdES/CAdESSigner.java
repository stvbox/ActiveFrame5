package ru.CryptoPro.CAdES;

import org.bouncycastle.tsp.TimeStampToken;

import java.security.cert.X509Certificate;
import java.util.Collection;

public class CAdESSigner {

    public void verify(Object o, Object o1, Object o2, boolean b) {
    }

    public X509Certificate getSignerCertificate() {
        return null;
    }

    public Object getSignatureType() {
        return null;
    }

    public TimeStampToken getCAdESCTimestampToken() {
        return null;
    }

    public TimeStampToken getSignatureTimestampToken() {
        return null;
    }

    public Collection<org.bouncycastle.tsp.TimeStampToken> getSignatureTimestampTokenList() {
        return null;
    }

    public Collection<org.bouncycastle.tsp.TimeStampToken> getCAdESCTimestampTokenList() {
        return null;
    }
}
