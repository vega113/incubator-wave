/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.waveprotocol.box.server.robots.dataapi;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.wave.api.BlipData;
import com.google.wave.api.InvalidRequestException;
import com.google.wave.api.JsonRpcConstant.ParamsProperty;
import com.google.wave.api.JsonRpcResponse;
import com.google.wave.api.OperationRequest;
import com.google.wave.api.OperationType;
import com.google.wave.api.ProtocolVersion;
import com.google.wave.api.RobotSerializer;
import com.google.wave.api.data.converter.EventDataConverter;
import com.google.wave.api.data.converter.EventDataConverterManager;
import com.google.wave.api.OperationRequest.Parameter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.waveprotocol.box.server.robots.OperationServiceRegistry;
import org.waveprotocol.box.server.robots.OperationContext;
import org.waveprotocol.box.server.robots.RobotWaveletData;
import org.waveprotocol.box.server.robots.operations.OperationService;
import org.waveprotocol.box.server.robots.util.ConversationUtil;
import org.waveprotocol.box.server.util.WaveletDataUtil;
import org.waveprotocol.box.server.waveserver.WaveletProvider;
import org.waveprotocol.wave.model.document.operation.DocInitialization;
import org.waveprotocol.wave.model.document.operation.impl.DocInitializationBuilder;
import org.waveprotocol.wave.model.id.IdURIEncoderDecoder;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.model.version.HashedVersionFactory;
import org.waveprotocol.wave.model.version.HashedVersionZeroFactoryImpl;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.wave.data.ObservableWaveletData;
import org.waveprotocol.wave.util.escapers.jvm.JavaUrlCodec;

/**
 * Unit tests for per-operation scope enforcement in {@link BaseApiServlet}.
 *
 * <p>Verifies that:
 * <ul>
 *   <li>Fetch operations succeed with read scope</li>
 *   <li>Fetch operations fail without read scope</li>
 *   <li>Modify operations fail with only read scope</li>
 *   <li>Modify operations succeed with write scope</li>
 * </ul>
 */
public class BaseApiServletScopeEnforcementTest {

  private static final ParticipantId PARTICIPANT =
      ParticipantId.ofUnsafe("alice@example.com");
  private static final ParticipantId OTHER_PARTICIPANT =
      ParticipantId.ofUnsafe("bob@example.com");
  private static final IdURIEncoderDecoder URI_CODEC =
      new IdURIEncoderDecoder(new JavaUrlCodec());
  private static final HashedVersionFactory HASH_FACTORY =
      new HashedVersionZeroFactoryImpl(URI_CODEC);
  private static final WaveId WAVE_ID = WaveId.of("example.com", "waveid");
  private static final WaveletId WAVELET_ID = WaveletId.of("example.com", "conv+root");
  private static final WaveletName WAVELET_NAME = WaveletName.of(WAVE_ID, WAVELET_ID);

  private RobotSerializer robotSerializer;
  private EventDataConverterManager converterManager;
  private WaveletProvider waveletProvider;
  private OperationServiceRegistry operationRegistry;

  private HttpServletRequest req;
  private HttpServletResponse resp;
  private StringWriter responseBody;

  /** Concrete subclass so we can test the abstract BaseApiServlet directly. */
  private TestableBaseApiServlet servlet;

  @Before
  public void setUp() throws Exception {
    robotSerializer = mock(RobotSerializer.class);
    converterManager = mock(EventDataConverterManager.class);
    waveletProvider = mock(WaveletProvider.class);
    operationRegistry = mock(OperationServiceRegistry.class);
    ConversationUtil conversationUtil = mock(ConversationUtil.class);

    EventDataConverter eventDataConverter = mock(EventDataConverter.class);
    when(converterManager.getEventDataConverter(any(ProtocolVersion.class)))
        .thenReturn(eventDataConverter);

    // Mock operation registry to return a no-op service for all operation types
    OperationService noOpService = mock(OperationService.class);
    when(operationRegistry.getServiceFor(any(com.google.wave.api.OperationType.class)))
        .thenReturn(noOpService);

    req = mock(HttpServletRequest.class);
    when(req.getReader()).thenReturn(new BufferedReader(new StringReader("[]")));

    resp = mock(HttpServletResponse.class);
    responseBody = new StringWriter();
    when(resp.getWriter()).thenReturn(new PrintWriter(responseBody));

    servlet = new TestableBaseApiServlet(
        robotSerializer, converterManager, waveletProvider, operationRegistry, conversationUtil);
  }

