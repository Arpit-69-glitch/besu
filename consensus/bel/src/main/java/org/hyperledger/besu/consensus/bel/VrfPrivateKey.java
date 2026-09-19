package org.hyperledger.besu.consensus.bel;

/** Opaque VRF private-key material. Consensus code never serializes this value. */
public record VrfPrivateKey(byte[] encoded) {
  public VrfPrivateKey(final byte[] encoded) {
    this.encoded = encoded.clone();
  }
}
