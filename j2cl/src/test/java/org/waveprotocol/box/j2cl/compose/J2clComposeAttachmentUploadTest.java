package org.waveprotocol.box.j2cl.compose;

import com.google.j2cl.junit.apt.J2clTestInput;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;
import org.waveprotocol.box.j2cl.attachment.J2clAttachmentComposerController;
import org.waveprotocol.box.j2cl.attachment.J2clAttachmentUploadClient;
import org.waveprotocol.box.j2cl.search.J2clSidecarWriteSession;
import org.waveprotocol.box.j2cl.testsupport.FakeAttachmentTransport;
import org.waveprotocol.box.j2cl.testsupport.FakeGateway;
import org.waveprotocol.box.j2cl.testsupport.FakeView;
import org.waveprotocol.box.j2cl.toolbar.J2clDailyToolbarAction;

/**
 * #1270: split from the monster J2clComposeSurfaceControllerTest. Covers
 * attachment upload+submit, controller-factory contract, reconnect and rich-annotation survival.
 */
@J2clTestInput(J2clComposeAttachmentUploadTest.class)
public class J2clComposeAttachmentUploadTest extends J2clComposeControllerTestSupport {

  @Test
  public void selectedAttachmentUploadsAndSubmitsStructuredReplyContent() {
    FakeGateway gateway = new FakeGateway();
    FakeView view = new FakeView();
    FakeAttachmentTransport transport = new FakeAttachmentTransport();
    List<String> refreshed = new ArrayList<String>();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            gateway,
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            testAttachmentControllerFactory(transport),
            waveId -> { },
            refreshed::add);
    Object payload = new Object();

