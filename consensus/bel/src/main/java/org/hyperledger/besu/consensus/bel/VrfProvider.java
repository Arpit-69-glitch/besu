package org.hyperledger.besu.consensus.bel;

/**
 * Cryptographic boundary for the exact RFC 9381 ECVRF-P256-SHA256-SSWU suite.
 *
 * <p>There is intentionally no hash, signature, RNG, or alternate-suite fallback. The concrete
 * provider must be independently selected, vector-tested, and documented before consensus is
 * enabled.
 */
public interface VrfProvider {
  VrfProof prove(VrfPrivateKey privateKey, byte[] message);

  VrfResult verify(VrfPublicKey publicKey, byte[] message, VrfProof proof);
}
