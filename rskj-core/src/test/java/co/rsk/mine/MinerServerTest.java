/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package co.rsk.mine;

import co.rsk.TestHelpers.Tx;
import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.params.RegTestParams;
import co.rsk.config.ConfigUtils;
import co.rsk.config.TestSystemProperties;
import co.rsk.core.Coin;
import co.rsk.core.DifficultyCalculator;
import co.rsk.core.bc.BlockExecutorFactory;
import co.rsk.crypto.Keccak256;
import co.rsk.db.StateRootHandler;
import co.rsk.remasc.RemascTransaction;
import co.rsk.validators.BlockUnclesValidationRule;
import co.rsk.validators.ProofOfWorkRule;
import org.ethereum.core.*;
import org.ethereum.db.BlockStore;
import org.ethereum.facade.EthereumImpl;
import org.ethereum.rpc.TypeConverter;
import org.ethereum.util.RskTestFactory;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.junit.Assert.*;

/**
 * Created by adrian.eidelman on 3/16/2016.
 */
public class MinerServerTest extends ParameterizedNetworkUpgradeTest {

    private final DifficultyCalculator difficultyCalculator;
    private Blockchain blockchain;
    private Repository repository;
    private StateRootHandler stateRootHandler;
    private BlockStore blockStore;
    private TransactionPool transactionPool;
    private BlockFactory blockFactory;
    private BlockExecutorFactory blockExecutorFactory;

    public MinerServerTest(TestSystemProperties config) {
        super(config);
        this.difficultyCalculator = new DifficultyCalculator(config.getActivationConfig(), config.getNetworkConstants());
    }

    @Before
    public void setUp() {
        RskTestFactory factory = new RskTestFactory(config) {
            @Override
            protected Repository buildRepository() {
                return Mockito.spy(super.buildRepository());
            }
        };
        blockchain = factory.getBlockchain();
        repository = factory.getRepository();
        stateRootHandler = factory.getStateRootHandler();
        blockStore = factory.getBlockStore();
        transactionPool = factory.getTransactionPool();
        blockFactory = factory.getBlockFactory();
        blockExecutorFactory = factory.getBlockExecutorFactory();
    }

    @Test
    public void buildBlockToMineCheckThatLastTransactionIsForREMASC() {
        Transaction tx1 = Tx.create(config, 0, 21000, 100, 0, 0, 0);
        byte[] s1 = new byte[32];
        s1[0] = 0;
        Mockito.when(tx1.getHash()).thenReturn(new Keccak256(s1));
        Mockito.when(tx1.getEncoded()).thenReturn(new byte[32]);

        Repository track = Mockito.mock(Repository.class);
        Mockito.doReturn(repository.getRoot()).when(track).getRoot();
        Mockito.doReturn(repository.getMutableTrie()).when(track).getMutableTrie();
        Mockito.when(track.getNonce(tx1.getSender())).thenReturn(BigInteger.ZERO);
        Mockito.when(track.getNonce(RemascTransaction.REMASC_ADDRESS)).thenReturn(BigInteger.ZERO);
        Mockito.when(track.getBalance(tx1.getSender())).thenReturn(Coin.valueOf(4200000L));
        Mockito.when(track.getBalance(RemascTransaction.REMASC_ADDRESS)).thenReturn(Coin.valueOf(4200000L));
        Mockito.doReturn(track).when(repository).getSnapshotTo(Mockito.any());
        Mockito.doReturn(track).when(repository).startTracking();
        Mockito.doReturn(track).when(track).startTracking();

        List<Transaction> txs = new ArrayList<>(Collections.singletonList(tx1));

        TransactionPool localTransactionPool = Mockito.mock(TransactionPool.class);
        Mockito.when(localTransactionPool.getPendingTransactions()).thenReturn(txs);

        BlockUnclesValidationRule unclesValidationRule = Mockito.mock(BlockUnclesValidationRule.class);
        Mockito.when(unclesValidationRule.isValid(Mockito.any())).thenReturn(true);
        MinerClock clock = new MinerClock(true, Clock.systemUTC());
        MinerServerImpl minerServer = new MinerServerImpl(
                config,
                Mockito.mock(EthereumImpl.class),
                this.blockchain,
                null,
                new ProofOfWorkRule(config).setFallbackMiningEnabled(false),
                new BlockToMineBuilder(
                        config.getActivationConfig(),
                        ConfigUtils.getDefaultMiningConfig(),
                        repository,
                        stateRootHandler,
                        blockStore,
                        localTransactionPool,
                        difficultyCalculator,
                        new GasLimitCalculator(config.getNetworkConstants()),
                        unclesValidationRule,
                        clock,
                        blockFactory,
                        blockExecutorFactory
                ),
                clock,
                blockFactory,
                ConfigUtils.getDefaultMiningConfig()
        );

        minerServer.buildBlockToMine(blockchain.getBestBlock(), false);
        Block blockAtHeightOne = minerServer.getBlocksWaitingforPoW().entrySet().iterator().next().getValue();

        List<Transaction> blockTransactions = blockAtHeightOne.getTransactionsList();
        assertNotNull(blockTransactions);
        assertEquals(2, blockTransactions.size());

        Transaction remascTransaction = blockTransactions.get(1);
        assertThat(remascTransaction, instanceOf(RemascTransaction.class));
    }

