package org.hyperledger.besu.consensus.bel;

/** Public RFC 9381 VRF key associated with one authorized validator. */
public record VrfPublicKey(byte[] encoded) {
  public VrfPublicKey(final byte[] encoded) {
    this.encoded = encoded.clone();
  }
}
