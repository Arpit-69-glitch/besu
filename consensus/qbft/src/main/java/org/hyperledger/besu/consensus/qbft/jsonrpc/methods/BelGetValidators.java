package org.hyperledger.besu.consensus.qbft.jsonrpc.methods;

import org.hyperledger.besu.consensus.common.validator.ValidatorProvider;
import org.hyperledger.besu.consensus.qbft.jsonrpc.BelValidatorMetadataProvider;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.exception.InvalidJsonRpcParameters;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.AbstractBlockParameterMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.BlockParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.core.BlockHeader;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Optional;

/** Read-only JSON-RPC exposure of authoritative Besu validator metadata. */
public final class BelGetValidators extends AbstractBlockParameterMethod implements JsonRpcMethod {
  private final ValidatorProvider validatorProvider;
  private final BelValidatorMetadataProvider metadataProvider;

  public BelGetValidators(
      final BlockchainQueries blockchainQueries,
      final ValidatorProvider validatorProvider,
      final BelValidatorMetadataProvider metadataProvider) {
    super(blockchainQueries);
    this.validatorProvider = validatorProvider;
    this.metadataProvider = metadataProvider;
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
    final BlockHeader header = getBlockchainQueries().headBlockHeader();
    return response(header, validatorProvider.getValidatorsForBlock(header));
  }

  @Override
  protected Object resultByBlockNumber(
      final JsonRpcRequestContext request, final long blockNumber) {
    final Optional<BlockHeader> header = getBlockchainQueries().getBlockHeaderByNumber(blockNumber);
    return header.map(value -> response(value, validatorProvider.getValidatorsForBlock(value))).orElse(null);
  }

  private Object response(final BlockHeader header, final Collection<org.hyperledger.besu.datatypes.Address> active) {
    final LinkedHashMap<String, Object> result = new LinkedHashMap<>();
    result.put("height", header.getNumber());
    result.put("validators", metadataProvider.metadata(header, active));
    return result;
  }

  @Override
  public String getName() {
    return "bel_getValidators";
  }
}
