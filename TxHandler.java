import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TxHandler {

	UTXOPool utxoPool;

	/**
	 * Creates a public ledger whose current UTXOPool (collection of unspent
	 * transaction outputs) is {@code utxoPool}. This should make a copy of
	 * utxoPool by using the UTXOPool(UTXOPool uPool) constructor.
	 */
	public TxHandler(UTXOPool utxoPool) {
		this.utxoPool = new UTXOPool(utxoPool);
	}

	/**
	 * @return true if: (1) all outputs claimed by {@code tx} are in the current
	 *         UTXO pool, (2) the signatures on each input of {@code tx} are
	 *         valid, (3) no UTXO is claimed multiple times by {@code tx}, (4)
	 *         all of {@code tx}s output values are non-negative, and (5) the
	 *         sum of {@code tx}s input values is greater than or equal to the
	 *         sum of its output values; and false otherwise.
	 */
	public boolean isValidTx(Transaction tx) {
		// Check (1) + (2) + (3) + (4)
		int index = 0; // keeps track of iteration
		double inputSum = 0; // sum of the inputs
		Set<UTXO> set = new HashSet<UTXO>(tx.getInputs().size()); // to detect
																	// double-spend
		for (Transaction.Input txIn : tx.getInputs()) {
			UTXO utxo = new UTXO(txIn.prevTxHash, txIn.outputIndex);
			// check if it's unspent(1)
			if (!utxoPool.contains(utxo))
				return false;
			// fetch the public key and verify the signature(2)
			Transaction.Output txOut = utxoPool.getTxOutput(utxo);
			PublicKey pk = txOut.address;
			byte[] msg = tx.getRawDataToSign(index++);
			if (!Crypto.verifySignature(pk, msg, txIn.signature))
				return false;
			// check for double-spent(3)
			if (!set.add(utxo))
				return false;
			// check if output values are non-negative(4)
			if (txOut.value < 0)
				return false;
			inputSum += txOut.value;
		}
		// Check (5)
		double outputSum = 0;
		for (Transaction.Output txOut : tx.getOutputs()) {
			// check if output values are non-negative(4)
			if (txOut.value < 0)
				return false;
			outputSum += txOut.value;
		}
		if (inputSum < outputSum)
			return false;
		return true;
	}

	/**
	 * Handles each epoch by receiving an unordered array of proposed
	 * transactions, checking each transaction for correctness, returning a
	 * mutually valid array of accepted transactions, and updating the current
	 * UTXO pool as appropriate.
	 */
	public Transaction[] handleTxs(Transaction[] possibleTxs) {
		// Handle the base case where there's only one transaction
		if (possibleTxs.length == 1) {
			if (isValidTx(possibleTxs[0]))
				return possibleTxs;
			else
				return new Transaction[0]; // an empty array
		}
		// filter valid txs
		List<Transaction> validTxs = new ArrayList<Transaction>();
		for (Transaction tx : possibleTxs) {
			if (isValidTx(tx))
				validTxs.add(tx);

		}
		// Map txs to their ids and add vertices to the graph as described by
		// {@code findAMaximalClique}
		Map<byte[], Transaction> idMap = new HashMap<byte[], Transaction>();
		Map<byte[], HashSet<byte[]>> graph = new HashMap<byte[], HashSet<byte[]>>();
		for (Transaction tx : validTxs) {
			idMap.put(tx.getHash(), tx);
			graph.put(tx.getHash(), new HashSet<byte[]>());
		}
		// Add the edges
		for (int i = 0; i < validTxs.size(); i++) {
			for (int j = i + 1; j < validTxs.size(); j++) {
				if (areValidTogether(validTxs.get(i), validTxs.get(j))) {
					graph.get(validTxs.get(i).getHash()).add(validTxs.get(j).getHash());
					graph.get(validTxs.get(j).getHash()).add(validTxs.get(i).getHash());

				}
			}
		}
		// Find a maximal clique
		Set<byte[]> maximal = findAMaximalClique(new HashSet<byte[]>(), graph.keySet(), new HashSet<byte[]>(), graph);
		// Extract the transactions from maximal and remove spent inputs from
		// {@code utxoPool}
		Transaction[] maximalTxs = new Transaction[maximal.size()];
		int index = 0;
		for (byte[] txID : maximal) {
			Transaction tx = idMap.get(txID);
			for (Transaction.Input txIn : tx.getInputs())
				utxoPool.removeUTXO(new UTXO(txIn.prevTxHash, txIn.outputIndex));
			maximalTxs[index++] = tx;
		}
		return maximalTxs;
	}

	/**
	 * Given two valid transactions, tells whether they're valid together, i.e
	 * 1st and 2nd transactions do not spend the same utxo
	 * 
	 * @param tx1
	 * @param tx2
	 * @return
	 */
	private boolean areValidTogether(Transaction tx1, Transaction tx2) {
		// if (isValidTx(tx1) == false || isValidTx(tx2) == false)
		// return false;
		Set<UTXO> set = new HashSet<UTXO>(tx1.getInputs().size());
		for (Transaction.Input txIn : tx1.getInputs())
			set.add(new UTXO(txIn.prevTxHash, txIn.outputIndex));
		for (Transaction.Input txIn : tx2.getInputs()) {
			if (!set.add(new UTXO(txIn.prevTxHash, txIn.outputIndex)))
				return false;
		}
		return true;
	}

	/**
	 * Given a graph, returns an arbitrary maximal clique using BronKerbosch
	 * algorithm Vertices: Hash of transactions Edges: There exists an edge
	 * between vertices tx_i and tx_j if they're valid together.
	 * 
	 * @param graph
	 * @return
	 */
	private static Set<byte[]> findAMaximalClique(Set<byte[]> R, Set<byte[]> P, Set<byte[]> X,
			Map<byte[], HashSet<byte[]>> graph) {
		if (P.isEmpty() && X.isEmpty())
			return R;
		for (byte[] v : P) {
			Set<byte[]> newR = new HashSet<byte[]>(R);
			Set<byte[]> newP = new HashSet<byte[]>(P);
			Set<byte[]> newX = new HashSet<byte[]>(X);
			newR.add(v);
			newP.retainAll(graph.get(v));
			newX.retainAll(graph.get(v));
			Set<byte[]> result = findAMaximalClique(newR, newP, newX, graph);
			if (!result.isEmpty())
				return result;
			P.remove(v);
			X.add(v);
		}
		return null;
	}
}