    @Test
    public void submitBitcoinBlockTwoTags() {
        EthereumImpl ethereumImpl = Mockito.mock(EthereumImpl.class);
        Mockito.when(ethereumImpl.addNewMinedBlock(Mockito.any())).thenReturn(ImportResult.IMPORTED_BEST);

        BlockUnclesValidationRule unclesValidationRule = Mockito.mock(BlockUnclesValidationRule.class);
        Mockito.when(unclesValidationRule.isValid(Mockito.any())).thenReturn(true);
        MinerClock clock = new MinerClock(true, Clock.systemUTC());
        MinerServer minerServer = new MinerServerImpl(
                config,
                ethereumImpl,
                blockchain,
                null,
                new ProofOfWorkRule(config).setFallbackMiningEnabled(false),
                new BlockToMineBuilder(
                        config.getActivationConfig(),
                        ConfigUtils.getDefaultMiningConfig(),
                        repository,
                        stateRootHandler,
                        blockStore,
                        transactionPool,
                        difficultyCalculator,
                        new GasLimitCalculator(config.getNetworkConstants()),
                        unclesValidationRule,
                        clock,
                        blockFactory,
                        blockExecutorFactory
                ),
                clock,
                blockFactory,
                ConfigUtils.getDefaultMiningConfig()
        );
        try {
        byte[] extraData = ByteBuffer.allocate(4).putInt(1).array();
        minerServer.setExtraData(extraData);
        minerServer.start();
        MinerWork work = minerServer.getWork();
        Block bestBlock = blockchain.getBestBlock();

        extraData = ByteBuffer.allocate(4).putInt(2).array();
        minerServer.setExtraData(extraData);
        minerServer.buildBlockToMine(bestBlock, false);
        MinerWork work2 = minerServer.getWork(); // only the tag is used
        Assert.assertNotEquals(work2.getBlockHashForMergedMining(),work.getBlockHashForMergedMining());

        BtcBlock bitcoinMergedMiningBlock = getMergedMiningBlockWithTwoTags(work,work2);

        findNonce(work, bitcoinMergedMiningBlock);
        SubmitBlockResult result;
        result = ((MinerServerImpl) minerServer).submitBitcoinBlock(work2.getBlockHashForMergedMining(), bitcoinMergedMiningBlock,true);


        Assert.assertEquals("OK", result.getStatus());
        Assert.assertNotNull(result.getBlockInfo());
        Assert.assertEquals("0x1", result.getBlockInfo().getBlockIncludedHeight());
        Assert.assertEquals("0x494d504f525445445f42455354", result.getBlockInfo().getBlockImportedResult());

        // Submit again the save PoW for a different header
        result = ((MinerServerImpl) minerServer).submitBitcoinBlock(work.getBlockHashForMergedMining(), bitcoinMergedMiningBlock,false);

        Assert.assertEquals("ERROR", result.getStatus());

        Mockito.verify(ethereumImpl, Mockito.times(1)).addNewMinedBlock(Mockito.any());
        } finally {
            minerServer.stop();
        }
    }

