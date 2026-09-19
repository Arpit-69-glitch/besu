package org.hyperledger.besu.consensus.qbft.jsonrpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.consensus.common.validator.ValidatorProvider;
import org.hyperledger.besu.crypto.SECPPrivateKey;
import org.hyperledger.besu.crypto.SECPPublicKey;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BelValidatorMetadataProviderTest {
  @Mock private ValidatorProvider validatorProvider;
  @Mock private BlockchainQueries blockchainQueries;
  @Mock private BlockHeader header;

  @TempDir Path tempDir;

  @Test
  void exposesValidatedKeyStatusAndCanonicalJoinedAt() throws Exception {
    final SECPPrivateKey privateKey =
        SignatureAlgorithmFactory.getInstance()
            .createPrivateKey(Bytes32.fromHexString("0x" + "01".repeat(32)));
    final SECPPublicKey publicKey =
        SignatureAlgorithmFactory.getInstance().createPublicKey(privateKey);
    final Address address = Util.publicKeyToAddress(publicKey);
    final Path registry = tempDir.resolve("validator-public-keys.json");
    final String registryJson =
        "{\"validators\":{\""
            + address
            + "\":\""
            + publicKey.getEncodedBytes().toHexString()
            + "\"}}";
    Files.writeString(registry, registryJson);

    when(header.getNumber()).thenReturn(0L);
    when(header.getTimestamp()).thenReturn(Instant.parse("2026-09-10T12:30:00Z").getEpochSecond());
    when(blockchainQueries.getBlockHeaderByNumber(0L)).thenReturn(Optional.of(header));
    when(validatorProvider.getValidatorsForBlock(header)).thenReturn(List.of(address));

    final BelValidatorMetadataProvider metadataProvider =
        new BelValidatorMetadataProvider(validatorProvider, blockchainQueries, registry);

    final List<Map<String, String>> result = metadataProvider.metadata(header, List.of(address));

    assertThat(result)
        .containsExactly(
            Map.of(
                "validatorId", address.toString(),
                "publicKey", publicKey.getEncodedBytes().toHexString(),
                "status", "ACTIVE",
                "joinedAt", "2026-09-10T12:30:00Z"));
  }
}
