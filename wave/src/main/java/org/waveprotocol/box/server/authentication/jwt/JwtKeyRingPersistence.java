package org.waveprotocol.box.server.authentication.jwt;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Properties;

public final class JwtKeyRingPersistence {
  private static final Base64.Encoder BASE64_ENCODER = Base64.getEncoder();
  private static final Base64.Decoder BASE64_DECODER = Base64.getDecoder();
  private static final String KEYS_PROPERTY = "keys";
  private static final String SIGNING_KEY_PROPERTY = "signingKeyId";

  private JwtKeyRingPersistence() {
  }

  public static JwtKeyRing loadOrCreate(Path path, String defaultKeyId) {
    Objects.requireNonNull(path, "path");
    String keyId = requireText(defaultKeyId, "defaultKeyId");
    if (Files.exists(path)) {
      return load(path);
    }
    JwtKeyRing keyRing = JwtKeyRing.generate(keyId);
    save(path, keyRing);
    return keyRing;
  }

  public static JwtKeyRing load(Path path) {
    Objects.requireNonNull(path, "path");
    try (InputStream input = Files.newInputStream(path)) {
      Properties properties = new Properties();
      properties.load(input);
      String signingKeyId = requireText(properties.getProperty(SIGNING_KEY_PROPERTY), SIGNING_KEY_PROPERTY);
      String[] keyIds = requireText(properties.getProperty(KEYS_PROPERTY), KEYS_PROPERTY).split(",");
      List<JwtKeyMaterial> keyMaterials = new ArrayList<>();
      for (String rawKeyId : keyIds) {
        String keyId = requireText(rawKeyId, "keyId");
        keyMaterials.add(new JwtKeyMaterial(keyId, new KeyPair(loadPublicKey(properties, keyId), loadPrivateKey(properties, keyId))));
      }
      return new JwtKeyRing(keyMaterials, signingKeyId);
    } catch (Exception e) {
      throw new IllegalStateException("Unable to load JWT key ring from " + path, e);
    }
  }

  public static void save(Path path, JwtKeyRing keyRing) {
    Objects.requireNonNull(path, "path");
    Objects.requireNonNull(keyRing, "keyRing");
    try {
      Path parent = path.getParent();
      if (parent != null) {
        Files.createDirectories(parent);
      }
      Properties properties = new Properties();
      properties.setProperty(SIGNING_KEY_PROPERTY, keyRing.signingKeyId());
      Collection<JwtKeyMaterial> materials = keyRing.keyMaterials();
      properties.setProperty(KEYS_PROPERTY, joinKeyIds(materials));
      for (JwtKeyMaterial material : materials) {
        properties.setProperty(publicKeyProperty(material.keyId()), BASE64_ENCODER.encodeToString(material.publicKey().getEncoded()));
        properties.setProperty(privateKeyProperty(material.keyId()), BASE64_ENCODER.encodeToString(material.privateKey().getEncoded()));
      }
      try (OutputStream output = Files.newOutputStream(path)) {
        properties.store(output, "Wave JWT signing keys");
      }
    } catch (IOException e) {
      throw new IllegalStateException("Unable to persist JWT key ring to " + path, e);
    }
  }

  private static String joinKeyIds(Collection<JwtKeyMaterial> materials) {
    StringBuilder builder = new StringBuilder();
    for (JwtKeyMaterial material : materials) {
      if (builder.length() > 0) {
        builder.append(",");
      }
      builder.append(material.keyId());
    }
    return builder.toString();
  }

  private static PublicKey loadPublicKey(Properties properties, String keyId) throws Exception {
    byte[] encoded = BASE64_DECODER.decode(requireText(properties.getProperty(publicKeyProperty(keyId)), publicKeyProperty(keyId)));
    return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(encoded));
  }

  private static PrivateKey loadPrivateKey(Properties properties, String keyId) throws Exception {
    byte[] encoded = BASE64_DECODER.decode(requireText(properties.getProperty(privateKeyProperty(keyId)), privateKeyProperty(keyId)));
    return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(encoded));
  }

  private static String publicKeyProperty(String keyId) {
    return "key." + keyId + ".public";
  }

  private static String privateKeyProperty(String keyId) {
    return "key." + keyId + ".private";
  }

  private static String requireText(String value, String name) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException(name + " must be provided");
    }
    return value.trim();
  }
}
