/*
 * Copyright 2026 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package org.hyperledger.besu.consensus.qbft.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.consensus.common.bft.BftContextBuilder.setupContextWithValidators;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.consensus.common.bft.BftContext;
import org.hyperledger.besu.consensus.common.bft.ConsensusRoundIdentifier;
import org.hyperledger.besu.consensus.common.bft.headervalidationrules.BftCommitSealsValidationRule;
import org.hyperledger.besu.consensus.qbft.messagewrappers.Commit;
import org.hyperledger.besu.consensus.qbft.messagewrappers.Prepare;
import org.hyperledger.besu.crypto.SECPSignature;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.chain.BadBlockManager;
import org.hyperledger.besu.ethereum.core.BlockHeader;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Besu integration-level Byzantine/equivocation validation tests.
 *
 * <p>These tests exercise the same QBFT validators used after messages enter
 * {@code BaseBftController}. They are not live peer-to-peer Byzantine tests.
 */
public class ByzantineEvidenceValidationTest {

  private static final int VALIDATOR_COUNT = 4;
  private static final ConsensusRoundIdentifier ROUND = new ConsensusRoundIdentifier(1, 0);
  private static final Hash BLOCK_A = Hash.fromHexStringLenient("0x1");
  private static final Hash BLOCK_B = Hash.fromHexStringLenient("0x2");

  private final QbftNodeList validators = QbftNodeList.createNodes(VALIDATOR_COUNT);

  @Test
  void conflictingPrepareFromOneValidatorIsRejectedAndCannotReachQuorum() {
    final PrepareValidator validator =
        new PrepareValidator(validators.getNodeAddresses(), ROUND, BLOCK_A);
    final Prepare prepareA = validators.getMessageFactory(0).createPrepare(ROUND, BLOCK_A);
    final Prepare conflictingPrepare =
        validators.getMessageFactory(0).createPrepare(ROUND, BLOCK_B);

    assertThat(validator.validate(prepareA)).isTrue();
    assertThat(validator.validate(conflictingPrepare)).isFalse();
    assertThat(List.of(prepareA, conflictingPrepare).stream().filter(validator::validate).count())
        .isLessThan(3L);
  }

  @Test
  void conflictingCommitAndInvalidSignatureAreRejectedAndCannotReachQuorum() {
    final CommitValidator validator =
        new CommitValidator(validators.getNodeAddresses(), ROUND, BLOCK_A, BLOCK_A);
    final SECPSignature validSeal = validators.getNode(0).getNodeKey().sign(BLOCK_A);
    final Commit commitA =
        validators.getMessageFactory(0).createCommit(ROUND, BLOCK_A, validSeal);
    final Commit conflictingCommit =
        validators.getMessageFactory(0).createCommit(ROUND, BLOCK_B, validSeal);
    final SECPSignature wrongAuthorSeal = validators.getNode(1).getNodeKey().sign(BLOCK_A);
    final Commit invalidSignatureCommit =
        validators.getMessageFactory(0).createCommit(ROUND, BLOCK_A, wrongAuthorSeal);

    assertThat(validator.validate(commitA)).isTrue();
    assertThat(validator.validate(conflictingCommit)).isFalse();
    assertThat(validator.validate(invalidSignatureCommit)).isFalse();
    assertThat(List.of(commitA, conflictingCommit, invalidSignatureCommit).stream()
            .filter(validator::validate)
            .count())
        .isLessThan(3L);
  }

  @Test
  void duplicateOrNonCommitteeCommitEvidenceCannotPassFinalityHeaderRule() {
    final BftContext bftContext = setupContextWithValidators(validators.getNodeAddresses());
    final ProtocolContext context =
        new ProtocolContext(null, null, bftContext, new BadBlockManager());
    final BlockHeader header = org.mockito.Mockito.mock(BlockHeader.class);
    final BftCommitSealsValidationRule finalityRule = new BftCommitSealsValidationRule();

    when(bftContext.getBlockInterface().getCommitters(any()))
        .thenReturn(
            List.of(
                validators.getNode(0).getAddress(),
                validators.getNode(0).getAddress(),
                validators.getNode(1).getAddress()));
    assertThat(finalityRule.validate(header, null, context)).isFalse();

    when(bftContext.getBlockInterface().getCommitters(any()))
        .thenReturn(List.of(org.hyperledger.besu.datatypes.Address.ZERO));
    assertThat(finalityRule.validate(header, null, context)).isFalse();
  }
}