package com.oula.iam.dev;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

@Component
@Profile("dev-auth & !prod")
class DevJwtKeyMaterial {
    private final RSAPublicKey publicKey;
    private final RSAPrivateKey privateKey;

    DevJwtKeyMaterial() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair pair = generator.generateKeyPair();
            this.publicKey = (RSAPublicKey) pair.getPublic();
            this.privateKey = (RSAPrivateKey) pair.getPrivate();
        } catch (Exception ex) {
            throw new IllegalStateException("unable to initialize dev-auth signing key", ex);
        }
    }

    RSAPublicKey publicKey() {
        return publicKey;
    }

    RSAPrivateKey privateKey() {
        return privateKey;
    }
}