    @Test
    public void submitBitcoinBlock() {
        EthereumImpl ethereumImpl = Mockito.mock(EthereumImpl.class);
        Mockito.when(ethereumImpl.addNewMinedBlock(Mockito.any())).thenReturn(ImportResult.IMPORTED_BEST);

        BlockUnclesValidationRule unclesValidationRule = Mockito.mock(BlockUnclesValidationRule.class);
        Mockito.when(unclesValidationRule.isValid(Mockito.any())).thenReturn(true);
        MinerClock clock = new MinerClock(true, Clock.systemUTC());
        MinerServer minerServer = new MinerServerImpl(
                config,
                ethereumImpl,
                blockchain,
                null,
                new ProofOfWorkRule(config).setFallbackMiningEnabled(false),
                new BlockToMineBuilder(
                        config.getActivationConfig(),
                        ConfigUtils.getDefaultMiningConfig(),
                        repository,
                        stateRootHandler,
                        blockStore,
                        transactionPool,
                        difficultyCalculator,
                        new GasLimitCalculator(config.getNetworkConstants()),
                        unclesValidationRule,
                        clock,
                        blockFactory,
                        blockExecutorFactory
                ),
                clock,
                blockFactory,
                ConfigUtils.getDefaultMiningConfig()
        );
        try {
            minerServer.start();
            MinerWork work = minerServer.getWork();

            BtcBlock bitcoinMergedMiningBlock = getMergedMiningBlockWithOnlyCoinbase(work);

            findNonce(work, bitcoinMergedMiningBlock);

            SubmitBlockResult result = minerServer.submitBitcoinBlock(work.getBlockHashForMergedMining(), bitcoinMergedMiningBlock);

            Assert.assertEquals("OK", result.getStatus());
            Assert.assertNotNull(result.getBlockInfo());
            Assert.assertEquals("0x1", result.getBlockInfo().getBlockIncludedHeight());
            Assert.assertEquals("0x494d504f525445445f42455354", result.getBlockInfo().getBlockImportedResult());

            Mockito.verify(ethereumImpl, Mockito.times(1)).addNewMinedBlock(Mockito.any());
        } finally {
            minerServer.stop();
        }
    }

    @Test
    public void submitBitcoinBlockPartialMerkleWhenBlockIsEmpty() {
        EthereumImpl ethereumImpl = Mockito.mock(EthereumImpl.class);
        Mockito.when(ethereumImpl.addNewMinedBlock(Mockito.any())).thenReturn(ImportResult.IMPORTED_BEST);

        BlockUnclesValidationRule unclesValidationRule = Mockito.mock(BlockUnclesValidationRule.class);
        Mockito.when(unclesValidationRule.isValid(Mockito.any())).thenReturn(true);
        MinerClock clock = new MinerClock(true, Clock.systemUTC());
        MinerServer minerServer = new MinerServerImpl(
                config,
                ethereumImpl,
                blockchain,
                null,
                new ProofOfWorkRule(config).setFallbackMiningEnabled(false),
                new BlockToMineBuilder(
                        config.getActivationConfig(),
                        ConfigUtils.getDefaultMiningConfig(),
                        repository,
                        stateRootHandler,
                        blockStore,
                        transactionPool,
                        difficultyCalculator,
                        new GasLimitCalculator(config.getNetworkConstants()),
                        unclesValidationRule,
                        clock,
                        blockFactory,
                        blockExecutorFactory
                ),
                clock,
                blockFactory,
                ConfigUtils.getDefaultMiningConfig()
        );
        try {
            minerServer.start();
            MinerWork work = minerServer.getWork();

            BtcBlock bitcoinMergedMiningBlock = getMergedMiningBlockWithOnlyCoinbase(work);

            findNonce(work, bitcoinMergedMiningBlock);

            //noinspection ConstantConditions
            BtcTransaction coinbase = bitcoinMergedMiningBlock.getTransactions().get(0);
            List<String> coinbaseReversedHash = Collections.singletonList(Sha256Hash.wrap(coinbase.getHash().getReversedBytes()).toString());
            SubmitBlockResult result = minerServer.submitBitcoinBlockPartialMerkle(work.getBlockHashForMergedMining(), bitcoinMergedMiningBlock, coinbase, coinbaseReversedHash, 1);

            Assert.assertEquals("OK", result.getStatus());
            Assert.assertNotNull(result.getBlockInfo());
            Assert.assertEquals("0x1", result.getBlockInfo().getBlockIncludedHeight());
            Assert.assertEquals("0x494d504f525445445f42455354", result.getBlockInfo().getBlockImportedResult());

            Mockito.verify(ethereumImpl, Mockito.times(1)).addNewMinedBlock(Mockito.any());
        } finally {
            minerServer.stop();
        }
    }

