package org.hyperledger.besu.consensus.bel;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Deterministic BEL hash-index leader selection; no leader VRF is used. */
public final class BelLeaderSelector {
  private static final byte[] DOMAIN = "BEL-LEADER".getBytes(StandardCharsets.UTF_8);

  private BelLeaderSelector() {}

  public static byte[] select(
      final byte[] seed, final long height, final long round, final List<byte[]> orderedCommittee) {
    if (orderedCommittee == null || orderedCommittee.isEmpty()) {
      throw new IllegalArgumentException("committee must not be empty");
    }
    if (height < 0 || round < 0) {
      throw new IllegalArgumentException("height and round must be non-negative");
    }
    final ByteBuffer context = ByteBuffer.allocate(Long.BYTES * 2);
    context.putLong(height).putLong(round);
    final ByteBuffer count = ByteBuffer.allocate(Integer.BYTES).putInt(orderedCommittee.size());
    final byte[] encodedCommittee = encodeCommittee(orderedCommittee);
    final byte[] digest = BelSeed.sha256(DOMAIN, seed, context.array(), count.array(), encodedCommittee);
    final int index = new BigInteger(1, digest).mod(BigInteger.valueOf(orderedCommittee.size())).intValueExact();
    return orderedCommittee.get(index).clone();
  }

  private static byte[] encodeCommittee(final List<byte[]> committee) {
    int size = 0;
    for (final byte[] validator : committee) {
      if (validator == null || validator.length == 0) {
        throw new IllegalArgumentException("validator id must not be empty");
      }
      size += Integer.BYTES + validator.length;
    }
    final ByteBuffer encoded = ByteBuffer.allocate(size);
    for (final byte[] validator : committee) {
      encoded.putInt(validator.length).put(validator);
    }
    return encoded.array();
  }
}
