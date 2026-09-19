package org.hyperledger.besu.consensus.bel;

/** RFC 9381 proof plus the output claimed by the prover. */
public record VrfProof(byte[] output, byte[] proof) {
  public VrfProof(final byte[] output, final byte[] proof) {
    this.output = output.clone();
    this.proof = proof.clone();
  }
}
