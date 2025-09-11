package org.waveprotocol.box.server;

import com.google.inject.AbstractModule;

/**
 * No-op StatModule for Jakarta path. Avoids pulling GWT server classes.
 */
public class StatModule extends AbstractModule {
  @Override
  protected void configure() {
    // No profiling interceptors on Jakarta until a Jakarta-friendly stats stack is wired.
  }
}

