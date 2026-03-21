package org.waveprotocol.box.server.authentication.jwt;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public record JwtClaims(
    JwtTokenType tokenType,
    String issuer,
    String subject,
    String tokenId,
    String keyId,
    Set<JwtAudience> audiences,
    Set<String> scopes,
    long issuedAtEpochSeconds,
    long notBeforeEpochSeconds,
    long expiresAtEpochSeconds,
    long subjectVersion) {

  public JwtClaims {
    tokenType = Objects.requireNonNull(tokenType, "tokenType");
    issuer = requireText(issuer, "issuer");
    subject = requireText(subject, "subject");
    tokenId = requireText(tokenId, "tokenId");
    keyId = requireText(keyId, "keyId");
    audiences = Set.copyOf(Objects.requireNonNull(audiences, "audiences"));
    scopes = normalizeScopes(scopes);
    if (audiences.isEmpty()) {
      throw new IllegalArgumentException("audiences must not be empty");
    }
    validateEntries(audiences, "audience");
    if (issuedAtEpochSeconds < 0) {
      throw new IllegalArgumentException("issuedAtEpochSeconds must be non-negative");
    }
    if (notBeforeEpochSeconds < 0) {
      throw new IllegalArgumentException("notBeforeEpochSeconds must be non-negative");
    }
    if (expiresAtEpochSeconds < 0) {
      throw new IllegalArgumentException("expiresAtEpochSeconds must be non-negative");
    }
    if (notBeforeEpochSeconds < issuedAtEpochSeconds) {
      throw new IllegalArgumentException("notBeforeEpochSeconds must be at or after issuedAtEpochSeconds");
    }
    if (expiresAtEpochSeconds < notBeforeEpochSeconds) {
      throw new IllegalArgumentException("expiresAtEpochSeconds must be at or after notBeforeEpochSeconds");
    }
    if (subjectVersion < 0) {
      throw new IllegalArgumentException("subjectVersion must be non-negative");
    }
  }

  public boolean isActiveAt(long epochSeconds) {
    return epochSeconds >= notBeforeEpochSeconds && epochSeconds < expiresAtEpochSeconds;
  }

  public boolean isExpiredAt(long epochSeconds) {
    return epochSeconds >= expiresAtEpochSeconds;
  }

  public boolean hasAudience(JwtAudience audience) {
    return audiences.contains(Objects.requireNonNull(audience, "audience"));
  }

  public boolean hasScope(String scope) {
    return scopes.contains(requireText(scope, "scope"));
  }

  private static String requireText(String value, String name) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException(name + " must be provided");
    }
    return value.trim();
  }

  private static <T> void validateEntries(Set<T> values, String name) {
    for (T value : values) {
      if (value == null) {
        throw new IllegalArgumentException(name + " entries must not be null");
      }
      if (value instanceof String && ((String) value).trim().isEmpty()) {
        throw new IllegalArgumentException(name + " entries must not be blank");
      }
    }
  }

  private static Set<String> normalizeScopes(Set<String> values) {
    Objects.requireNonNull(values, "scopes");
    return values.stream()
        .map(scope -> requireText(scope, "scope"))
        .collect(Collectors.toUnmodifiableSet());
  }
}
