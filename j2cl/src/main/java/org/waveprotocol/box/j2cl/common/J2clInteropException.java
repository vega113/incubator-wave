package org.waveprotocol.box.j2cl.common;

/**
 * #1272: minification-friendly runtime exception raised by
 * {@link J2clJsInteropUtils} when a required JS-interop / DOM invariant is
 * violated. The {@code message} carries the human {@code what}/context string so
 * a failure is diagnosable even after the J2CL optimiser has renamed everything.
 */
public final class J2clInteropException extends RuntimeException {

  public J2clInteropException(String message) {
    super(message);
  }
}
