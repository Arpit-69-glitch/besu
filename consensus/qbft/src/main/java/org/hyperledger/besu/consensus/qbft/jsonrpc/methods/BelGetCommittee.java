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

import org.hyperledger.besu.consensus.common.validator.CommitteeProvider;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.exception.InvalidJsonRpcParameters;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.AbstractBlockParameterMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.BlockParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.core.BlockHeader;

import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.stream.Collectors;

/** Read-only JSON-RPC exposure of BEL's verification committee. */
public class BelGetCommittee extends AbstractBlockParameterMethod implements JsonRpcMethod {

  private final CommitteeProvider committeeProvider;

  public BelGetCommittee(
      final BlockchainQueries blockchainQueries, final CommitteeProvider committeeProvider) {
    super(blockchainQueries);
    this.committeeProvider = committeeProvider;
  }

  @Override
  protected BlockParameter blockParameter(final JsonRpcRequestContext request) {
    try {
      return request.getRequiredParameter(0, BlockParameter.class);
    } catch (Exception e) {
      throw new InvalidJsonRpcParameters(
          "Invalid block parameter (index 0)", RpcErrorType.INVALID_BLOCK_PARAMS, e);
    }
  }

  @Override
  protected Object pendingResult(final JsonRpcRequestContext request) {
    final BlockHeader blockHeader = getBlockchainQueries().headBlockHeader();
    return response(blockHeader.getNumber(), committeeProvider.getCommitteeForBlock(blockHeader));
  }

  @Override
  protected Object resultByBlockNumber(
      final JsonRpcRequestContext request, final long blockNumber) {
    final Optional<BlockHeader> blockHeader =
        getBlockchainQueries().getBlockHeaderByNumber(blockNumber);
    return blockHeader
        .map(header -> response(blockNumber, committeeProvider.getCommitteeForBlock(header)))
        .orElse(null);
  }

  private Object response(final long height, final java.util.Collection<Address> committee) {
    final var result = new LinkedHashMap<String, Object>();
    result.put("height", height);
    result.put(
        "validatorIds",
        committee.stream().map(Address::toString).collect(Collectors.toList()));
    return result;
  }

  @Override
  public String getName() {
    return "bel_getCommittee";
  }
}
