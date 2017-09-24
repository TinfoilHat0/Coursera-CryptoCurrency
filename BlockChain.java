import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

// Block Chain should maintain only limited block nodes to satisfy the functions
// You should not have all the blocks added to the block chain in memory 
// as it would cause a memory overflow.

public class BlockChain {
	public static final int CUT_OFF_AGE = 10;
	private Integer maxHeight = 0;
	private Block maxHeightBlock;
	private TransactionPool txnPool = new TransactionPool();
	private Map<byte[], byte[]> blockchain = new HashMap<byte[], byte[]>();
	private Map<byte[], Integer> blockHeight = new HashMap<byte[], Integer>();
	private Map<byte[], UTXOPool> blockUTXO = new HashMap<byte[], UTXOPool>();

	/**
	 * create an empty block chain with just a genesis block. Assume
	 * {@code genesisBlock} is a valid block
	 */
	public BlockChain(Block genesisBlock) {
		// Update max params and extract UTXOs from the block
		maxHeight++;
		maxHeightBlock = genesisBlock;
		// Update the blockchain and other map structures
		byte[] blockID = genesisBlock.getHash();
		blockchain.put(blockID, genesisBlock.getPrevBlockHash());
		blockHeight.put(blockID, maxHeight);
		// Initialize the UTXO pool of the chain
		UTXOPool utxoPool = new UTXOPool();
		// First handle the coinbase txn
		processCoinbase(genesisBlock, utxoPool);
		// Then put the rest of them
		for (Transaction txn : genesisBlock.getTransactions()) {
			int ctr = 0;
			for (Transaction.Output txOut : txn.getOutputs()) {
				utxoPool.addUTXO(new UTXO(txn.getHash(), ctr++), txOut);
			}
		}
		blockUTXO.put(blockID, utxoPool);
	}

	/** Get the maximum height block */
	public Block getMaxHeightBlock() {
		return maxHeightBlock;

	}

	/** Get the UTXOPool for mining a new block on top of max height block */
	public UTXOPool getMaxHeightUTXOPool() {
		return blockUTXO.get(maxHeightBlock.getHash());
	}

	/** Get the transaction pool to mine a new block */
	public TransactionPool getTransactionPool() {
		return txnPool;
	}

	/**
	 * Add {@code block} to the block chain if it is valid. For validity, all
	 * transactions should be valid and block should be at
	 * {@code height > (maxHeight - CUT_OFF_AGE)}.
	 * 
	 * 
	 * For example, you can try creating a new block over the genesis block
	 * (block height 2) if the block chain height is {@code <=
	 * CUT_OFF_AGE + 1}. As soon as {@code height > CUT_OFF_AGE + 1}, you cannot
	 * create a new block at height 2.
	 * 
	 * @return true if block is successfully added
	 */
	public boolean addBlock(Block block) {
		byte[] parentHash = block.getPrevBlockHash();
		// Fake genesis
		if (parentHash == null)
			return false;
		// There's no such block in the chain
		if (!blockchain.containsKey(parentHash))
			return false;
		// Verify if height of the block is valid
		if (blockHeight.get(parentHash) + 1 <= maxHeight - CUT_OFF_AGE)
			return false;
		// Verify txns in the block
		TxHandler txHandler = new TxHandler(blockUTXO.get(parentHash));
		Set<UTXO> set = new HashSet<UTXO>();
		/*
		 * for (Transaction txn : block.getTransactions()){ // Check each txn
		 * individually if (!txHandler.isValidTx(txn)) return false; // Check if
		 * they form a valid set, i.e. no txn uses input of an another for
		 * (Transaction.Input txIn : txn.getInputs()){ if (!set.add(new
		 * UTXO(txIn.prevTxHash, txIn.outputIndex))) return false; } }
		 */
		// Add block to the chain
		byte[] blockID = block.getHash();
		Integer height;
		// Extending on the longest branch
		if (parentHash.equals(maxHeightBlock.getHash())) {
			height = ++maxHeight;
			maxHeightBlock = block;
		}
		// Extending on a side branch/creating a fork
		else {
			height = blockHeight.get(parentHash) + 1;
			// Check if side branch became larger
			if (height > maxHeight) {
				maxHeight = height;
				maxHeightBlock = block;
			}
		}
		blockHeight.put(blockID, height);
		blockchain.put(blockID, parentHash);
		// Fetch the UTXOPool from the parent and update it
		// Remove inputs from the pool and add the outputs
		UTXOPool utxoPool = new UTXOPool(blockUTXO.get(parentHash));
		processCoinbase(block, utxoPool);
		for (Transaction txn : block.getTransactions()) {
			for (Transaction.Input txIn : txn.getInputs()) {
				utxoPool.removeUTXO(new UTXO(txIn.prevTxHash, txIn.outputIndex));
			}
			int ctr = 0;
			for (Transaction.Output txOut : txn.getOutputs()) {
				utxoPool.addUTXO(new UTXO(txn.getHash(), ctr++), txOut);
			}
			// Remove "processed" txns from the pool
			txnPool.removeTransaction(txn.getHash());
		}
		blockUTXO.put(blockID, utxoPool);
		cleanOldBlocks();
		return true;
	}

	/** Add a transaction to the transaction pool */
	public void addTransaction(Transaction tx) {
		txnPool.addTransaction(tx);
	}

	/** Adds coinbase txn to UTXOPool */
	private void processCoinbase(Block b, UTXOPool utxoPool) {
		int ctr = 0;
		for (Transaction.Output txOut : b.getCoinbase().getOutputs()) {
			UTXO utxo = new UTXO(b.getCoinbase().getHash(), ctr++);
			utxoPool.addUTXO(utxo, txOut);
		}
	}

	/** Clean "expired" blocks */
	private void cleanOldBlocks() {
		// Goes over the height map and deletes the blocks
		// which we can't build on top of
		List<byte[]> toRemove = new ArrayList<byte[]>();
		for (byte[] e : blockHeight.keySet()) {
			if (blockHeight.get(e) < maxHeight - CUT_OFF_AGE) {
				toRemove.add(e);
			}
		}
		for (byte[] e : toRemove) {
			blockchain.remove(e);
			blockHeight.remove(e);
			blockUTXO.remove(e);
		}
	}
}