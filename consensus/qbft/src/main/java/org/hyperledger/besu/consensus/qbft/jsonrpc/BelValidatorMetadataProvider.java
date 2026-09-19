package org.hyperledger.besu.consensus.qbft.jsonrpc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hyperledger.besu.consensus.common.validator.ValidatorProvider;
import org.hyperledger.besu.crypto.SECPPublicKey;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Util;
import org.apache.tuweni.bytes.Bytes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Builds authoritative validator metadata for the backend-facing BEL RPC. */
public final class BelValidatorMetadataProvider {
  private static final String REGISTRY_ENV = "BEL_VALIDATOR_PUBLIC_KEYS_FILE";
  private static final Path DEFAULT_REGISTRY = Path.of("config", "validator-public-keys.json");

  private final ValidatorProvider validatorProvider;
  private final BlockchainQueries blockchainQueries;
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final Path registryPath;
  private final Map<Address, String> publicKeys = new LinkedHashMap<>();
  private final Map<Address, String> joinedAtCache = new LinkedHashMap<>();
  private boolean loaded;

  public BelValidatorMetadataProvider(
      final ValidatorProvider validatorProvider, final BlockchainQueries blockchainQueries) {
    this(validatorProvider, blockchainQueries, configuredRegistryPath());
  }

  public BelValidatorMetadataProvider(
      final ValidatorProvider validatorProvider,
      final BlockchainQueries blockchainQueries,
      final Path registryPath) {
    this.validatorProvider = validatorProvider;
    this.blockchainQueries = blockchainQueries;
    this.registryPath = registryPath;
  }

  private static Path configuredRegistryPath() {
    final String configured = System.getenv(REGISTRY_ENV);
    return configured == null || configured.isBlank()
        ? DEFAULT_REGISTRY
        : Path.of(configured);
  }

  public synchronized List<Map<String, String>> metadata(
      final BlockHeader header, final Collection<Address> activeValidators) {
    loadRegistry();
    final Set<Address> allValidators = new LinkedHashSet<>(publicKeys.keySet());
    allValidators.addAll(activeValidators);
    for (final Address active : activeValidators) {
      if (!publicKeys.containsKey(active)) {
        throw new IllegalStateException(
            "Active validator has no registered public key: " + active);
      }
    }

    final Map<Address, String> joinedAt = joinedAtFor(allValidators, header.getNumber());
    final List<Map<String, String>> result = new ArrayList<>();
    for (final Address address : allValidators) {
      final String publicKey = publicKeys.get(address);
      if (publicKey == null) {
        throw new IllegalStateException(
            "Validator has no registered public key: " + address);
      }
      final Map<String, String> item = new LinkedHashMap<>();
      item.put("validatorId", address.toString());
      item.put("publicKey", publicKey);
      item.put("status", activeValidators.contains(address) ? "ACTIVE" : "INACTIVE");
      item.put("joinedAt", joinedAt.get(address));
      result.add(item);
    }
    return result;
  }

  private Map<Address, String> joinedAtFor(
      final Collection<Address> validators, final long targetBlock) {
    final Map<Address, String> result = new LinkedHashMap<>();
    for (Address address : validators) {
      if (joinedAtCache.containsKey(address)) {
        result.put(address, joinedAtCache.get(address));
      }
    }

    final Set<Address> unresolved = new LinkedHashSet<>(validators);
    unresolved.removeAll(result.keySet());
    for (long number = 0; number <= targetBlock && !unresolved.isEmpty(); number++) {
      final Optional<BlockHeader> maybeHeader = blockchainQueries.getBlockHeaderByNumber(number);
      if (maybeHeader.isEmpty()) {
        continue;
      }
      final Collection<Address> atBlock = validatorProvider.getValidatorsForBlock(maybeHeader.get());
      final List<Address> found = atBlock.stream().filter(unresolved::contains).toList();
      for (Address address : found) {
        final String timestamp =
            DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochSecond(maybeHeader.get().getTimestamp()));
        joinedAtCache.put(address, timestamp);
        result.put(address, timestamp);
      }
      unresolved.removeAll(found);
    }
    if (!unresolved.isEmpty()) {
      throw new IllegalStateException(
          "No canonical onboarding block found for validator(s): " + unresolved);
    }
    return result;
  }

  private void loadRegistry() {
    if (loaded) {
      return;
    }
    final Path path = registryPath;
    if (!Files.isRegularFile(path)) {
      throw new IllegalStateException(
          "Validator public-key registry is required for bel_getValidators: " + path);
    }
    try {
      final JsonNode root = objectMapper.readTree(Files.readString(path));
      final JsonNode entries = root.has("validators") ? root.get("validators") : root;
      if (entries.isObject()) {
        entries.fields().forEachRemaining(entry -> register(entry.getKey(), entry.getValue().asText()));
      } else if (entries.isArray()) {
        for (JsonNode entry : entries) {
          register(entry.get("validatorId").asText(), entry.get("publicKey").asText());
        }
      } else {
        throw new IllegalArgumentException("Registry must be an object or validators array");
      }
      loaded = true;
    } catch (IOException | RuntimeException e) {
      throw new IllegalStateException("Unable to load validator public-key registry: " + path, e);
    }
  }

  private void register(final String validatorId, final String encodedKey) {
    final Address address = Address.fromHexString(validatorId);
    final Bytes keyBytes = Bytes.fromHexString(encodedKey);
    if (keyBytes.size() != 64) {
      throw new IllegalArgumentException(
          "Validator public key must be 64 bytes (X || Y): " + validatorId);
    }
    final SECPPublicKey publicKey =
        SignatureAlgorithmFactory.getInstance().createPublicKey(keyBytes);
    if (!Util.publicKeyToAddress(publicKey).equals(address)) {
      throw new IllegalArgumentException(
          "Validator public key does not derive validatorId: " + validatorId);
    }
    if (publicKeys.put(address, keyBytes.toHexString()) != null) {
      throw new IllegalArgumentException("Duplicate validatorId: " + validatorId);
    }
  }
}
