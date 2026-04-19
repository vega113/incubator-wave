/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.waveprotocol.box.server.waveserver;

import static org.mockito.Mockito.mock;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.protobuf.ByteString;

import junit.framework.TestCase;

import org.waveprotocol.box.server.common.CoreWaveletOperationSerializer;
import org.waveprotocol.box.server.persistence.memory.MemoryDeltaStore;
import org.waveprotocol.wave.model.conversation.Conversation;
import org.waveprotocol.wave.model.conversation.ConversationBlip;
import org.waveprotocol.wave.model.conversation.WaveBasedConversationView;
import org.waveprotocol.wave.model.conversation.WaveletBasedConversation;
import org.waveprotocol.wave.federation.Proto.ProtocolDocumentOperation;
import org.waveprotocol.wave.federation.Proto.ProtocolSignature;
import org.waveprotocol.wave.federation.Proto.ProtocolSignature.SignatureAlgorithm;
import org.waveprotocol.wave.federation.Proto.ProtocolSignedDelta;
import org.waveprotocol.wave.federation.Proto.ProtocolWaveletDelta;
import org.waveprotocol.wave.federation.Proto.ProtocolWaveletOperation;
import org.waveprotocol.wave.federation.Proto.ProtocolWaveletOperation.MutateDocument;
import org.waveprotocol.wave.model.id.IdURIEncoderDecoder;
import org.waveprotocol.wave.model.id.IdGenerator;
import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.model.operation.wave.WaveletDelta;
import org.waveprotocol.wave.model.operation.wave.WaveletOperation;
import org.waveprotocol.wave.model.schema.conversation.ConversationSchemas;
import org.waveprotocol.wave.model.testing.FakeIdGenerator;
import org.waveprotocol.wave.model.testing.FakeSilentOperationSink;
import org.waveprotocol.wave.model.testing.FakeWaveView;
import org.waveprotocol.wave.model.version.HashedVersion;
import org.waveprotocol.wave.model.version.HashedVersionFactory;
import org.waveprotocol.wave.model.version.HashedVersionFactoryImpl;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.util.escapers.jvm.JavaUrlCodec;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;


/**
 * Tests for LocalWaveletContainerImpl.
 *
 * @author arb@google.com (Anthony Baxter)
 */
public class LocalWaveletContainerImplTest extends TestCase {

  private static final IdURIEncoderDecoder URI_CODEC =
      new IdURIEncoderDecoder(new JavaUrlCodec());
  private static final HashedVersionFactory HASH_FACTORY = new HashedVersionFactoryImpl(URI_CODEC);
  private static final Executor PERSIST_EXECUTOR = MoreExecutors.directExecutor();
  private static final Executor STORAGE_CONTINUATION_EXECUTOR = MoreExecutors.directExecutor();

  private static final WaveletName WAVELET_NAME = WaveletName.of("a", "a", "b", "b");
  private static final WaveletName CONVERSATION_WAVELET_NAME =
      WaveletName.of("example.com", "w+reply-depth", "example.com", "conv+root");
  private static final ProtocolSignature SIGNATURE = ProtocolSignature.newBuilder()
      .setSignatureAlgorithm(SignatureAlgorithm.SHA1_RSA)
      .setSignatureBytes(ByteString.EMPTY)
      .setSignerId(ByteString.EMPTY)
      .build();
  private static final String AUTHOR = "kermit@muppetshow.com";

  private static final HashedVersion HASHED_VERSION_ZERO =
      HASH_FACTORY.createVersionZero(WAVELET_NAME);
  private ProtocolWaveletOperation addParticipantOp;
  private static final String BLIP_ID = "b+muppet";
  private ProtocolWaveletOperation addBlipOp;
  private LocalWaveletContainerImpl wavelet;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    addParticipantOp = ProtocolWaveletOperation.newBuilder()
        .setAddParticipant(AUTHOR)
        .build();
    // An empty blip operation - creates a new document.
    addBlipOp = ProtocolWaveletOperation.newBuilder().setMutateDocument(
        MutateDocument.newBuilder().setDocumentId(BLIP_ID).setDocumentOperation(
            ProtocolDocumentOperation.newBuilder().build())).build();

