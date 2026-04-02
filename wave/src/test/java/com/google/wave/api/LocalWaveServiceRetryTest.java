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

package com.google.wave.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import junit.framework.TestCase;

import org.waveprotocol.box.server.robots.agent.LocalOperationSubmitter;
import org.waveprotocol.wave.model.wave.ParticipantId;

public class LocalWaveServiceRetryTest extends TestCase {

  public void testSubmitRestoresPendingOperationsWhenSubmitterFails() throws Exception {
    LocalOperationSubmitter submitter = mock(LocalOperationSubmitter.class);
    when(submitter.submitOperations(anyList(), any(ParticipantId.class)))
        .thenThrow(new IndexOutOfBoundsException("boom"));

    LocalWaveService service = new LocalWaveService(submitter,
        new ParticipantId("robot@example.com"), "hash");

    OperationQueue opQueue = new OperationQueue();
    opQueue.appendOperation(OperationType.ROBOT_NOTIFY,
        OperationRequest.Parameter.of(JsonRpcConstant.ParamsProperty.CAPABILITIES_HASH, "123"));
    Wavelet wavelet = mock(Wavelet.class);
    when(wavelet.getOperationQueue()).thenReturn(opQueue);

    assertEquals(1, opQueue.getPendingOperations().size());
    try {
      service.submit(wavelet, "unused");
      fail("submit should fail when the submitter throws");
    } catch (IndexOutOfBoundsException expected) {
    }
    assertEquals(2, opQueue.getPendingOperations().size());
  }
}
