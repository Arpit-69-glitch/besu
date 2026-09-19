package org.hyperledger.besu.consensus.bel;

import java.nio.charset.StandardCharsets;
import org.hyperledger.besu.crypto.Hash;
import org.apache.tuweni.bytes.Bytes;

/**
 * Test-only provider for deterministic consensus demonstrations.
 *
 * <p>This is not RFC 9381 cryptography and is not production-grade. Test keys are deliberately
 * represented by the same deterministic identity bytes on the prove and verify sides so that the
 * consensus protocol can be exercised without selecting an unvalidated cryptographic backend.
 */
public final class DeterministicTestVrfProvider implements VrfProvider {
  private static final byte[] OUTPUT_DOMAIN =
      "BEL-TEST-ONLY-TICKET".getBytes(StandardCharsets.UTF_8);
  private static final byte[] PROOF_DOMAIN =
      "BEL-TEST-ONLY-PROOF".getBytes(StandardCharsets.UTF_8);

  @Override
  public VrfProof prove(final VrfPrivateKey privateKey, final byte[] message) {
    final byte[] key = requireKey(privateKey == null ? null : privateKey.encoded());
    final byte[] input = requireMessage(message);
    return new VrfProof(digest(OUTPUT_DOMAIN, key, input), digest(PROOF_DOMAIN, key, input));
  }

  @Override
  public VrfResult verify(
      final VrfPublicKey publicKey, final byte[] message, final VrfProof proof) {
    if (publicKey == null || proof == null) {
      return new VrfResult(false, new byte[0]);
    }
    final byte[] key = requireKey(publicKey.encoded());
    final byte[] input = requireMessage(message);
    final byte[] expectedOutput = digest(OUTPUT_DOMAIN, key, input);
    final byte[] expectedProof = digest(PROOF_DOMAIN, key, input);
    final boolean valid = constantTimeEquals(expectedOutput, proof.output())
        && constantTimeEquals(expectedProof, proof.proof());
    return new VrfResult(valid, valid ? expectedOutput : new byte[0]);
  }

  private static byte[] requireKey(final byte[] key) {
    if (key == null || key.length == 0) {
      throw new IllegalArgumentException("test provider key must not be empty");
    }
    return key.clone();
  }

  private static byte[] requireMessage(final byte[] message) {
    if (message == null) {
      throw new IllegalArgumentException("test provider message must not be null");
    }
    return message.clone();
  }

  private static byte[] digest(final byte[] domain, final byte[] key, final byte[] message) {
    return Hash.sha256(
            Bytes.concatenate(
                Bytes.wrap(domain),
                Bytes.of(0),
                Bytes.wrap(key),
                Bytes.of(0),
                Bytes.wrap(message)))
        .toArrayUnsafe();
  }

  private static boolean constantTimeEquals(final byte[] left, final byte[] right) {
    if (left == null || right == null || left.length != right.length) {
      return false;
    }
    int difference = 0;
    for (int i = 0; i < left.length; i++) {
      difference |= left[i] ^ right[i];
    }
    return difference == 0;
  }
}