    @Test
    public void submitBitcoinBlockPartialMerkleWhenBlockHasTransactions() {
        EthereumImpl ethereumImpl = Mockito.mock(EthereumImpl.class);
        Mockito.when(ethereumImpl.addNewMinedBlock(Mockito.any())).thenReturn(ImportResult.IMPORTED_BEST);

        BlockUnclesValidationRule unclesValidationRule = Mockito.mock(BlockUnclesValidationRule.class);
        Mockito.when(unclesValidationRule.isValid(Mockito.any())).thenReturn(true);
        MinerClock clock = new MinerClock(true, Clock.systemUTC());
        MinerServer minerServer = new MinerServerImpl(
                config,
                ethereumImpl,
                blockchain,
                null,
                new ProofOfWorkRule(config).setFallbackMiningEnabled(false),
                new BlockToMineBuilder(
                        config.getActivationConfig(),
                        ConfigUtils.getDefaultMiningConfig(),
                        repository,
                        stateRootHandler,
                        blockStore,
                        transactionPool,
                        difficultyCalculator,
                        new GasLimitCalculator(config.getNetworkConstants()),
                        unclesValidationRule,
                        clock,
                        blockFactory,
                        blockExecutorFactory
                ),
                clock,
                blockFactory,
                ConfigUtils.getDefaultMiningConfig()
        );
        try {
            minerServer.start();
            MinerWork work = minerServer.getWork();

            BtcTransaction otherTx = Mockito.mock(BtcTransaction.class);
            Sha256Hash otherTxHash = Sha256Hash.wrap("aaaabbbbccccddddaaaabbbbccccddddaaaabbbbccccddddaaaabbbbccccdddd");
            Mockito.when(otherTx.getHash()).thenReturn(otherTxHash);
            Mockito.when(otherTx.getHashAsString()).thenReturn(otherTxHash.toString());

            BtcBlock bitcoinMergedMiningBlock = getMergedMiningBlock(work, Collections.singletonList(otherTx));

            findNonce(work, bitcoinMergedMiningBlock);

            //noinspection ConstantConditions
            BtcTransaction coinbase = bitcoinMergedMiningBlock.getTransactions().get(0);
            String coinbaseReversedHash = Sha256Hash.wrap(coinbase.getHash().getReversedBytes()).toString();
            String otherTxHashReversed = Sha256Hash.wrap(otherTxHash.getReversedBytes()).toString();
            List<String> merkleHashes = Arrays.asList(coinbaseReversedHash, otherTxHashReversed);
            SubmitBlockResult result = minerServer.submitBitcoinBlockPartialMerkle(work.getBlockHashForMergedMining(), bitcoinMergedMiningBlock, coinbase, merkleHashes, 2);

            Assert.assertEquals("OK", result.getStatus());
            Assert.assertNotNull(result.getBlockInfo());
            Assert.assertEquals("0x1", result.getBlockInfo().getBlockIncludedHeight());
            Assert.assertEquals("0x494d504f525445445f42455354", result.getBlockInfo().getBlockImportedResult());

            Mockito.verify(ethereumImpl, Mockito.times(1)).addNewMinedBlock(Mockito.any());
        } finally {
            minerServer.stop();
        }
    }

