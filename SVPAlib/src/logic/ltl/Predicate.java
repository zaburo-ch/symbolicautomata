package logic.ltl;

import java.util.Collection;
import java.util.HashMap;

import org.sat4j.specs.TimeoutException;

import automata.safa.BooleanExpressionFactory;
import automata.safa.SAFA;
import automata.safa.SAFAInputMove;
import automata.safa.booleanexpression.PositiveBooleanExpression;
import theory.BooleanAlgebra;

public class Predicate<P, S> extends LTLFormula<P, S> {

	protected P predicate;

	public Predicate(P predicate) {
		super();
		this.predicate = predicate;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((predicate == null) ? 0 : predicate.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (!(obj instanceof Predicate))
			return false;
		@SuppressWarnings("unchecked")
		Predicate<P, S> other = (Predicate<P, S>) obj;
		if (predicate == null) {
			if (other.predicate != null)
				return false;
		} else if (predicate != other.predicate)
			return false;
		return true;
	}

	@Override
	protected PositiveBooleanExpression accumulateSAFAStatesTransitions(
			HashMap<LTLFormula<P, S>, PositiveBooleanExpression> formulaToState, Collection<SAFAInputMove<P, S>> moves,
			Collection<Integer> finalStates, BooleanAlgebra<P, S> ba, int emptyId) {
		BooleanExpressionFactory<PositiveBooleanExpression> boolexpr = SAFA.getBooleanExpressionFactory();

		// If I already visited avoid recomputing
		if (formulaToState.containsKey(this))
			return formulaToState.get(this);

		//True
		LTLFormula<P, S> tr = new True<>();
		int trueid = formulaToState.size();
		PositiveBooleanExpression trueState = boolexpr.MkState(trueid);
		if(formulaToState.containsKey(tr))
			trueState = formulaToState.get(tr);
		else{
			formulaToState.put(tr, trueState);
			finalStates.add(trueid);
			// delta(True, true) = True
			moves.add(new SAFAInputMove<>(trueid, trueState, ba.True()));			
		}
						
		// Update hash tables	
		int id = formulaToState.size();
		PositiveBooleanExpression initialState = boolexpr.MkState(id);
		formulaToState.put(this, initialState);

		// delta([p], p) = true
		moves.add(new SAFAInputMove<>(id, trueState, predicate));

		if (this.isFinalState())
			finalStates.add(id);

		return initialState;
	}

	@Override
	protected boolean isFinalState() {
		return false;
	}

	@Override
	protected LTLFormula<P, S> pushNegations(boolean isPositive, BooleanAlgebra<P, S> ba,
			HashMap<String, LTLFormula<P, S>> posHash, HashMap<String, LTLFormula<P, S>> negHash) throws TimeoutException {
		if (isPositive)
			return this;
		else
			return new Or<>(new Predicate<>(ba.MkNot(this.predicate)), new EmptyString<>());
	}

	@Override
	public void toString(StringBuilder sb) {
		sb.append(predicate.toString());
	}

	@Override
	public int getSize() {
		return 1;
	}
}