  @Test
  public void testFetchWaveletWithReadScopeSucceeds() throws Exception {
    // robot.fetchWave maps to FETCH_WAVE which requires wave:data:read
    OperationRequest fetchOp = new OperationRequest("robot.fetchWave", "op1");
    List<OperationRequest> ops = Collections.singletonList(fetchOp);
    when(robotSerializer.deserializeOperations(any())).thenReturn(ops);
    when(robotSerializer.serialize(any(), any(Type.class), any(ProtocolVersion.class)))
        .thenReturn("[]");

    Set<String> scopes = Set.of(OpScopeMapper.SCOPE_WAVE_DATA_READ);

    servlet.invokeProcessOpsRequest(req, resp, PARTICIPANT, scopes);

    // Should NOT send a 403 error
    verify(resp, never()).sendError(eq(HttpServletResponse.SC_FORBIDDEN), anyString());
  }

  @Test
  public void testFetchWaveletWithoutReadScopeFails() throws Exception {
    // robot.fetchWave maps to FETCH_WAVE which requires wave:data:read
    OperationRequest fetchOp = new OperationRequest("robot.fetchWave", "op1");
    List<OperationRequest> ops = Collections.singletonList(fetchOp);
    when(robotSerializer.deserializeOperations(any())).thenReturn(ops);

    // Token has no scopes at all
    Set<String> scopes = Set.of();

    servlet.invokeProcessOpsRequest(req, resp, PARTICIPANT, scopes);

    // Should have sent 403
    verify(resp).sendError(eq(HttpServletResponse.SC_FORBIDDEN), anyString());
  }

  @Test
  public void testModifyWaveletWithReadScopeFails() throws Exception {
    // wavelet.appendBlip maps to MODIFY_WAVELET which requires wave:data:write
    OperationRequest modifyOp = new OperationRequest("wavelet.appendBlip", "op1");
    List<OperationRequest> ops = Collections.singletonList(modifyOp);
    when(robotSerializer.deserializeOperations(any())).thenReturn(ops);

    // Token only has read scope
    Set<String> scopes = Set.of(OpScopeMapper.SCOPE_WAVE_DATA_READ);

    servlet.invokeProcessOpsRequest(req, resp, PARTICIPANT, scopes);

    // Should have sent 403
    verify(resp).sendError(eq(HttpServletResponse.SC_FORBIDDEN), anyString());
  }

  @Test
  public void testModifyWaveletWithWriteScopeSucceeds() throws Exception {
    // wavelet.appendBlip maps to MODIFY_WAVELET which requires wave:data:write
    OperationRequest modifyOp = new OperationRequest("wavelet.appendBlip", "op1");
    List<OperationRequest> ops = Collections.singletonList(modifyOp);
    when(robotSerializer.deserializeOperations(any())).thenReturn(ops);
    when(robotSerializer.serialize(any(), any(Type.class), any(ProtocolVersion.class)))
        .thenReturn("[]");

    Set<String> scopes = Set.of(OpScopeMapper.SCOPE_WAVE_DATA_WRITE);

    servlet.invokeProcessOpsRequest(req, resp, PARTICIPANT, scopes);

    // Should NOT send a 403 error
    verify(resp, never()).sendError(eq(HttpServletResponse.SC_FORBIDDEN), anyString());
  }