    @Test
    public void submitBitcoinBlockTransactionsWhenBlockIsEmpty() {
        EthereumImpl ethereumImpl = Mockito.mock(EthereumImpl.class);
        Mockito.when(ethereumImpl.addNewMinedBlock(Mockito.any())).thenReturn(ImportResult.IMPORTED_BEST);

        BlockUnclesValidationRule unclesValidationRule = Mockito.mock(BlockUnclesValidationRule.class);
        Mockito.when(unclesValidationRule.isValid(Mockito.any())).thenReturn(true);
        MinerClock clock = new MinerClock(true, Clock.systemUTC());
        MinerServer minerServer = new MinerServerImpl(
                config,
                ethereumImpl,
                blockchain,
                null,
                new ProofOfWorkRule(config).setFallbackMiningEnabled(false),
                new BlockToMineBuilder(
                        config.getActivationConfig(),
                        ConfigUtils.getDefaultMiningConfig(),
                        repository,
                        stateRootHandler,
                        blockStore,
                        transactionPool,
                        difficultyCalculator,
                        new GasLimitCalculator(config.getNetworkConstants()),
                        unclesValidationRule,
                        clock,
                        blockFactory,
                        blockExecutorFactory
                ),
                clock,
                blockFactory,
                ConfigUtils.getDefaultMiningConfig()
        );
        try {
            minerServer.start();
            MinerWork work = minerServer.getWork();

            BtcBlock bitcoinMergedMiningBlock = getMergedMiningBlockWithOnlyCoinbase(work);

            findNonce(work, bitcoinMergedMiningBlock);

            //noinspection ConstantConditions
            BtcTransaction coinbase = bitcoinMergedMiningBlock.getTransactions().get(0);
            SubmitBlockResult result = minerServer.submitBitcoinBlockTransactions(work.getBlockHashForMergedMining(), bitcoinMergedMiningBlock, coinbase, Collections.singletonList(coinbase.getHashAsString()));

            Assert.assertEquals("OK", result.getStatus());
            Assert.assertNotNull(result.getBlockInfo());
            Assert.assertEquals("0x1", result.getBlockInfo().getBlockIncludedHeight());
            Assert.assertEquals("0x494d504f525445445f42455354", result.getBlockInfo().getBlockImportedResult());

            Mockito.verify(ethereumImpl, Mockito.times(1)).addNewMinedBlock(Mockito.any());
        } finally {
            minerServer.stop();
        }
    }

    @Test
    public void submitBitcoinBlockTransactionsWhenBlockHasTransactions() {
        EthereumImpl ethereumImpl = Mockito.mock(EthereumImpl.class);
        Mockito.when(ethereumImpl.addNewMinedBlock(Mockito.any())).thenReturn(ImportResult.IMPORTED_BEST);

        BlockUnclesValidationRule unclesValidationRule = Mockito.mock(BlockUnclesValidationRule.class);
        Mockito.when(unclesValidationRule.isValid(Mockito.any())).thenReturn(true);
        MinerClock clock = new MinerClock(true, Clock.systemUTC());
        MinerServer minerServer = new MinerServerImpl(
                config,
                ethereumImpl,
                blockchain,
                null,
                new ProofOfWorkRule(config).setFallbackMiningEnabled(false),
                new BlockToMineBuilder(
                        config.getActivationConfig(),
                        ConfigUtils.getDefaultMiningConfig(),
                        repository,
                        stateRootHandler,
                        blockStore,
                        transactionPool,
                        difficultyCalculator,
                        new GasLimitCalculator(config.getNetworkConstants()),
                        unclesValidationRule,
                        clock,
                        blockFactory,
                        blockExecutorFactory
                ),
                clock,
                blockFactory,
                ConfigUtils.getDefaultMiningConfig()
        );
        try {
            minerServer.start();
            MinerWork work = minerServer.getWork();

            BtcTransaction otherTx = Mockito.mock(BtcTransaction.class);
            Sha256Hash otherTxHash = Sha256Hash.wrap("aaaabbbbccccddddaaaabbbbccccddddaaaabbbbccccddddaaaabbbbccccdddd");
            Mockito.when(otherTx.getHash()).thenReturn(otherTxHash);
            Mockito.when(otherTx.getHashAsString()).thenReturn(otherTxHash.toString());

            BtcBlock bitcoinMergedMiningBlock = getMergedMiningBlock(work, Collections.singletonList(otherTx));

            findNonce(work, bitcoinMergedMiningBlock);

            //noinspection ConstantConditions
            BtcTransaction coinbase = bitcoinMergedMiningBlock.getTransactions().get(0);
            List<String> txs = Arrays.asList(coinbase.getHashAsString(), otherTxHash.toString());
            SubmitBlockResult result = minerServer.submitBitcoinBlockTransactions(work.getBlockHashForMergedMining(), bitcoinMergedMiningBlock, coinbase, txs);

            Assert.assertEquals("OK", result.getStatus());
            Assert.assertNotNull(result.getBlockInfo());
            Assert.assertEquals("0x1", result.getBlockInfo().getBlockIncludedHeight());
            Assert.assertEquals("0x494d504f525445445f42455354", result.getBlockInfo().getBlockImportedResult());

            Mockito.verify(ethereumImpl, Mockito.times(1)).addNewMinedBlock(Mockito.any());
        } finally {
            minerServer.stop();
        }
    }

