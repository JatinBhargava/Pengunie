package com.pengunie.auth;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.util.StringUtils;

/**
 * RS256 signing for self-issued access tokens. The resource-server side only needs the public key
 * and issuer, which is exactly what it would need for Keycloak/Cognito tokens (ADR 0002).
 */
@Configuration
class JwtConfig {

	private static final Logger log = LoggerFactory.getLogger(JwtConfig.class);

	static final String KEY_ID = "pios-1";

	@Bean
	RSAKey signingKey(AuthProperties props) throws Exception {
		if (!StringUtils.hasText(props.privateKey()) || !StringUtils.hasText(props.publicKey())) {
			log.warn("app.auth.private-key/public-key not set: generating an ephemeral RSA key. "
					+ "Tokens will not survive a restart. Run scripts/gen-dev-keys.sh for stable dev keys.");
			KeyPair pair = generate();
			return new RSAKey.Builder((RSAPublicKey) pair.getPublic()).privateKey(pair.getPrivate())
				.keyID(KEY_ID)
				.build();
		}
		KeyFactory factory = KeyFactory.getInstance("RSA");
		RSAPublicKey publicKey = (RSAPublicKey) factory
			.generatePublic(new X509EncodedKeySpec(pemBody(props.publicKey())));
		RSAPrivateKey privateKey = (RSAPrivateKey) factory
			.generatePrivate(new PKCS8EncodedKeySpec(pemBody(props.privateKey())));
		return new RSAKey.Builder(publicKey).privateKey(privateKey).keyID(KEY_ID).build();
	}

	@Bean
	JwtEncoder jwtEncoder(RSAKey signingKey) {
		return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(signingKey)));
	}

	@Bean
	JwtDecoder jwtDecoder(RSAKey signingKey, AuthProperties props) throws Exception {
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(signingKey.toRSAPublicKey()).build();
		decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(props.issuer()));
		return decoder;
	}

	private static KeyPair generate() throws NoSuchAlgorithmException {
		KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
		generator.initialize(2048);
		return generator.generateKeyPair();
	}

	private static byte[] pemBody(String pem) {
		String base64 = pem.replaceAll("-----(BEGIN|END) [A-Z ]+-----", "").replace("\\n", "").replaceAll("\\s", "");
		return Base64.getDecoder().decode(base64);
	}

}
