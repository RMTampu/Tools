package com.toolbox.tools.delivery;

import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Base64;

public final class RemoteTrustAnchor {
    public static final String CERTIFICATE_SHA256 =
            "290fb37d527935766e327781833493400dd647cfc8bdbe433254a2df52e4b8e4";
    private static final String CERTIFICATE_DER_BASE64 =
            "MIIFeDCCA2CgAwIBAgIJAOnhNxQVLbY+MA0GCSqGSIb3DQEBCwUAMGkxCzAJBgNVBAYTAklEMRYwFAYDVQQIEw1TdW1hdHJhIFV0YXJhMQ4wDAYDVQQHEwVNZWRhbjEPMA0GA1UEChMGRHJlYW1zMRAwDgYDVQQLEwdBbmRyb2lkMQ8wDQYDVQQDEwZEcmVhbXMwIBcNMjYwODMxMDAwMzE3WhgPMjEyNjA4MDcwMDAzMTdaMGkxCzAJBgNVBAYTAklEMRYwFAYDVQQIEw1TdW1hdHJhIFV0YXJhMQ4wDAYDVQQHEwVNZWRhbjEPMA0GA1UEChMGRHJlYW1zMRAwDgYDVQQLEwdBbmRyb2lkMQ8wDQYDVQQDEwZEcmVhbXMwggIiMA0GCSqGSIb3DQEBAQUAA4ICDwAwggIKAoICAQC8XPZVyxnzhUjGPw0sV9SqmXFqe1zq5HdFFk5Ts3d+lH4fahnjuwSJzF0eQfu2PgnKJra5eg5BMWr9uSmqqH2AVHUJY7ljtb7uT2fdO3AA3JsCsWRBu9b18CCke+WZfk0ntle3yPqwOONZOViWKzFBTiBJheGNPm4Clem2zd2GqiWYgM1XmYXdJGwX6e0e3pIHgOGycd36kTghTukvxx7ORyTCe9Yrkx3HFq0vlSiwxjzzJtCy0NFwp9WAu8JZ7bZ+1LCHwRiMN5lWJDIMiymR38zsEfmxmZkXQITB33Iqb5IrrGtMHJ7eJcRHxFhKGMiHdZM4jD84LHqAXSolj9pD9I18C7xlrSwOmifxqsZMHBHYs1eGBBpR3ZFFH4eHomK2uAopY1cNJHVIrl28gVnChneaukny68QyHdBpLtjrZHSQqBSibZXoyxCEKSWkx9scYw8QYq/0P4lAll0waaQUhsb/c0PTtjSATwEWgB96i4ujuCyYgoHtkpgSkIflL0NGsLlMGM4TAIY04gFHTAYQnU1TpbgxrpoYDtvlIodv4a28nCHl1sRkSrGar/Q2H7t2aSTfzS8p5GhidvFSXAb2GRH2d5v1OJTyOpCKVAkYEsRHhQZOlI9nJ2/2YBPRZZ9QeTl/5iDQatUTIL+8CbjEEkiwtL11WhY06MjfNAfUKQIDAQABoyEwHzAdBgNVHQ4EFgQUufc24BuCBSRO8TqejnEHwfM3cGAwDQYJKoZIhvcNAQELBQADggIBAC8F7iS2he5B+QFmEVPo93tQq3di9d5Y8upsBTGUrrWBg1ZpGo+rceDw2CmHu0hM/avlw/JnQ1UXHX8mfszcVoWmY5xqsiYW5fcA5zYDoUq4lqlDOsp8JF0ldM4FjhfGf08Xnyff2amKlBMO8CPOicUIHyYV6isiDvHgtN5dNL1Nz8lwNM5s8EvCObqqceGARgDQynrzqD8t7k643vPimDwjKK8i51T84QWOToewzpjgi2kGc78lPnQJEHx0yf89IxPmwMOWtcO3w9OZG80FKsBZ2OBvDB7WniMWfj42HfZIO8VRQb6KpoQYI1z+xce1nURScwEYRJ+u7SKdNKTzSy+L+9d9ztwTHABfraOoQ5yP09c37QRuH8V8ExyXnVU3BU8J2QNHHmOON2fG6nsD/3J6fUgI5wDI75qrSkHpOAX8VnC6bu/U+q6CjRC4l7zuWLl6D5FYQtNjOx94ZEdMhU0OokdUUBCd4lYFvkpfUvbt3+cWcjMbb2Assm/mrOv1dZC9PRdfh+5l5CVF4e6MIbqFtsLaKwPZBHgLxDvLsSRbkJ58PKLsYefVE5YNpa47C2zAgkyNwhTuK/7diSi4of47+/CQ0k3jqoCNnFBjWx6nSeb2UxcqVNRLdcreTK0aVzNzdqYUvTxFNtbgdAaNhPbCQQW31zMvlO7FbF4rR786";

    private RemoteTrustAnchor(){}

    public static RemotePatchVerifier createVerifier(){
        try{
            byte[] der=Base64.getDecoder().decode(CERTIFICATE_DER_BASE64);
            if(!CERTIFICATE_SHA256.equals(sha256(der))) {
                throw new IllegalStateException("release certificate digest mismatch");
            }
            X509Certificate cert=(X509Certificate) CertificateFactory
                    .getInstance("X.509")
                    .generateCertificate(new ByteArrayInputStream(der));
            return new RemotePatchVerifier(
                    cert.getPublicKey(),
                    CERTIFICATE_SHA256
            );
        }catch(Exception error){
            throw new IllegalStateException("remote trust anchor invalid",error);
        }
    }

    private static String sha256(byte[] value)throws Exception{
        byte[] digest=MessageDigest.getInstance("SHA-256").digest(value);
        StringBuilder out=new StringBuilder();
        for(byte item:digest) {
            out.append(String.format(java.util.Locale.ROOT,"%02x",item));
        }
        return out.toString();
    }
}