    @Test
    public void workWithNoTransactionsZeroFees() {
        EthereumImpl ethereumImpl = Mockito.mock(EthereumImpl.class);

        BlockUnclesValidationRule unclesValidationRule = Mockito.mock(BlockUnclesValidationRule.class);
        Mockito.when(unclesValidationRule.isValid(Mockito.any())).thenReturn(true);
        MinerClock clock = new MinerClock(true, Clock.systemUTC());
        MinerServer minerServer = new MinerServerImpl(
                config,
                ethereumImpl,
                this.blockchain,
                null,
                new ProofOfWorkRule(config).setFallbackMiningEnabled(false),
                new BlockToMineBuilder(
                        config.getActivationConfig(),
                        ConfigUtils.getDefaultMiningConfig(),
                        repository,
                        stateRootHandler,
                        blockStore,
                        transactionPool,
                        difficultyCalculator,
                        new GasLimitCalculator(config.getNetworkConstants()),
                        unclesValidationRule,
                        clock,
                        blockFactory,
                        blockExecutorFactory
                ),
                clock,
                blockFactory,
                ConfigUtils.getDefaultMiningConfig()
        );

        minerServer.start();
        try {
        MinerWork work = minerServer.getWork();

        assertEquals("0", work.getFeesPaidToMiner());
        } finally {
            minerServer.stop();
        }
    }

    @Test
    public void initialWorkTurnsNotifyFlagOn() {
        EthereumImpl ethereumImpl = Mockito.mock(EthereumImpl.class);

        BlockUnclesValidationRule unclesValidationRule = Mockito.mock(BlockUnclesValidationRule.class);
        Mockito.when(unclesValidationRule.isValid(Mockito.any())).thenReturn(true);
        MinerClock clock = new MinerClock(true, Clock.systemUTC());
        MinerServer minerServer = new MinerServerImpl(
                config,
                ethereumImpl,
                this.blockchain,
                null,
                new ProofOfWorkRule(config).setFallbackMiningEnabled(false),
                new BlockToMineBuilder(
                        config.getActivationConfig(),
                        ConfigUtils.getDefaultMiningConfig(),
                        repository,
                        stateRootHandler,
                        blockStore,
                        transactionPool,
                        difficultyCalculator,
                        new GasLimitCalculator(config.getNetworkConstants()),
                        unclesValidationRule,
                        clock,
                        blockFactory,
                        blockExecutorFactory
                ),
                clock,
                blockFactory,
                ConfigUtils.getDefaultMiningConfig()
        );
        try {
        minerServer.start();

        MinerWork work = minerServer.getWork();

        assertEquals(true, work.getNotify());
        } finally {
            minerServer.stop();
        }
    }

    @Test
    public void secondWorkWithNoChangesTurnsNotifyFlagOff() {
        EthereumImpl ethereumImpl = Mockito.mock(EthereumImpl.class);

        BlockUnclesValidationRule unclesValidationRule = Mockito.mock(BlockUnclesValidationRule.class);
        Mockito.when(unclesValidationRule.isValid(Mockito.any())).thenReturn(true);
        MinerClock clock = new MinerClock(true, Clock.systemUTC());
        MinerServer minerServer = new MinerServerImpl(
                config,
                ethereumImpl,
                this.blockchain,
                null,
                new ProofOfWorkRule(config).setFallbackMiningEnabled(false),
                new BlockToMineBuilder(
                        config.getActivationConfig(),
                        ConfigUtils.getDefaultMiningConfig(),
                        repository,
                        stateRootHandler,
                        blockStore,
                        transactionPool,
                        difficultyCalculator,
                        new GasLimitCalculator(config.getNetworkConstants()),
                        unclesValidationRule,
                        clock,
                        blockFactory,
                        blockExecutorFactory
                ),
                clock,
                blockFactory,
                ConfigUtils.getDefaultMiningConfig()
        );

        minerServer.start();
        try {
        MinerWork work = minerServer.getWork();

        assertEquals(true, work.getNotify());

        work = minerServer.getWork();

        assertEquals(false, work.getNotify());
        } finally {
            minerServer.stop();
        }
    }