    controller.start();
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 44L, "ABCD", "b+root"));
    controller.onToolbarAction(J2clDailyToolbarAction.ATTACHMENT_SIZE_LARGE);
    controller.onAttachmentFilesSelected(
        Arrays.asList(new J2clComposeSurfaceController.AttachmentFileSelection(payload, "diagram.png")));

    Assert.assertEquals(1, transport.requests.size());
    Assert.assertEquals("/attachment/example.com/seedA", transport.requests.get(0).getUrl());
    Assert.assertSame(payload, transport.requests.get(0).getPart(2).getPayload());
    Assert.assertEquals("Uploading diagram.png (0%).", view.model.getCommandStatusText());

    transport.requests.get(0).getProgressCallback().onProgress(37);

    Assert.assertEquals("Uploading diagram.png (37%).", view.model.getCommandStatusText());

    transport.complete(0, new J2clAttachmentUploadClient.HttpResponse(200, "OK", null));
    controller.onReplySubmitted("");

    Assert.assertEquals(Arrays.asList("example.com/w+1"), refreshed);
    assertContains(
        gateway.lastSubmitRequest.getDeltaJson(),
        "{\"1\":\"attachment\",\"2\":\"example.com/seedA\"}",
        "{\"1\":\"display-size\",\"2\":\"large\"}",
        "\"2\":\"diagram.png\"");
    Assert.assertEquals("", view.model.getActiveCommandId());
    Assert.assertEquals("", view.model.getCommandStatusText());
  }

  @Test
  public void attachmentIdsContinueAcrossSuccessfulReplyBatches() {
    FakeGateway gateway = new FakeGateway();
    FakeView view = new FakeView();
    FakeAttachmentTransport transport = new FakeAttachmentTransport();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            gateway,
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            testAttachmentControllerFactory(transport),
            waveId -> { },
            waveId -> { });

    controller.start();
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 44L, "ABCD", "b+root"));
    controller.onAttachmentFilesSelected(
        Arrays.asList(new J2clComposeSurfaceController.AttachmentFileSelection(new Object(), "first.png")));
    transport.complete(0, new J2clAttachmentUploadClient.HttpResponse(200, "OK", null));
    controller.onReplySubmitted("");

    Assert.assertEquals("/attachment/example.com/seedA", transport.requests.get(0).getUrl());
    assertContains(gateway.lastSubmitRequest.getDeltaJson(), "{\"1\":\"attachment\",\"2\":\"example.com/seedA\"}");

    controller.onAttachmentFilesSelected(
        Arrays.asList(new J2clComposeSurfaceController.AttachmentFileSelection(new Object(), "second.png")));
    transport.complete(1, new J2clAttachmentUploadClient.HttpResponse(200, "OK", null));
    controller.onReplySubmitted("");

    Assert.assertEquals("/attachment/example.com/seedB", transport.requests.get(1).getUrl());
    assertContains(gateway.lastSubmitRequest.getDeltaJson(), "{\"1\":\"attachment\",\"2\":\"example.com/seedB\"}");
  }

  @Test
  public void attachmentControllerFactoryKeepsIdsMonotonicAcrossWaveChanges() {
    FakeAttachmentTransport transport = new FakeAttachmentTransport();
    J2clComposeSurfaceController.AttachmentControllerFactory factory =
        J2clComposeSurfaceController.attachmentControllerFactory(
            "seed", new J2clAttachmentUploadClient(transport));
    J2clAttachmentComposerController firstController =
        factory.create(
            "example.com/w+1/~/conv+root",
            "example.com",
            (document, insertion) -> { },
            () -> { });
    J2clAttachmentComposerController secondController =
        factory.create(
            "example.com/w+2/~/conv+root",
            "example.com",
            (document, insertion) -> { },
            () -> { });
    J2clAttachmentComposerController otherDomainController =
        factory.create(
            "other.example/w+1/~/conv+root",
            "other.example",
            (document, insertion) -> { },
            () -> { });

    firstController.selectFiles(
        Arrays.asList(
            J2clAttachmentComposerController.AttachmentSelection.file(
                new Object(),
                "first.png",
                "",
                J2clAttachmentComposerController.DisplaySize.SMALL)));
    firstController.cancelAndReset();
    firstController.selectFiles(
        Arrays.asList(
            J2clAttachmentComposerController.AttachmentSelection.file(
                new Object(),
                "after-cancel.png",
                "",
                J2clAttachmentComposerController.DisplaySize.SMALL)));
    otherDomainController.selectFiles(
        Arrays.asList(
            J2clAttachmentComposerController.AttachmentSelection.file(
                new Object(),
                "other.png",
                "",
                J2clAttachmentComposerController.DisplaySize.SMALL)));
    // Only selections consume ids: cancel clears queue state without rewinding the shared counter.
    secondController.selectFiles(
        Arrays.asList(
            J2clAttachmentComposerController.AttachmentSelection.file(
                new Object(),
                "second.png",
                "",
                J2clAttachmentComposerController.DisplaySize.SMALL)));

    Assert.assertEquals("/attachment/example.com/seedA", transport.requests.get(0).getUrl());
    Assert.assertEquals("/attachment/example.com/seedB", transport.requests.get(1).getUrl());
    Assert.assertEquals("/attachment/other.example/seedA", transport.requests.get(2).getUrl());
    Assert.assertEquals("/attachment/example.com/seedC", transport.requests.get(3).getUrl());

    FakeAttachmentTransport secondTransport = new FakeAttachmentTransport();
    J2clComposeSurfaceController.AttachmentControllerFactory secondFactory =
        J2clComposeSurfaceController.attachmentControllerFactory(
            "seed", new J2clAttachmentUploadClient(secondTransport));
    J2clAttachmentComposerController secondFactoryController =
        secondFactory.create(
            "example.com/w+fresh/~/conv+root",
            "example.com",
            (document, insertion) -> { },
            () -> { });
    secondFactoryController.selectFiles(
        Arrays.asList(
            J2clAttachmentComposerController.AttachmentSelection.file(
                new Object(),
                "fresh.png",
                "",
                J2clAttachmentComposerController.DisplaySize.SMALL)));

    Assert.assertEquals(
        "/attachment/example.com/seedA", secondTransport.requests.get(0).getUrl());
  }

  @Test
  public void attachmentControllerFactoryUsesGeneratorDomainValidationContract() {
    FakeAttachmentTransport transport = new FakeAttachmentTransport();
    J2clComposeSurfaceController.AttachmentControllerFactory factory =
        J2clComposeSurfaceController.attachmentControllerFactory(
            "seed", new J2clAttachmentUploadClient(transport));
    J2clAttachmentComposerController controller =
        factory.create(
            "example.com/w+1/~/conv+root",
            "  example.com  ",
            (document, insertion) -> { },
            () -> { });

    controller.selectFiles(
        Arrays.asList(
            J2clAttachmentComposerController.AttachmentSelection.file(
                new Object(),
                "trimmed.png",
                "",
                J2clAttachmentComposerController.DisplaySize.SMALL)));

    Assert.assertEquals("/attachment/example.com/seedA", transport.requests.get(0).getUrl());
    J2clAttachmentComposerController normalizedController =
        factory.create(
            "example.com/w+2/~/conv+root",
            "example.com",
            (document, insertion) -> { },
            () -> { });
    normalizedController.selectFiles(
        Arrays.asList(
            J2clAttachmentComposerController.AttachmentSelection.file(
                new Object(),
                "normalized.png",
                "",
                J2clAttachmentComposerController.DisplaySize.SMALL)));

    Assert.assertEquals("/attachment/example.com/seedB", transport.requests.get(1).getUrl());

    assertFactoryRejectsDomain(factory, null);
    assertFactoryRejectsDomain(factory, "");
    assertFactoryRejectsDomain(factory, "   ");
    assertFactoryRejectsDomain(factory, "example.com/bad");

    J2clAttachmentComposerController freshController =
        factory.create(
            "fresh.example/w+1/~/conv+root",
            "fresh.example",
            (document, insertion) -> { },
            () -> { });
    freshController.selectFiles(
        Arrays.asList(
            J2clAttachmentComposerController.AttachmentSelection.file(
                new Object(),
                "fresh.png",
                "",
                J2clAttachmentComposerController.DisplaySize.SMALL)));

    Assert.assertEquals("/attachment/fresh.example/seedA", transport.requests.get(2).getUrl());
  }

  @Test
  public void attachmentControllerFactoryValidatesDomainBeforeCreatingUploadClient() {
    CountingUploadClientFactory clientFactory = new CountingUploadClientFactory();
    J2clComposeSurfaceController.AttachmentControllerFactory factory =
        J2clComposeSurfaceController.attachmentControllerFactory("seed", clientFactory);

    assertFactoryRejectsDomain(factory, null);
    assertFactoryRejectsDomain(factory, "");
    assertFactoryRejectsDomain(factory, "example.com/bad");

    Assert.assertEquals(0, clientFactory.createCalls);
  }

  @Test
  public void attachmentControllerFactoryCreatesUploadClientPerController() {
    CountingUploadClientFactory clientFactory = new CountingUploadClientFactory();
    J2clComposeSurfaceController.AttachmentControllerFactory factory =
        J2clComposeSurfaceController.attachmentControllerFactory("seed", clientFactory);

    factory.create(
        "example.com/w+1/~/conv+root",
        "example.com",
        (document, insertion) -> { },
        () -> { });
    factory.create(
        "example.com/w+2/~/conv+root",
        "example.com",
        (document, insertion) -> { },
        () -> { });

    Assert.assertEquals(2, clientFactory.createCalls);
  }

  @Test
  public void publicAttachmentControllerFactoryCreatesFreshUploadClients() throws Exception {
    J2clComposeSurfaceController.AttachmentControllerFactory factory =
        J2clComposeSurfaceController.attachmentControllerFactory("seed");

    J2clAttachmentComposerController first =
        factory.create(
            "example.com/w+1/~/conv+root",
            "example.com",
            (document, insertion) -> { },
            () -> { });
    J2clAttachmentComposerController second =
        factory.create(
            "example.com/w+2/~/conv+root",
            "example.com",
            (document, insertion) -> { },
            () -> { });

    Assert.assertNotSame(uploadClient(first), uploadClient(second));
  }

  @Test
  public void sameWaveReconnectPreservesInFlightAttachmentUpload() {
    FakeGateway gateway = new FakeGateway();
    FakeView view = new FakeView();
    FakeAttachmentTransport transport = new FakeAttachmentTransport();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            gateway,
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            testAttachmentControllerFactory(transport),
            waveId -> { },
            waveId -> { });

    controller.start();
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 44L, "ABCD", "b+root"));
    controller.onAttachmentFilesSelected(
        Arrays.asList(new J2clComposeSurfaceController.AttachmentFileSelection(new Object(), "late.png")));
    controller.onWriteSessionChanged(null);
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-2", 45L, "BCDE", "b+root"));

    transport.complete(0, new J2clAttachmentUploadClient.HttpResponse(200, "OK", null));
    controller.onReplySubmitted("");

    assertContains(
        gateway.lastSubmitRequest.getDeltaJson(),
        "{\"1\":\"attachment\",\"2\":\"example.com/seedA\"}",
        "\"2\":\"late.png\"");
  }

  @Test
  public void differentWaveReconnectAfterDisconnectDropsInFlightAttachmentUpload() {
    FakeGateway gateway = new FakeGateway();
    FakeView view = new FakeView();
    FakeAttachmentTransport transport = new FakeAttachmentTransport();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            gateway,
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            testAttachmentControllerFactory(transport),
            waveId -> { },
            waveId -> { });

    controller.start();
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 44L, "ABCD", "b+root"));
    controller.onAttachmentFilesSelected(
        Arrays.asList(new J2clComposeSurfaceController.AttachmentFileSelection(new Object(), "late.png")));
    controller.onWriteSessionChanged(null);
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+2", "chan-2", 45L, "BCDE", "b+root"));

    transport.complete(0, new J2clAttachmentUploadClient.HttpResponse(200, "OK", null));
    controller.onReplySubmitted("");

    Assert.assertEquals(0, gateway.submitCalls);
    Assert.assertEquals(
        J2clComposeSurfaceController.EMPTY_REPLY_VALIDATION_MESSAGE, view.model.getReplyErrorText());
  }

  @Test
  public void pastedImageUploadsAndSubmitsStructuredReplyContentWhenSignedIn() {
    FakeGateway gateway = new FakeGateway();
    FakeView view = new FakeView();
    FakeAttachmentTransport transport = new FakeAttachmentTransport();
    List<String> refreshed = new ArrayList<String>();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            gateway,
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            testAttachmentControllerFactory(transport),
            waveId -> { },
            refreshed::add);
    Object payload = new Object();

    controller.start();
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 44L, "ABCD", "b+root"));
    controller.onPastedImage(payload);

    Assert.assertEquals(1, transport.requests.size());
    Assert.assertSame(payload, transport.requests.get(0).getPart(2).getPayload());
    Assert.assertEquals("Uploading pasted-image.png (0%).", view.model.getCommandStatusText());

    transport.complete(0, new J2clAttachmentUploadClient.HttpResponse(200, "OK", null));
    controller.onReplySubmitted("");

    Assert.assertEquals(Arrays.asList("example.com/w+1"), refreshed);
    assertContains(
        gateway.lastSubmitRequest.getDeltaJson(),
        "{\"1\":\"attachment\",\"2\":\"example.com/seedA\"}",
        "\"2\":\"pasted-image.png\"");
  }

  @Test
  public void richToolbarAnnotationSurvivesAttachmentUploadQueueStatus() {
    FakeGateway gateway = new FakeGateway();
    FakeView view = new FakeView();
    FakeAttachmentTransport transport = new FakeAttachmentTransport();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            gateway,
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            testAttachmentControllerFactory(transport),
            waveId -> { },
            waveId -> { });

    controller.start();
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 44L, "ABCD", "b+root"));
    Assert.assertTrue(controller.onToolbarAction(J2clDailyToolbarAction.BOLD));
    controller.onAttachmentFilesSelected(
        Arrays.asList(
            new J2clComposeSurfaceController.AttachmentFileSelection(
                new Object(), "bold-attachment.png")));
    transport.complete(0, new J2clAttachmentUploadClient.HttpResponse(200, "OK", null));
    controller.onReplySubmitted("Bold plus file");

    assertContains(
        gateway.lastSubmitRequest.getDeltaJson(),
        "{\"1\":{\"3\":[{\"1\":\"fontWeight\",\"3\":\"bold\"}]}}",
        "\"2\":\"Bold plus file\"",
        "{\"1\":\"attachment\",\"2\":\"example.com/seedA\"}",
        "\"2\":\"bold-attachment.png\"");
  }

  @Test
  public void richToolbarAnnotationCanToggleOffAfterAttachmentQueueStatus() {
    FakeGateway gateway = new FakeGateway();
    FakeView view = new FakeView();
    FakeAttachmentTransport transport = new FakeAttachmentTransport();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            gateway,
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            testAttachmentControllerFactory(transport),
            waveId -> { },
            waveId -> { });

    controller.start();
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 44L, "ABCD", "b+root"));
    Assert.assertTrue(controller.onToolbarAction(J2clDailyToolbarAction.BOLD));
    controller.onAttachmentFilesSelected(
        Arrays.asList(
            new J2clComposeSurfaceController.AttachmentFileSelection(
                new Object(), "plain-attachment.png")));
    transport.complete(0, new J2clAttachmentUploadClient.HttpResponse(200, "OK", null));

    Assert.assertTrue(controller.onToolbarAction(J2clDailyToolbarAction.BOLD));
    controller.onReplySubmitted("Plain plus file");

    Assert.assertFalse(gateway.lastSubmitRequest.getDeltaJson().contains("fontWeight"));
    assertContains(
        gateway.lastSubmitRequest.getDeltaJson(),
        "\"2\":\"Plain plus file\"",
        "{\"1\":\"attachment\",\"2\":\"example.com/seedA\"}",
        "\"2\":\"plain-attachment.png\"");
  }

  @Test
  public void richToolbarAnnotationSurvivesAttachmentUploadErrorStatus() {
    FakeGateway gateway = new FakeGateway();
    FakeView view = new FakeView();
    FakeAttachmentTransport transport = new FakeAttachmentTransport();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            gateway,
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            testAttachmentControllerFactory(transport),
            waveId -> { },
            waveId -> { });

    controller.start();
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 44L, "ABCD", "b+root"));
    Assert.assertTrue(controller.onToolbarAction(J2clDailyToolbarAction.BOLD));
    controller.onAttachmentFilesSelected(
        Arrays.asList(
            new J2clComposeSurfaceController.AttachmentFileSelection(
                new Object(), "failed-attachment.png")));
    transport.complete(0, new J2clAttachmentUploadClient.HttpResponse(500, "failed", null));
    Assert.assertTrue(view.model.getCommandErrorText().contains("failed-attachment.png"));
    controller.onReplySubmitted("Bold without failed file");

    assertContains(
        gateway.lastSubmitRequest.getDeltaJson(),
        "{\"1\":{\"3\":[{\"1\":\"fontWeight\",\"3\":\"bold\"}]}}",
        "\"2\":\"Bold without failed file\"");
    Assert.assertFalse(gateway.lastSubmitRequest.getDeltaJson().contains("\"1\":\"attachment\""));
  }
}
