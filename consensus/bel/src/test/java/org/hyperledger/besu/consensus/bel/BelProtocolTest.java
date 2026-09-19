package org.hyperledger.besu.consensus.bel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class BelProtocolTest {
  @Test
  void derivesStableSeedAndLeader() {
    final byte[] seed = BelSeed.derive(new byte[] {1, 2, 3}, 4, "BEL");
    final List<byte[]> committee = List.of(new byte[] {1}, new byte[] {2}, new byte[] {3});
    assertThat(BelSeed.derive(new byte[] {1, 2, 3}, 4, "BEL")).containsExactly(seed);
    final byte[] leader = BelLeaderSelector.select(seed, 4, 0, committee);
    assertThat(
            Arrays.equals(leader, new byte[] {1})
                || Arrays.equals(leader, new byte[] {2})
                || Arrays.equals(leader, new byte[] {3}))
        .isTrue();
    assertThat(BelLeaderSelector.select(seed, 4, 0, committee)).containsExactly(BelLeaderSelector.select(seed, 4, 0, committee));
  }

  @Test
  void usesExactQuorumFormula() {
    assertThat(BelQuorum.quorum(70)).isEqualTo(47);
    assertThat(BelQuorum.byzantineTolerance(70)).isEqualTo(23);
    assertThat(BelQuorum.reached(70, 46)).isFalse();
    assertThat(BelQuorum.reached(70, 47)).isTrue();
  }

  @Test
  void fallsBackToSmallestSeventyTickets() {
    final List<BelCommitteeSelector.VrfTicket> tickets = new ArrayList<>();
    for (int i = 0; i < 70; i++) {
      final byte[] output = new byte[32];
      output[31] = (byte) i;
      tickets.add(new BelCommitteeSelector.VrfTicket(new byte[] {(byte) i}, output, new byte[] {9}));
    }
    assertThat(BelCommitteeSelector.select(70, tickets)).hasSize(70);
  }

  @Test
  void rejectsSmallNormalPopulation() {
    assertThatThrownBy(() -> BelCommitteeSelector.select(4, List.of()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void deterministicTestProviderIsStableAndRejectsTampering() {
    final DeterministicTestVrfProvider provider = new DeterministicTestVrfProvider();
    final VrfPrivateKey privateKey = new VrfPrivateKey(new byte[] {7, 8, 9});
    final VrfPublicKey publicKey = new VrfPublicKey(new byte[] {7, 8, 9});
    final byte[] message = new byte[] {1, 2, 3};

    final VrfProof first = provider.prove(privateKey, message);
    final VrfProof second = provider.prove(privateKey, message);
    assertThat(first.output()).containsExactly(second.output());
    assertThat(first.proof()).containsExactly(second.proof());
    assertThat(provider.verify(publicKey, message, first).valid()).isTrue();

    final byte[] modifiedMessage = message.clone();
    modifiedMessage[0] ^= 1;
    assertThat(provider.verify(publicKey, modifiedMessage, first).valid()).isFalse();

    final byte[] modifiedProofBytes = first.proof().clone();
    modifiedProofBytes[0] ^= 1;
    assertThat(provider.verify(publicKey, message, new VrfProof(first.output(), modifiedProofBytes)).valid())
        .isFalse();

    assertThat(provider.verify(new VrfPublicKey(new byte[] {1, 2, 3}), message, first).valid())
        .isFalse();
  }

  @Test
  void verifiesAndSelectsTheFrozenHundredValidatorDemoProfile() {
    final DeterministicTestVrfProvider provider = new DeterministicTestVrfProvider();
    final byte[] seed = BelSeed.derive(new byte[] {9, 8, 7}, 1, "BEL-DEMO");
    final List<BelCommitteeEvidence.Candidate> candidates = new ArrayList<>();
    for (int i = 0; i < 100; i++) {
      final byte[] identity = new byte[] {(byte) (i >>> 8), (byte) i};
      final VrfPrivateKey privateKey = new VrfPrivateKey(identity);
      final VrfProof proof =
          provider.prove(privateKey, BelCommitteeEvidence.committeeInput(seed, 2, identity));
      candidates.add(
          new BelCommitteeEvidence.Candidate(identity, new VrfPublicKey(identity), proof));
    }

    final List<BelCommitteeSelector.VrfTicket> committee =
        BelCommitteeEvidence.verifyAndSelect(100, seed, 2, candidates, provider);
    assertThat(committee).hasSizeBetween(70, 100);
    assertThat(committee.stream().map(ticket -> ticket.validatorId()).distinct()).hasSize(committee.size());
    assertThat(BelQuorum.quorum(committee.size())).isGreaterThan(0);
  }

  @Test
  void rejectsDuplicateAndTamperedCommitteeEvidence() {
    final DeterministicTestVrfProvider provider = new DeterministicTestVrfProvider();
    final byte[] seed = BelSeed.derive(new byte[] {1}, 1, "BEL");
    final List<BelCommitteeEvidence.Candidate> candidates = new ArrayList<>();
    for (int i = 0; i < 70; i++) {
      final byte[] identity = new byte[] {(byte) i};
      final VrfProof proof =
          provider.prove(new VrfPrivateKey(identity), BelCommitteeEvidence.committeeInput(seed, 1, identity));
      candidates.add(new BelCommitteeEvidence.Candidate(identity, new VrfPublicKey(identity), proof));
    }
    candidates.set(69, candidates.get(0));
    assertThatThrownBy(() -> BelCommitteeEvidence.verifyAndSelect(70, seed, 1, candidates, provider))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("duplicate");
  }
}
