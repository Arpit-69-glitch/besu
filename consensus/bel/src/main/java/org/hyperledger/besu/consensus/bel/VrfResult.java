package org.hyperledger.besu.consensus.bel;

/** Result of RFC 9381 proof verification. */
public record VrfResult(boolean valid, byte[] output) {
  public VrfResult(final boolean valid, final byte[] output) {
    this.valid = valid;
    this.output = output == null ? new byte[0] : output.clone();
  }
}
