package org.hyperledger.besu.consensus.bel;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Verifies the complete set of committee-selection evidence before selection.
 *
 * <p>This class deliberately does not implement a VRF. The concrete cryptographic backend is
 * supplied through {@link VrfProvider}; the current deterministic provider is test-only and is
 * not RFC 9381 cryptography.
 */
public final class BelCommitteeEvidence {
  private static final byte[] DOMAIN = "BEL-COMMITTEE-VRF".getBytes(StandardCharsets.UTF_8);

  public record Candidate(byte[] validatorId, VrfPublicKey publicKey, VrfProof proof) {
    public Candidate(
        final byte[] validatorId, final VrfPublicKey publicKey, final VrfProof proof) {
      if (validatorId == null || validatorId.length == 0) {
        throw new IllegalArgumentException("validator id is required");
      }
      if (publicKey == null || proof == null) {
        throw new IllegalArgumentException("public key and proof are required");
      }
      this.validatorId = validatorId.clone();
      this.publicKey = publicKey;
      this.proof = proof;
    }
  }

  private BelCommitteeEvidence() {}

  /**
   * Verifies one ticket per validator and returns the protocol-selected committee.
   *
   * <p>Every active validator is verified, including validators that are not selected. This keeps
   * the selection input deterministic and prevents a proposer from silently omitting tickets to
   * influence the fallback ordering.
   */
  public static List<BelCommitteeSelector.VrfTicket> verifyAndSelect(
      final int validatorPopulation,
      final byte[] seed,
      final long height,
      final List<Candidate> candidates,
      final VrfProvider vrfProvider) {
    return verifyAndSelect(
        validatorPopulation,
        seed,
        height,
        candidates,
        vrfProvider,
        BelCommitteeSelector.MINIMUM_COMMITTEE_SIZE);
  }

  public static List<BelCommitteeSelector.VrfTicket> verifyAndSelect(
      final int validatorPopulation,
      final byte[] seed,
      final long height,
      final List<Candidate> candidates,
      final VrfProvider vrfProvider,
      final int minimumCommitteeSize) {
    if (seed == null || seed.length == 0 || height < 0 || vrfProvider == null) {
      throw new IllegalArgumentException("invalid committee evidence inputs");
    }
    if (candidates == null || candidates.size() != validatorPopulation) {
      throw new IllegalArgumentException("one ticket is required per active validator");
    }

    final Set<ByteArrayKey> identities = new HashSet<>();
    final List<BelCommitteeSelector.VrfTicket> tickets =
        candidates.stream()
            .map(
                candidate -> {
                  final byte[] validatorId = candidate.validatorId();
                  if (!identities.add(new ByteArrayKey(validatorId))) {
                    throw new IllegalArgumentException("duplicate validator evidence");
                  }
                  final byte[] alpha = committeeInput(seed, height, validatorId);
                  final VrfResult result =
                      vrfProvider.verify(candidate.publicKey(), alpha, candidate.proof());
                  if (!result.valid() || result.output().length != 32) {
                    throw new IllegalArgumentException("invalid committee VRF evidence");
                  }
                  return new BelCommitteeSelector.VrfTicket(
                      validatorId, result.output(), candidate.proof().proof());
                })
            .toList();
    return BelCommitteeSelector.select(validatorPopulation, tickets, minimumCommitteeSize);
  }

  /** Exact alpha encoding from the frozen BEL protocol: domain || seed || enc(height) || id. */
  public static byte[] committeeInput(
      final byte[] seed, final long height, final byte[] validatorId) {
    if (seed == null || validatorId == null || validatorId.length == 0 || height < 0) {
      throw new IllegalArgumentException("invalid committee VRF input");
    }
    return BelSeed.sha256(DOMAIN, seed, ByteBuffer.allocate(Long.BYTES).putLong(height).array(), validatorId);
  }

  private record ByteArrayKey(byte[] value) {
    private ByteArrayKey(final byte[] value) {
      this.value = value.clone();
    }

    @Override
    public boolean equals(final Object other) {
      return other instanceof ByteArrayKey key && java.util.Arrays.equals(value, key.value);
    }

    @Override
    public int hashCode() {
      return java.util.Arrays.hashCode(value);
    }
  }
}
