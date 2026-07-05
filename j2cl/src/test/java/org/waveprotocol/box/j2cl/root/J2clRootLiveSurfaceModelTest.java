package org.waveprotocol.box.j2cl.root;

import com.google.j2cl.junit.apt.J2clTestInput;
import org.junit.Assert;
import org.junit.Test;
import org.waveprotocol.box.j2cl.search.J2clSidecarRouteState;

@J2clTestInput(J2clRootLiveSurfaceModelTest.class)
public class J2clRootLiveSurfaceModelTest {

  @Test
  public void startingDefaultsToOnlineAndSaved() {
    J2clRootLiveSurfaceModel model = J2clRootLiveSurfaceModel.starting();
    Assert.assertEquals("online", model.getConnectionState());
    Assert.assertEquals("saved", model.getSaveState());
  }

  @Test
  public void withConnectionStateAcceptsKnownValues() {
    J2clRootLiveSurfaceModel model = J2clRootLiveSurfaceModel.starting();
    Assert.assertEquals("offline", model.withConnectionState("offline").getConnectionState());
    Assert.assertEquals("connecting", model.withConnectionState("connecting").getConnectionState());
    Assert.assertEquals("online", model.withConnectionState("online").getConnectionState());
  }

  @Test
  public void withConnectionStateNormalizesUnknownToOnline() {
    J2clRootLiveSurfaceModel model =
        J2clRootLiveSurfaceModel.starting().withConnectionState("garbage");
    Assert.assertEquals("online", model.getConnectionState());
  }

  @Test
  public void withSaveStateAcceptsKnownValues() {
    J2clRootLiveSurfaceModel model = J2clRootLiveSurfaceModel.starting();
    Assert.assertEquals("saving", model.withSaveState("saving").getSaveState());
    Assert.assertEquals("unsaved", model.withSaveState("unsaved").getSaveState());
    Assert.assertEquals("saved", model.withSaveState("saved").getSaveState());
  }

  @Test
  public void withSaveStateNormalizesUnknownToSaved() {
    J2clRootLiveSurfaceModel model = J2clRootLiveSurfaceModel.starting().withSaveState("weird");
    Assert.assertEquals("saved", model.getSaveState());
  }

  @Test
  public void unchangedStateReturnsSameInstance() {
    J2clRootLiveSurfaceModel model = J2clRootLiveSurfaceModel.starting();
    Assert.assertSame(model, model.withConnectionState("online"));
    Assert.assertSame(model, model.withSaveState("saved"));
  }

  @Test
  public void connectionAndSaveStateSurviveRouteUrlChange() {
    J2clRootLiveSurfaceModel model =
        J2clRootLiveSurfaceModel.starting()
            .withConnectionState("offline")
            .withSaveState("saving")
            .withRouteUrl("?view=j2cl-root&q=in%3Ainbox");

    Assert.assertEquals("offline", model.getConnectionState());
    Assert.assertEquals("saving", model.getSaveState());
    Assert.assertEquals("in:inbox", model.getQuery());
  }

  @Test
  public void connectionAndSaveStateSurviveRouteStateChange() {
    J2clRootLiveSurfaceModel model =
        J2clRootLiveSurfaceModel.starting()
            .withConnectionState("connecting")
            .withSaveState("saving")
            .withRouteState(new J2clSidecarRouteState("in:inbox", "example.com/w+1"));

    Assert.assertEquals("connecting", model.getConnectionState());
    Assert.assertEquals("saving", model.getSaveState());
    Assert.assertEquals("example.com/w+1", model.getSelectedWaveId());
  }

  @Test
  public void connectionAndSaveStateSurviveSelectedWaveChange() {
    J2clRootLiveSurfaceModel model =
        J2clRootLiveSurfaceModel.starting()
            .withConnectionState("offline")
            .withSaveState("unsaved")
            .withSelectedWaveId("example.com/w+2");

    Assert.assertEquals("offline", model.getConnectionState());
    Assert.assertEquals("unsaved", model.getSaveState());
    Assert.assertEquals("example.com/w+2", model.getSelectedWaveId());
  }

  @Test
  public void emptyRouteUrlResetsStatusButPreservesRealState() {
    J2clRootLiveSurfaceModel model =
        J2clRootLiveSurfaceModel.starting()
            .withConnectionState("offline")
            .withSaveState("saving")
            .withRouteUrl("");

    Assert.assertEquals("Loading workspace.", model.getStatusText());
    Assert.assertEquals("offline", model.getConnectionState());
    Assert.assertEquals("saving", model.getSaveState());
  }
}