    @Test
    public void secondBuildBlockToMineTurnsNotifyFlagOff() {
        EthereumImpl ethereumImpl = Mockito.mock(EthereumImpl.class);

        BlockUnclesValidationRule unclesValidationRule = Mockito.mock(BlockUnclesValidationRule.class);
        Mockito.when(unclesValidationRule.isValid(Mockito.any())).thenReturn(true);
        MinerClock clock = new MinerClock(true, Clock.systemUTC());
        MinerServer minerServer = new MinerServerImpl(
                config,
                ethereumImpl,
                this.blockchain,
                null,
                new ProofOfWorkRule(config).setFallbackMiningEnabled(false),
                new BlockToMineBuilder(
                        config.getActivationConfig(),
                        ConfigUtils.getDefaultMiningConfig(),
                        repository,
                        stateRootHandler,
                        blockStore,
                        transactionPool,
                        difficultyCalculator,
                        new GasLimitCalculator(config.getNetworkConstants()),
                        unclesValidationRule,
                        clock,
                        blockFactory,
                        blockExecutorFactory
                ),
                clock,
                blockFactory,
                ConfigUtils.getDefaultMiningConfig()
        );
        try {
        minerServer.start();

        MinerWork work = minerServer.getWork();

        String hashForMergedMining = work.getBlockHashForMergedMining();

        minerServer.buildBlockToMine(blockchain.getBestBlock(), false);

        work = minerServer.getWork();
        assertEquals(hashForMergedMining, work.getBlockHashForMergedMining());
        assertEquals(false, work.getNotify());
        } finally {
            minerServer.stop();
        }
    }

    private BtcBlock getMergedMiningBlockWithOnlyCoinbase(MinerWork work) {
        return getMergedMiningBlock(work, Collections.emptyList());
    }

    private BtcBlock getMergedMiningBlock(MinerWork work, List<BtcTransaction> txs) {
        NetworkParameters bitcoinNetworkParameters = RegTestParams.get();
        BtcTransaction bitcoinMergedMiningCoinbaseTransaction = MinerUtils.getBitcoinMergedMiningCoinbaseTransaction(bitcoinNetworkParameters, work);

        List<BtcTransaction> blockTxs = new ArrayList<>();
        blockTxs.add(bitcoinMergedMiningCoinbaseTransaction);
        blockTxs.addAll(txs);

        return MinerUtils.getBitcoinMergedMiningBlock(bitcoinNetworkParameters, blockTxs);
    }

    private BtcBlock getMergedMiningBlockWithTwoTags(MinerWork work, MinerWork work2) {
        NetworkParameters bitcoinNetworkParameters = RegTestParams.get();
        BtcTransaction bitcoinMergedMiningCoinbaseTransaction =
                MinerUtils.getBitcoinMergedMiningCoinbaseTransactionWithTwoTags(bitcoinNetworkParameters, work, work2);
        return MinerUtils.getBitcoinMergedMiningBlock(bitcoinNetworkParameters, bitcoinMergedMiningCoinbaseTransaction);
    }

    private void findNonce(MinerWork work, BtcBlock bitcoinMergedMiningBlock) {
        BigInteger target = new BigInteger(TypeConverter.stringHexToByteArray(work.getTarget()));

        while (true) {
            try {
                // Is our proof of work valid yet?
                BigInteger blockHashBI = bitcoinMergedMiningBlock.getHash().toBigInteger();
                if (blockHashBI.compareTo(target) <= 0) {
                    break;
                }
                // No, so increment the nonce and try again.
                bitcoinMergedMiningBlock.setNonce(bitcoinMergedMiningBlock.getNonce() + 1);
            } catch (VerificationException e) {
                throw new RuntimeException(e); // Cannot happen.
            }
        }
    }
}
