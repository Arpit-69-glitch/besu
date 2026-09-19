package org.hyperledger.besu.consensus.bel;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Deterministic committee selection over independently verified VRF tickets. */
public final class BelCommitteeSelector {
  public static final int MINIMUM_COMMITTEE_SIZE = 70;
  public static final int MINIMUM_TARGET_NUMERATOR = 33;
  public static final int MINIMUM_TARGET_DENOMINATOR = 2500; // 0.0132
  private static final BigInteger TWO_256 = BigInteger.ONE.shiftLeft(256);

  public record VrfTicket(byte[] validatorId, byte[] output, byte[] proof) {
    public VrfTicket(
        final byte[] validatorId, final byte[] output, final byte[] proof) {
      if (validatorId == null || validatorId.length == 0 || output == null || output.length != 32) {
        throw new IllegalArgumentException("validator id and 32-byte VRF output are required");
      }
      this.validatorId = validatorId.clone();
      this.output = output.clone();
      this.proof = proof == null ? new byte[0] : proof.clone();
    }
  }

  private BelCommitteeSelector() {}

  public static List<VrfTicket> select(final int validatorPopulation, final List<VrfTicket> tickets) {
    if (validatorPopulation < MINIMUM_COMMITTEE_SIZE) {
      throw new IllegalArgumentException("normal BEL deployment requires N >= 70");
    }
    if (tickets == null || tickets.size() != validatorPopulation) {
      throw new IllegalArgumentException("one verified ticket is required per active validator");
    }
    final List<VrfTicket> ordered = new ArrayList<>(tickets);
    ordered.sort(BelCommitteeSelector::compareTickets);
    final List<VrfTicket> selected = new ArrayList<>();
    final long[] probability = probability(validatorPopulation);
    for (final VrfTicket ticket : tickets) {
      if (belowThreshold(ticket.output(), probability[0], probability[1])) {
        selected.add(ticket);
      }
    }
    final boolean fallback = selected.size() < MINIMUM_COMMITTEE_SIZE;
    final List<VrfTicket> result = fallback
        ? new ArrayList<>(ordered.subList(0, MINIMUM_COMMITTEE_SIZE))
        : selected;
    result.sort(BelCommitteeSelector::compareTickets);
    return List.copyOf(result);
  }

  private static long[] probability(final int n) {
    if (n <= 70) {
      return new long[] {1, 1};
    }
    if (70L * MINIMUM_TARGET_DENOMINATOR >= (long) MINIMUM_TARGET_NUMERATOR * n) {
      return new long[] {70, n};
    }
    return new long[] {MINIMUM_TARGET_NUMERATOR, MINIMUM_TARGET_DENOMINATOR};
  }

  private static boolean belowThreshold(final byte[] output, final long numerator, final long denominator) {
    final BigInteger value = new BigInteger(1, output);
    return value.multiply(BigInteger.valueOf(denominator)).compareTo(TWO_256.multiply(BigInteger.valueOf(numerator))) < 0;
  }

  private static int compareTickets(final VrfTicket left, final VrfTicket right) {
    int result = compareUnsigned(left.output(), right.output());
    return result == 0 ? compareUnsigned(left.validatorId(), right.validatorId()) : result;
  }

  private static int compareUnsigned(final byte[] left, final byte[] right) {
    for (int i = 0; i < Math.min(left.length, right.length); i++) {
      int result = Integer.compare(Byte.toUnsignedInt(left[i]), Byte.toUnsignedInt(right[i]));
      if (result != 0) return result;
    }
    return Integer.compare(left.length, right.length);
  }
}
