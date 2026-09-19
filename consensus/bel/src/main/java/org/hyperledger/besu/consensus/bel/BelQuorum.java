package org.hyperledger.besu.consensus.bel;

/** Exact BEL BFT quorum arithmetic. */
public final class BelQuorum {
  private BelQuorum() {}

  public static int quorum(final int committeeSize) {
    if (committeeSize <= 0) {
      throw new IllegalArgumentException("committee size must be positive");
    }
    return (2 * committeeSize) / 3 + 1;
  }

  public static int byzantineTolerance(final int committeeSize) {
    if (committeeSize <= 0) {
      throw new IllegalArgumentException("committee size must be positive");
    }
    return (committeeSize - 1) / 3;
  }

  public static boolean reached(final int committeeSize, final int distinctValidVotes) {
    return distinctValidVotes >= quorum(committeeSize);
  }
}