  @Test
  public void testSubmitFailureConvertsMutatingResponseToError() throws Exception {
    OperationRequest modifyOp = new OperationRequest("wavelet.appendBlip", "op1");
    BlipData blipData =
        new BlipData(WAVE_ID.serialise(), WAVELET_ID.serialise(), "TBD_test_blip", "hello");
    blipData.setBlipId("TBD_test_blip");
    modifyOp.addParameter(Parameter.of(ParamsProperty.WAVE_ID, WAVE_ID.serialise()));
    modifyOp.addParameter(Parameter.of(ParamsProperty.WAVELET_ID, WAVELET_ID.serialise()));
    modifyOp.addParameter(Parameter.of(ParamsProperty.BLIP_DATA, blipData));
    List<OperationRequest> ops = Collections.singletonList(modifyOp);
    when(robotSerializer.deserializeOperations(any())).thenReturn(ops);
    when(robotSerializer.serialize(any(), any(Type.class), any(ProtocolVersion.class)))
        .thenReturn("[]");

    when(operationRegistry.getServiceFor(eq(OperationType.WAVELET_APPEND_BLIP)))
        .thenReturn(new FailingDeltaSubmitService());
    doAnswer(invocation -> {
      WaveletProvider.SubmitRequestListener listener = invocation.getArgument(2);
      listener.onFailure("delta failed");
      return null;
    }).when(waveletProvider).submitRequest(eq(WAVELET_NAME), any(), any());

    boolean handled =
        servlet.invokeProcessOpsRequest(req, resp, PARTICIPANT, Set.of(OpScopeMapper.SCOPE_WAVE_DATA_WRITE));

    ArgumentCaptor<Object> responsesCaptor = ArgumentCaptor.forClass(Object.class);
    verify(robotSerializer).serialize(responsesCaptor.capture(), any(Type.class), any(ProtocolVersion.class));
    @SuppressWarnings("unchecked")
    List<JsonRpcResponse> responses = (List<JsonRpcResponse>) responsesCaptor.getValue();

    assertTrue(handled);
    assertEquals(1, responses.size());
    assertTrue(responses.get(0).isError());
    assertEquals("delta failed", responses.get(0).getErrorMessage());
  }

  // --- Minimal concrete subclass to expose processOpsRequest ---

  private static RobotWaveletData createWaveletWithPendingDelta() {
    org.waveprotocol.wave.model.version.HashedVersion versionZero =
        HASH_FACTORY.createVersionZero(WAVELET_NAME);
    ObservableWaveletData waveletData =
        WaveletDataUtil.createEmptyWavelet(WAVELET_NAME, PARTICIPANT, versionZero, 0L);
    DocInitialization content = new DocInitializationBuilder().build();
    waveletData.createDocument(
        "b+example", PARTICIPANT, Collections.singletonList(PARTICIPANT), content, 0L, 0);

    RobotWaveletData wavelet = new RobotWaveletData(waveletData, versionZero);
    wavelet.getOpBasedWavelet(PARTICIPANT).addParticipant(OTHER_PARTICIPANT);
    return wavelet;
  }

  private static final class FailingDeltaSubmitService implements OperationService {
    @Override
    public void execute(OperationRequest operation, OperationContext context, ParticipantId participant)
        throws InvalidRequestException {
      context.putWavelet(WAVE_ID, WAVELET_ID, createWaveletWithPendingDelta());
      context.constructResponse(operation, Map.of(ParamsProperty.BLIP_ID, "b+created"));
    }
  }

  /**
   * Concrete subclass of BaseApiServlet for testing.
   * processOpsRequest is final in the parent and accessible via inheritance (protected),
   * so we just need a concrete class to instantiate.
   */
  private static final class TestableBaseApiServlet extends BaseApiServlet {
    TestableBaseApiServlet(RobotSerializer robotSerializer,
                           EventDataConverterManager converterManager,
                           WaveletProvider waveletProvider,
                           OperationServiceRegistry operationRegistry,
                           ConversationUtil conversationUtil) {
      super(robotSerializer, converterManager, waveletProvider, operationRegistry, conversationUtil);
    }

    public boolean invokeProcessOpsRequest(HttpServletRequest req, HttpServletResponse resp,
                                           ParticipantId participant, Set<String> tokenScopes)
        throws IOException {
      return processOpsRequest(req, resp, participant, tokenScopes);
    }
  }
}
