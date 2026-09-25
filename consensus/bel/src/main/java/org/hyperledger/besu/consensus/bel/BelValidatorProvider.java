package org.hyperledger.besu.consensus.bel;

import org.hyperledger.besu.consensus.common.validator.CommitteeProvider;
import org.hyperledger.besu.consensus.common.validator.ValidatorProvider;
import org.hyperledger.besu.consensus.common.validator.VoteProvider;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.apache.tuweni.bytes.Bytes;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Comparator;

/**
 * BEL's consensus-facing validator view.
 *
 * <p>The delegated provider remains the source of the active validator population. BEL derives a
 * committee for each height and exposes that committee to Besu's existing QBFT state machine.
 * Consequently QBFT networking, message signatures, round changes, quorum accounting, commit
 * seals, and block import all use one consistent committee view.
 *
 * <p>The deterministic provider is intentionally injected and is only suitable for the hackathon
 * demonstration. It is not RFC 9381 cryptography and must not be used as production security.
 */
public final class BelValidatorProvider implements ValidatorProvider, CommitteeProvider {
  private static final String PROTOTYPE_PROFILE = "prototype";
  private static final int PROTOTYPE_COMMITTEE_SIZE = 4;
  private final ValidatorProvider delegate;
  private final Blockchain blockchain;
  private final String chainId;
  private final VrfProvider vrfProvider;
  private final int minimumCommitteeSize;

  public BelValidatorProvider(
      final ValidatorProvider delegate,
      final Blockchain blockchain,
      final BigInteger chainId,
      final VrfProvider vrfProvider) {
    this.delegate = delegate;
    this.blockchain = blockchain;
    this.chainId = chainId == null ? "0" : chainId.toString();
    this.vrfProvider = vrfProvider;
    this.minimumCommitteeSize =
        PROTOTYPE_PROFILE.equalsIgnoreCase(System.getProperty("bel.execution.profile", "production"))
            ? PROTOTYPE_COMMITTEE_SIZE
            : BelCommitteeSelector.MINIMUM_COMMITTEE_SIZE;
  }

  @Override
  public Collection<Address> getValidatorsAtHead() {
    return getValidatorsAfterBlock(blockchain.getChainHeadHeader());
  }

  @Override
  public Collection<Address> getValidatorsAfterBlock(final BlockHeader parentHeader) {
    return committeeForBlock(parentHeader, parentHeader.getNumber() + 1);
  }

  @Override
  public Collection<Address> getValidatorsForBlock(final BlockHeader header) {
    final BlockHeader parent =
        header.getNumber() == 0
            ? header
            : blockchain.getBlockHeader(header.getNumber() - 1).orElse(header);
    return committeeForBlock(parent, header.getNumber());
  }

  String chainId() {
    return chainId;
  }

  @Override
  public Collection<Address> getCommitteeForBlock(final BlockHeader header) {
    return getValidatorsForBlock(header);
  }

  public List<Address> committeeForBlock(final BlockHeader parentHeader, final long height) {
    return select(parentHeader, height);
  }

  @Override
  public Optional<VoteProvider> getVoteProviderAtHead() {
    return delegate.getVoteProviderAtHead();
  }

  @Override
  public Optional<VoteProvider> getVoteProviderAfterBlock(final BlockHeader header) {
    return delegate.getVoteProviderAfterBlock(header);
  }

  private List<Address> select(final BlockHeader parentHeader, final long height) {
    final List<Address> population =
        new ArrayList<>(delegate.getValidatorsAfterBlock(parentHeader));

    if (population.size() < minimumCommitteeSize) {
      throw new IllegalStateException(
          minimumCommitteeSize == BelCommitteeSelector.MINIMUM_COMMITTEE_SIZE
              ? "BEL requires at least 70 active validators"
              : "BEL prototype profile requires at least 4 active validators");
    }

    final byte[] seed =
        BelSeed.derive(parentHeader.getHash().toArray(), height, chainId);
    final List<BelCommitteeEvidence.Candidate> evidence =
        population.stream()
            .map(
                address -> {
                  final byte[] id = address.toArray();
                  final VrfProof proof =
                      vrfProvider.prove(
                          new VrfPrivateKey(id), BelCommitteeEvidence.committeeInput(seed, height, id));
                  return new BelCommitteeEvidence.Candidate(id, new VrfPublicKey(id), proof);
                })
            .toList();

    final List<Address> committee =
    BelCommitteeEvidence.verifyAndSelect(
            population.size(), seed, height, evidence, vrfProvider, minimumCommitteeSize)
        .stream()
        .map(ticket -> Address.wrap(Bytes.wrap(ticket.validatorId())))
        .sorted(Comparator.comparing(Address::toString))
        .toList();

return committee;
  }
}
