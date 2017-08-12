import java.util.HashSet;
import java.util.Set;

/* CompliantNode refers to a node that follows the rules (not malicious)*/
public class CompliantNode implements Node {

	int nRounds;
	Set<Transaction> txns = new HashSet<Transaction>();
	Set<Integer> followees = new HashSet<Integer>();

	public CompliantNode(double p_graph, double p_malicious, double p_txDistribution, int numRounds) {
		this.nRounds = numRounds;
	}

	public void setFollowees(boolean[] followees) {
		for (int i=0; i<followees.length; i++){
			if (followees[i])
				this.followees.add(i);
    	
		}
    }

	public void setPendingTransaction(Set<Transaction> pendingTransactions) {
		txns.addAll(pendingTransactions);
	}

	public Set<Transaction> sendToFollowers() {
		return this.txns;

	}

	public void receiveFromFollowees(Set<Candidate> candidates) {
		for (Candidate e : candidates){
			if (followees.contains(e.sender))
				txns.add(e.tx);
		}

	}
}
