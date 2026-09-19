/*
 * Copyright ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.besu.consensus.qbft.jsonrpc.methods;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.consensus.common.validator.CommitteeProvider;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.BlockParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.core.BlockHeader;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BelGetCommitteeTest {

  @Mock private BlockchainQueries blockchainQueries;
  @Mock private BlockHeader blockHeader;
  @Mock private JsonRpcRequestContext request;
  @Mock private CommitteeProvider committeeProvider;

  private BelGetCommittee method;

  @BeforeEach
  void setUp() {
    method = new BelGetCommittee(blockchainQueries, committeeProvider);
  }

  @Test
  void blockParameterIsParameter0() {
    request =
        new JsonRpcRequestContext(
            new JsonRpcRequest("?", "ignore", new String[] {"0x1245"}));
    final BlockParameter blockParameter = method.blockParameter(request);

    assertThat(blockParameter.getNumber()).isPresent();
    assertThat(blockParameter.getNumber().get()).isEqualTo(0x1245);
  }

  @Test
  void nameShouldBeCorrect() {
    assertThat(method.getName()).isEqualTo("bel_getCommittee");
  }

  @Test
  void shouldReturnCommitteeWithFrozenSchema() {
    when(blockchainQueries.getBlockHeaderByNumber(12)).thenReturn(Optional.of(blockHeader));
    when(committeeProvider.getCommitteeForBlock(blockHeader))
        .thenReturn(Collections.singletonList(Address.ID));

    final Object result = method.resultByBlockNumber(request, 12);

    assertThat(result)
        .isEqualTo(
            Map.of(
                "height", 12L,
                "validatorIds", List.of(Address.ID.toString())));
  }

  @Test
  void shouldReturnCommitteeForLatestBlock() {
    request =
        new JsonRpcRequestContext(
            new JsonRpcRequest("2.0", "bel_getCommittee", new String[] {"latest"}));
    when(blockchainQueries.headBlockNumber()).thenReturn(12L);
    when(blockchainQueries.getBlockHeaderByNumber(12)).thenReturn(Optional.of(blockHeader));
    when(committeeProvider.getCommitteeForBlock(blockHeader))
        .thenReturn(Collections.singletonList(Address.ID));

    final Object result = method.response(request);

    assertThat(result).isInstanceOf(JsonRpcSuccessResponse.class);
    assertThat(((JsonRpcSuccessResponse) result).getResult())
        .isEqualTo(
            Map.of(
                "height", 12L,
                "validatorIds", List.of(Address.ID.toString())));
  }
}