    WaveletNotificationSubscriber notifiee = mock(WaveletNotificationSubscriber.class);
    DeltaStore deltaStore = new MemoryDeltaStore();
    WaveletState waveletState = DeltaStoreBasedWaveletState.create(deltaStore.open(WAVELET_NAME),
        PERSIST_EXECUTOR);
    wavelet = new LocalWaveletContainerImpl(WAVELET_NAME, notifiee,
        Futures.immediateFuture(waveletState), null, STORAGE_CONTINUATION_EXECUTOR);
    wavelet.awaitLoad();
  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
  }

  /**
   * Tests that duplicate operations are a no-op.
   *
   * @throws Exception should not be thrown.
   */
  public void testDuplicateOperations() throws Exception {
    assertEquals(0L, wavelet.getCurrentVersion().getVersion());

    // create the wavelet.
    WaveletDeltaRecord v0Response = wavelet.submitRequest(
        WAVELET_NAME, createProtocolSignedDelta(addParticipantOp, HASHED_VERSION_ZERO));
    assertEquals(1L, wavelet.getCurrentVersion().getVersion());

    ProtocolSignedDelta psd = createProtocolSignedDelta(
        addBlipOp, v0Response.getResultingVersion());

    WaveletDeltaRecord dar1 = wavelet.submitRequest(WAVELET_NAME, psd);
    assertEquals(2L, wavelet.getCurrentVersion().getVersion());

    WaveletDeltaRecord dar2 = wavelet.submitRequest(WAVELET_NAME, psd);
    assertEquals(2L, wavelet.getCurrentVersion().getVersion());

    assertEquals(dar1.getResultingVersion(), dar2.getResultingVersion());
  }

  public void testDuplicateReplyDepthDeltaSkipsValidationAfterLaterManifestEdit() throws Exception {
    LocalWaveletContainerImpl conversationalWavelet = createWavelet(CONVERSATION_WAVELET_NAME, 5);
    ConversationFixture fixture = createConversationFixture();

    submitWaveletOps(conversationalWavelet, drainOps(fixture.sink));

    Conversation conversation = fixture.conversation;
    ConversationBlip root = conversation.getRootThread().appendBlip();
    submitWaveletOps(conversationalWavelet, drainOps(fixture.sink));

    ConversationBlip replyParent = root.addReplyThread().appendBlip();
    submitWaveletOps(conversationalWavelet, drainOps(fixture.sink));

    replyParent.addReplyThread().appendBlip();
    ProtocolSignedDelta duplicateSignedDelta =
        createProtocolSignedDelta(new WaveletDelta(new ParticipantId(AUTHOR),
            conversationalWavelet.getCurrentVersion(), drainOps(fixture.sink)));
    WaveletDeltaRecord firstReplyDelta =
        conversationalWavelet.submitRequest(CONVERSATION_WAVELET_NAME, duplicateSignedDelta);

    root.addReplyThread().appendBlip();
    submitWaveletOps(conversationalWavelet, drainOps(fixture.sink));
    long versionAfterLaterEdit = conversationalWavelet.getCurrentVersion().getVersion();

    WaveletDeltaRecord retriedReplyDelta =
        conversationalWavelet.submitRequest(CONVERSATION_WAVELET_NAME, duplicateSignedDelta);

    assertEquals(versionAfterLaterEdit, conversationalWavelet.getCurrentVersion().getVersion());
    assertEquals(firstReplyDelta.getResultingVersion(), retriedReplyDelta.getResultingVersion());
  }

  private LocalWaveletContainerImpl createWavelet(WaveletName waveletName, int maxReplyDepth)
      throws Exception {
    WaveletNotificationSubscriber notifiee = mock(WaveletNotificationSubscriber.class);
    DeltaStore deltaStore = new MemoryDeltaStore();
    WaveletState waveletState = DeltaStoreBasedWaveletState.create(deltaStore.open(waveletName),
        PERSIST_EXECUTOR);
    LocalWaveletContainerImpl container = new LocalWaveletContainerImpl(waveletName, notifiee,
        Futures.immediateFuture(waveletState), null, STORAGE_CONTINUATION_EXECUTOR, maxReplyDepth);
    container.awaitLoad();
    return container;
  }

  private WaveletDeltaRecord submitWaveletOps(LocalWaveletContainerImpl target,
      List<WaveletOperation> ops) throws Exception {
    assertFalse(ops.isEmpty());
    return target.submitRequest(target.getWaveletName(),
        createProtocolSignedDelta(new WaveletDelta(new ParticipantId(AUTHOR),
            target.getCurrentVersion(), ops)));
  }

  private static ConversationFixture createConversationFixture() {
    FakeSilentOperationSink<WaveletOperation> sink =
        new FakeSilentOperationSink<WaveletOperation>();
    IdGenerator idGenerator = FakeIdGenerator.create();
    FakeWaveView waveView = FakeWaveView.builder(new ConversationSchemas())
        .with(idGenerator)
        .with(sink)
        .build();
    WaveBasedConversationView conversationView =
        WaveBasedConversationView.create(waveView, idGenerator);
    return new ConversationFixture(sink, conversationView.createConversation());
  }

  private static List<WaveletOperation> drainOps(FakeSilentOperationSink<WaveletOperation> sink) {
    List<WaveletOperation> ops = new ArrayList<WaveletOperation>(sink.getOps());
    sink.clear();
    return ops;
  }

  private static final class ConversationFixture {
    private final FakeSilentOperationSink<WaveletOperation> sink;
    private final WaveletBasedConversation conversation;

    private ConversationFixture(FakeSilentOperationSink<WaveletOperation> sink,
        WaveletBasedConversation conversation) {
      this.sink = sink;
      this.conversation = conversation;
    }
  }

  private ProtocolSignedDelta createProtocolSignedDelta(ProtocolWaveletOperation operation,
      HashedVersion protocolHashedVersion) {
    ProtocolWaveletDelta delta = ProtocolWaveletDelta.newBuilder()
        .setAuthor(AUTHOR)
        .setHashedVersion(CoreWaveletOperationSerializer.serialize(protocolHashedVersion))
        .addOperation(operation)
        .build();

    return ProtocolSignedDelta.newBuilder()
        .setDelta(delta.toByteString())
        .addSignature(SIGNATURE)
        .build();
  }

  private ProtocolSignedDelta createProtocolSignedDelta(WaveletDelta delta) {
    return ProtocolSignedDelta.newBuilder()
        .setDelta(CoreWaveletOperationSerializer.serialize(delta).toByteString())
        .addSignature(SIGNATURE)
        .build();
  }
}
