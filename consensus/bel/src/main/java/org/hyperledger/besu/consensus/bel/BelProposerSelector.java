package org.hyperledger.besu.consensus.bel;

import org.hyperledger.besu.consensus.common.bft.ConsensusRoundIdentifier;
import org.hyperledger.besu.consensus.common.bft.blockcreation.ProposerSelector;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.apache.tuweni.bytes.Bytes;

import java.util.List;

/** Besu proposer selector backed by BEL's deterministic seed/committee/leader rules. */
public final class BelProposerSelector extends ProposerSelector {
  private final Blockchain blockchain;
  private final BelValidatorProvider validatorProvider;

  public BelProposerSelector(
      final Blockchain blockchain, final BelValidatorProvider validatorProvider) {
    super(blockchain, null, false, validatorProvider);
    this.blockchain = blockchain;
    this.validatorProvider = validatorProvider;
  }

  @Override
  public Address selectProposerForRound(final ConsensusRoundIdentifier roundIdentifier) {
    if (roundIdentifier.getRoundNumber() < 0 || roundIdentifier.getSequenceNumber() <= 0) {
      throw new IllegalArgumentException("invalid BEL round identifier");
    }
    final long height = roundIdentifier.getSequenceNumber();
    final BlockHeader parent =
        blockchain
            .getBlockHeader(height - 1)
            .orElseThrow(() -> new IllegalStateException("missing BEL parent header"));
    final byte[] seed =
        BelSeed.derive(parent.getHash().toArray(), height, validatorProvider.chainId());
    final List<Address> committee = validatorProvider.committeeForBlock(parent, height);
    final List<byte[]> canonicalCommittee =
        committee.stream().map(Address::toArray).toList();
    final Address selectedProposer =
        Address.wrap(
            Bytes.wrap(
                BelLeaderSelector.select(
                    seed,
                    height,
                    roundIdentifier.getRoundNumber(),
                    canonicalCommittee)));

    return selectedProposer;
  }
}
