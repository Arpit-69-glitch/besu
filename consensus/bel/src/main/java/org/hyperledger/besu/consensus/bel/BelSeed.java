package org.hyperledger.besu.consensus.bel;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import org.hyperledger.besu.crypto.Hash;
import org.apache.tuweni.bytes.Bytes;

/** Canonical BEL height seed derivation. */
public final class BelSeed {
  public static final byte[] DOMAIN = "BEL-COMMITTEE-SEED".getBytes(StandardCharsets.UTF_8);

  private BelSeed() {}

  public static byte[] derive(
      final byte[] previousBlockHash, final long height, final String chainId) {
    if (previousBlockHash == null || previousBlockHash.length == 0) {
      throw new IllegalArgumentException("previous block hash is required");
    }
    if (height < 0 || chainId == null) {
      throw new IllegalArgumentException("invalid seed inputs");
    }
    final byte[] chainIdBytes = chainId.getBytes(StandardCharsets.UTF_8);
    final ByteBuffer heightBytes = ByteBuffer.allocate(Long.BYTES).putLong(height);
    final ByteBuffer chainLength = ByteBuffer.allocate(Integer.BYTES).putInt(chainIdBytes.length);
    return sha256(DOMAIN, previousBlockHash, heightBytes.array(), chainLength.array(), chainIdBytes);
  }

  static byte[] sha256(final byte[]... parts) {
    final Bytes[] encoded = new Bytes[parts.length];
    for (int i = 0; i < parts.length; i++) {
      encoded[i] = Bytes.wrap(parts[i]);
    }
    return Hash.sha256(Bytes.concatenate(encoded)).toArrayUnsafe();
  }
}
