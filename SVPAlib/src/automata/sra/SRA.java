/**
 * SVPAlib
 * automata.sra
 * Jul 25, 2018
 * @author Tiago Ferreira
 */
package automata.sra;

import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

import org.sat4j.specs.TimeoutException;


import theory.BooleanAlgebra;
import utilities.*;

/**
 * Symbolic Register Automaton
 * 
 * @param <P> set of predicates over the domain S
 * @param <S> domain of the automaton alphabet
 */
public class SRA<P, S> {

	// ------------------------------------------------------
	// Automata properties
	// ------------------------------------------------------

    protected boolean isEmpty;
    protected boolean isDeterministic;
    protected boolean isTotal;
    protected boolean isMSRA;
	protected boolean isSingleValued;

	private Integer initialState;
    private LinkedList<S> registers;
	private Collection<Integer> states;
	private Collection<Integer> finalStates;

	public Map<Integer, Collection<SRACheckMove<P, S>>> checkMovesFrom;
	public Map<Integer, Collection<SRACheckMove<P, S>>> checkMovesTo;
    public Map<Integer, Collection<SRAFreshMove<P, S>>> freshMovesFrom;
    public Map<Integer, Collection<SRAFreshMove<P, S>>> freshMovesTo;
	public Map<Integer, Collection<SRAStoreMove<P, S>>> storeMovesFrom;
	public Map<Integer, Collection<SRAStoreMove<P, S>>> storeMovesTo;
    public Map<Integer, Collection<MSRAMove<P, S>>> MAMovesFrom;
    public Map<Integer, Collection<MSRAMove<P, S>>> MAMovesTo;
	
    private Integer maxStateId;
	private Integer transitionCount;

	public void setIsDet(boolean b) {
		isDeterministic = b;
	}

	/**
	 * Returns the empty SRA for the Boolean algebra <code>ba</code>
	 * @throws TimeoutException 
	 */
	public static <A, B> SRA<A, B> getEmptySRA(BooleanAlgebra<A, B> ba) throws TimeoutException {
		SRA<A, B> aut = new SRA<A, B>();
		aut.states = new HashSet<Integer>();
		aut.states.add(0);
		aut.finalStates = new HashSet<Integer>();
		aut.initialState = 0;
        aut.registers = new LinkedList<B>();
        aut.registers.add(null);
		aut.isDeterministic = true;
		aut.isEmpty = true;
		aut.isTotal = true;
		aut.isSingleValued = true;
		aut.maxStateId = 1;
		aut.addTransition(new SRACheckMove<>(0, 0, ba.True(), 0), ba, false);
		aut.addTransition(new SRAFreshMove<>(0, 0, ba.True(), 0, aut.registers.size()), ba, false);
		return aut;
	}

	/**
	 * Returns the SRA accepting every string in the Boolean algebra
	 * <code>ba</code>
	 * @throws TimeoutException 
	 */
	public static <A, B> SRA<A, B> getFullSRA(BooleanAlgebra<A, B> ba) throws TimeoutException {
		SRA<A, B> aut = new SRA<A, B>();
		aut.states = new HashSet<Integer>();
		aut.states.add(0);
		aut.finalStates = new HashSet<Integer>(aut.states);
		aut.initialState = 0;
        aut.registers = new LinkedList<B>();
        aut.registers.add(null);
		aut.isDeterministic = true;
		aut.isEmpty = false;
		aut.maxStateId = 1;
		aut.isSingleValued = false;
		aut.addTransition(new SRACheckMove<>(0, 0, ba.True(), 0), ba, false);
		aut.addTransition(new SRAFreshMove<>(0, 0, ba.True(), 0, aut.registers.size()), ba, false);
		return aut;
	}

    /**
	 * @return the maximum state id
	 */
	public Integer getMaxStateId() {
		return maxStateId;
	}

	/**
	 * @return number of states in the automaton
	 */
	public Integer stateCount() {
		return states.size();
	}

	/**
	 * @return number of transitions in the automaton
	 */
	public Integer getTransitionCount() {
		return transitionCount;
	}

    /**
     * @return the register list used by the automaton
     */
    public LinkedList<S> getRegisters() {
        return registers;
    }

	// ------------------------------------------------------
	// Constructors
	// ------------------------------------------------------

	// Initializes all the fields of the automaton
	private SRA() {
        isEmpty = false;
        isDeterministic = false;
        isTotal = false;
        isMSRA = false;
        isSingleValued = false;
		finalStates = new HashSet<Integer>();
		states = new HashSet<Integer>();
        registers = new LinkedList<S>();
		checkMovesFrom = new HashMap<Integer, Collection<SRACheckMove<P, S>>>();
		checkMovesTo = new HashMap<Integer, Collection<SRACheckMove<P, S>>>();
        freshMovesFrom = new HashMap<Integer, Collection<SRAFreshMove<P, S>>>();
        freshMovesTo = new HashMap<Integer, Collection<SRAFreshMove<P, S>>>();
        storeMovesFrom = new HashMap<Integer, Collection<SRAStoreMove<P, S>>>();
        storeMovesTo = new HashMap<Integer, Collection<SRAStoreMove<P, S>>>();
        MAMovesFrom = new HashMap<Integer, Collection<MSRAMove<P, S>>>();
        MAMovesTo = new HashMap<Integer, Collection<MSRAMove<P, S>>>();
    	transitionCount = 0;
		maxStateId = 0;
	}

	/**
	 * Create an automaton and removes unreachable states
	 * 
	 * @throws TimeoutException
	 */
	public static <A, B> SRA<A, B> MkSRA(Collection<SRAMove<A, B>> transitions, Integer initialState,
			Collection<Integer> finalStates, LinkedList<B> registers, BooleanAlgebra<A, B> ba) throws TimeoutException {
    
		return MkSRA(transitions, initialState, finalStates, registers, ba, true);
	}
	
	
	/**
	 * Create an automaton and removes unreachable states and only removes
	 * unreachable states if <code>remUnreachableStates<code> is true
	 * 
	 * @throws TimeoutException
	 */
	public static <A, B> SRA<A, B> MkSRA(Collection<SRAMove<A, B>> transitions, Integer initialState,
			Collection<Integer> finalStates, LinkedList<B> registers, BooleanAlgebra<A, B> ba, boolean remUnreachableStates)
					throws TimeoutException {

		return MkSRA(transitions, initialState, finalStates, registers, ba, remUnreachableStates, true);
	}

	/**
	 * Create an automaton and only removes unreachable states
	 * if remUnreachableStates is true and normalizes the
	 * automaton if normalize is true
	 */
	public static <A, B> SRA<A, B> MkSRA(Collection<SRAMove<A, B>> transitions, Integer initialState,
			Collection<Integer> finalStates, LinkedList<B> registers, BooleanAlgebra<A, B> ba, boolean remUnreachableStates, boolean normalize)
					throws TimeoutException {

		SRA<A, B> aut = new SRA<A, B>();

		aut.states = new HashSet<Integer>();
		aut.states.add(initialState);
		aut.states.addAll(finalStates);

		aut.initialState = initialState;
		aut.finalStates = finalStates;

		if (finalStates.isEmpty())
			return getEmptySRA(ba);

		aut.registers = registers;

		// Check if there are duplicated values
		aut.isSingleValued = false;
		HashSet<B> nonEmptyRegValues = new HashSet<>();
		for (B regValue: registers) {
			if (regValue != null) {
				if (nonEmptyRegValues.contains(regValue)) {
					aut.isSingleValued = false;
					break;
				}
				else nonEmptyRegValues.add(regValue);
			}
		}


		
        for (SRAMove<A, B> t : transitions) {
			aut.addTransition(t, ba, false);
			if (aut.isSingleValued && t.isStore())
				aut.isSingleValued = false;
		}

		if (normalize)
			aut = aut.normalize(ba);

		if (remUnreachableStates)
			aut = removeDeadOrUnreachableStates(aut, ba);

		if (aut.finalStates.isEmpty() || aut.registers.isEmpty())
			return getEmptySRA(ba);

		return aut;
	}

	/**
	 * Gives the option to create an automaton exactly as given by the parameters, avoiding all normalizations.
	 * 
	 * @throws TimeoutException
	 */
	public static <A, B> SRA<A, B> MkSRA(Collection<SRAMove<A, B>> transitions, Integer initialState,
			Collection<Integer> finalStates, LinkedList<B> registers, BooleanAlgebra<A, B> ba, boolean remUnreachableStates, boolean normalize, boolean keepEmpty)  
					throws TimeoutException {
		SRA<A, B> aut = new SRA<A, B>();

		aut.states = new HashSet<Integer>();
		aut.states.add(initialState);
		aut.states.addAll(finalStates);

		aut.initialState = initialState;
		aut.finalStates = finalStates;

        aut.registers = registers;

		// Check if there are duplicated values
		aut.isSingleValued = false;
		HashSet<B> nonEmptyRegValues = new HashSet<>();
		for (B regValue: registers) {
			if (regValue != null) {
				if (nonEmptyRegValues.contains(regValue)) {
					aut.isSingleValued = false;
					break;
				}
				else nonEmptyRegValues.add(regValue);
			}
		}

		for (SRAMove<A, B> t : transitions) {
			aut.addTransition(t, ba, true);

			if (aut.isSingleValued && t.isStore())
				aut.isSingleValued = false;
		}

		if (normalize)
			aut = aut.normalize(ba);

		if (remUnreachableStates)
			aut = removeDeadOrUnreachableStates(aut, ba);

		if (aut.finalStates.isEmpty() && !keepEmpty)
			return getEmptySRA(ba);

		return aut;
	}
	
	// Adds a transition to the SRA
	private void addTransition(SRAMove<P, S> transition, BooleanAlgebra<P, S> ba, boolean skipSatCheck) throws TimeoutException {
		if (skipSatCheck || transition.isSatisfiable(ba)) {

			transitionCount++;

			if (transition.from > maxStateId)
				maxStateId = transition.from;
			if (transition.to > maxStateId)
				maxStateId = transition.to;

			states.add(transition.from);
			states.add(transition.to);

            if (transition.isMultipleAssignment()) {
                // TODO: Check accuracy of translation.
				// FIXME: the following translation assumes isSingleValued = true
				// FIXME: if this is not the case, we need to translate all moves to MSRA moves explicitly
                MSRAMove<P, S> mTransition = transition.asMultipleAssignment(registers).getFirst();
                if (mTransition.E.size() == 1 && mTransition.U.isEmpty()) {
                    getCheckMovesFrom(transition.from).add((new SRACheckMove<P, S>(transition.from, transition.to, transition.guard, mTransition.E.iterator().next())));
                    getCheckMovesTo(transition.to).add((new SRACheckMove<P, S>(transition.from, transition.to, transition.guard, mTransition.E.iterator().next())));
                } else if (mTransition.E.isEmpty() && mTransition.U.size() == 1) {
                    getFreshMovesFrom(transition.from).add((new SRAFreshMove<P, S>(transition.from, transition.to, transition.guard, mTransition.U.iterator().next(), registers.size())));
                    getFreshMovesTo(transition.to).add((new SRAFreshMove<P, S>(transition.from, transition.to, transition.guard, mTransition.U.iterator().next(), registers.size())));
                } else {
                	isMSRA = true;
                	isSingleValued = false;
                    getMAMovesFrom(transition.from).add(mTransition);
                    getMAMovesTo(transition.to).add(mTransition);
                }
            } else if (transition.isFresh()) {
                getFreshMovesFrom(transition.from).add((SRAFreshMove<P, S>) transition);
                getFreshMovesTo(transition.to).add((SRAFreshMove<P, S>) transition);
            } else if (transition.isStore()) {
            	isSingleValued = false; // Store moves are not allowed for non single-valued SRAs
                getStoreMovesFrom(transition.from).add((SRAStoreMove<P, S>) transition);
                getStoreMovesTo(transition.to).add((SRAStoreMove<P, S>) transition);
            } else {
                getCheckMovesFrom(transition.from).add((SRACheckMove<P, S>) transition);
                getCheckMovesTo(transition.to).add((SRACheckMove<P, S>) transition);
            }
		}
	}

	/**
	 * Saves in the file <code>name</code> under the path <code>path</code> the
	 * dot representation of the automaton. Adds .dot if necessary
	 */
	public boolean createDotFile(String name, String path) {
		try {
			FileWriter fw = new FileWriter(path + name + (name.endsWith(".dot") ? "" : ".dot"));
			fw.write("digraph " + name + "{\n rankdir=LR;\n");
			for (Integer state : getStates()) {

				fw.write(state + "[label=" + state);
				if (getFinalStates().contains(state))
					fw.write(",peripheries=2");

				fw.write("]\n");
				if (isInitialState(state))
					fw.write("XX" + state + " [color=white, label=\"\"]");
			}

			fw.write("XX" + getInitialState() + " -> " + getInitialState() + "\n");

			for (Integer state : getStates()) {
				for (SRAMove<P, S> t : getMovesFrom(state))
					fw.write(t.toDotString());
			}

			fw.write("}");
			fw.close();
		} catch (IOException e) {
			System.out.println(e);
			return false;
		}
		return true;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#toString()
	 */
	public String toString() {
		String s = "";
		s = "Automaton: " + getMoves().size() + " transitions, " + getStates().size() + " states" + "\n";
		s += "Transitions \n";
		for (SRAMove<P, S> t : getMoves())
			s = s + t + "\n";

		s += "Initial State \n";
		s = s + getInitialState() + "\n";

		s += "Final States \n";
		for (Integer fs : getFinalStates())
			s = s + fs + "\n";
		return s;
	}

	/**
	 * Returns true if the machine accepts the input list
	 * 
	 * @param input
	 * @param ba
	 * @return true if accepted false otherwise
	 * @throws TimeoutException 
	 */
	public boolean accepts(List<S> input, BooleanAlgebra<P, S> ba) throws TimeoutException {
	    LinkedList<S> cleanRegisters = new LinkedList<S>(registers);
		Collection<Integer> currConf = new LinkedList<Integer>();
        currConf.add(getInitialState());
		for (S el : input) {
			currConf = getNextState(currConf, el, ba);
			if (currConf.isEmpty())
				return false;
		}
        registers = cleanRegisters;
		return isFinalConfiguration(currConf);
	}

	// ------------------------------------------------------
	// Accessory functions
	// ------------------------------------------------------

	/**
	 * Returns the set of transitions starting set of states
	 */
	public Collection<SRAMove<P, S>> getMoves() {
		return getMovesFrom(getStates());
	}

	/**
	 * Set of moves from state
	 */
	public Collection<SRAMove<P, S>> getMovesFrom(Integer state) {
		return new LinkedList<SRAMove<P, S>>(getTransitionsFrom(state));
	}

    /**
	 * Set of moves from set of states
	 */
	public Collection<SRAMove<P, S>> getMovesFrom(Collection<Integer> states) {
		Collection<SRAMove<P, S>> transitions = new LinkedList<SRAMove<P, S>>();
		for (Integer state : states)
			transitions.addAll(getMovesFrom(state));
		return transitions;
	}

    /**
     * Returns the set of moves from a state as multiple assignment form
     */
    public Collection<MSRAMove<P, S>> getMovesFromAsMA(Integer state) {
        Collection<MSRAMove<P, S>> transitions = new LinkedList<MSRAMove<P, S>>();
        for (SRAMove<P, S> transition : getTransitionsFrom(state))
            transitions.addAll(transition.asMultipleAssignment(registers));
        return transitions;
    }


    /**
     * Returns the set of moves from a set of states as multiple assignment form
     */
    public Collection<MSRAMove<P, S>> getMovesFromAsMA(Collection<Integer> states) {
        Collection<MSRAMove<P, S>> transitions = new LinkedList<MSRAMove<P, S>>();
        for (Integer state : states)
            transitions.addAll(getMovesFromAsMA(state));
        return transitions;
    }

	/**
	 * Set of moves to <code>state</code>
	 */
	public Collection<SRAMove<P, S>> getMovesTo(Integer state) {
		return getTransitionsTo(state);
	}

	/**
	 * Set of moves to a set of states <code>states</code>
	 */
	public Collection<SRAMove<P, S>> getMovesTo(Collection<Integer> states) {
		Collection<SRAMove<P, S>> transitions = new LinkedList<SRAMove<P, S>>();
		for (Integer state : states)
			transitions.addAll(getMovesTo(state));
		return transitions;
	}

	/**
	 * Returns the set of states
	 */
	public Collection<Integer> getStates() {
        return states;
    }

	/**
	 * Returns initial state
	 */
	public Integer getInitialState() {
        return initialState;
    }

	/**
	 * Returns the set of final states
	 */
	public Collection<Integer> getFinalStates() {
        return finalStates;
    }

    /**
     * Returns the set of non final states
     */
	public Collection<Integer> getNonFinalStates() {
		HashSet<Integer> nonFin = new HashSet<Integer>(states);
		nonFin.removeAll(finalStates);
		return nonFin;
	}

	/**
	 * @return true if the set <code>conf</code> contains an initial state
	 */
	public boolean isInitialConfiguration(Collection<Integer> conf) {
		for (Integer state : conf)
			if (isInitialState(state))
				return true;
		return false;
	}

	/**
	 * @return true if <code>state</code> is an initial state
	 */
	public boolean isInitialState(Integer state) {
		return getInitialState() == state;
	}

	/**
	 * @return true if <code>conf</code> contains a final state
	 */
	public boolean isFinalConfiguration(Collection<Integer> conf) {
		for (Integer state : conf)
			if (isFinalState(state))
				return true;
		return false;
	}

	/**
	 * @return true if <code>state</code> is a final state
	 */
	public boolean isFinalState(Integer state) {
		return getFinalStates().contains(state);
	}

	protected Collection<Integer> getNextState(Collection<Integer> currState, S inputElement, BooleanAlgebra<P, S> ba) throws TimeoutException {
		Collection<Integer> nextState = new HashSet<Integer>();
		for (SRAMove<P, S> t : getMovesFrom(currState)) {
			if (t.hasModel(inputElement, ba, registers)) {
                nextState.add(t.to);
                if (t.isMultipleAssignment())
                    for (Integer index : t.asMultipleAssignment(registers).getFirst().U)
                        registers.set(index, inputElement);
                if (t.isFresh() || t.isStore())
                    registers.set(t.U.iterator().next(), inputElement);
            }
		}
		return nextState;
	}


	// Get list of predicates without duplicates
	private ArrayList<P> getAllPredicates(long timeout) {
		HashSet<P> predicatesSet = new HashSet<>();

		HashMap<Integer, Integer> reached = new HashMap<>();
		LinkedList<Integer> toVisit = new LinkedList<>();

		reached.put(initialState, 0);
		toVisit.add(initialState);

		while (!toVisit.isEmpty()) {
			Integer curState = toVisit.removeFirst();

			for (SRAMove<P, S> ct : getMovesFrom(curState)) {
				predicatesSet.add(ct.guard);

				if (!reached.containsKey(ct.to)) {
					toVisit.add(ct.to);
					reached.put(ct.to, reached.size() + 1);
				}

			}

		}

		return new ArrayList<>(predicatesSet);
	}

	// ------------------------------------------------------
	// Utility functions and classes for normalised SRA
	// ------------------------------------------------------

	// TODO: Should all these be static?

	// Encapsulates minterm
	protected static class MinTerm<P> {
		private Pair<P, ArrayList<Integer>> data;

		protected MinTerm(P pred, ArrayList<Integer> bitVec) {
			data = new Pair<>(pred, bitVec);
		}

		protected boolean equals(MinTerm<P> mt) {
			return data.second.equals(mt.getBitVector());
		}

		protected P getPredicate() {
			return data.first;
		}

		protected ArrayList<Integer> getBitVector() {
			return data.second;
		}

	}

	// Encapsulates normal SRA state
	protected  static class NormSRAState<P> {
		private Pair<Integer, HashMap<Integer, MinTerm<P>>> data;

		protected NormSRAState(Integer stateID, HashMap<Integer, MinTerm<P>> regAbs) {
			data = new Pair(stateID, regAbs);
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;
			NormSRAState<?> that = (NormSRAState<?>) o;
			return Objects.equals(data, that.data);
		}

		@Override
		public int hashCode() {
			return Objects.hash(data);
		}

		protected Integer getStateId() {
			return data.first;
		}

		protected HashMap<Integer, MinTerm<P>> getRegAbs() {
			return data.second;
		}


	}

	protected static class NormSRAMove<P> {
		public NormSRAState<P> from;
		public NormSRAState<P> to;
		public MinTerm<P> guard;
		public Integer register;

		public NormSRAMove(NormSRAState<P> from, NormSRAState<P> to, MinTerm<P> guard, Integer register) {
			this.from = from;
			this.to = to;
			this.guard = guard;
			this.register = register;
		}
	}

	protected static class NormSRACheckMove<P> extends NormSRAMove<P> {

		public NormSRACheckMove(NormSRAState<P> from, NormSRAState<P> to, MinTerm<P> guard, Integer register) {
			super(from, to, guard, register);
		}

	}

	protected static class NormSRAFreshMove<P> extends NormSRAMove<P> {

		public NormSRAFreshMove(NormSRAState<P> from, NormSRAState<P> to, MinTerm<P> guard, Integer register) {
			super(from, to, guard, register);
		}

	}

	// Encapsulates a reduced bisimulation triple
	protected  static class NormSimTriple<P> {
		Triple<NormSRAState<P>, NormSRAState<P>, HashMap<Integer, Integer>> data;

		protected NormSimTriple(NormSRAState<P> NormState1, NormSRAState<P> NormState2, HashMap<Integer, Integer> regMap) {
			data = new Triple<>(NormState1, NormState2, regMap);
		}

//		protected boolean equals(NormSRAState<P> rs) {
//			return this.first.equals(rs.first) && this.second.equals(rs.second) &&
//		}

		protected NormSRAState<P> getState1() {
			return data.first;
		}

		protected NormSRAState<P> getState2() {
			return data.second;
		}

		protected HashMap<Integer, Integer> getRegMap(){
			return data.third;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;
			NormSimTriple<?> that = (NormSimTriple<?>) o;
			return Objects.equals(data, that.data);
		}

		@Override
		public int hashCode() {
			return Objects.hash(data);
		}
	}


	// Breaks down a SRA move into minterms.
	// Only for single-valued SRAs
	private static <P, S> LinkedList<NormSRAMove<P>> toNormSRAMoves(BooleanAlgebra<P, S> ba,
																  HashMap<Integer, MinTerm<P>> regAbs,
																  HashMap<P, LinkedList<MinTerm<P>>> mintermsForPredicate,
																  SRAMove<P, S> move,
																  NormSRAState<P> from) {

		LinkedList<NormSRAMove<P>> normMoves = new LinkedList<>();
		LinkedList<MinTerm<P>> minterms = mintermsForPredicate.get(move.guard);

		// TODO: Needs to be reviewed, which register do we use here?


		if (move instanceof SRACheckMove) {
            Integer register = move.E.iterator().next();
			MinTerm<P> registerMintInAbs = regAbs.get(register);

			if (registerMintInAbs != null && minterms.contains(registerMintInAbs)) {
				HashMap<Integer, MinTerm<P>> newRegAbs = new HashMap<>(regAbs);
				NormSRAState<P> targetState = new NormSRAState<>(move.to, newRegAbs);

				normMoves.add(new NormSRACheckMove<>(from, targetState, registerMintInAbs, register));
			}
		}
		else {
			for (MinTerm<P> mint: minterms) {
				Integer neededWitnessesForMint = 1;

				for (Integer r: regAbs.keySet()) {
					MinTerm<P> regMint = regAbs.get(r);

					if (regMint != null && regMint.equals(mint))
						neededWitnessesForMint++;
				}

				if (ba.hasNDistinctWitnesses(mint.getPredicate(), neededWitnessesForMint)) {
					HashMap<Integer, MinTerm<P>> newRegAbs = new HashMap<>(regAbs);
					// FIXME: Which register do we refer to now?
					newRegAbs.put(move.registerIndexes.iterator().next(), mint);
					NormSRAState<P> targetState = new NormSRAState<>(move.to, newRegAbs);

					normMoves.add(new NormSRAFreshMove<>(from, targetState, mint, register));
				}
			}
		}

		return normMoves;
	}


	// Compute minterms where predicates are non-negated
	private static <P> HashMap<P, LinkedList<MinTerm<P>>> getMintermsForPredicates(List<P> allPredicates, List<MinTerm<P>> minTerms) {
		HashMap<P, LinkedList<MinTerm<P>>> mintermsForPredicates = new HashMap<>();

		for (P pred: allPredicates) {
			LinkedList<MinTerm<P>> mintList = new LinkedList<>();

			Integer predicateIndex = allPredicates.indexOf(pred);

			for (MinTerm<P> mint: minTerms) {
				if (mint.getBitVector().get(predicateIndex) == 1) // pred is non-negated in mint
					mintList.add(mint);
			}

			mintermsForPredicates.put(pred, mintList);
		}

		return mintermsForPredicates;
	}

	// Create initial register abstraction
	private HashMap<Integer, MinTerm<P>> getInitialRegAbs(List<P> allPredicates,
														  Integer initValAtomsIndex,
														  HashMap<P, LinkedList<MinTerm<P>>> mintermsForPredicates) {
		HashMap<Integer, MinTerm<P>> initRegAb = new HashMap<>();

		Integer notNullInd = 0;
		for (Integer r = 0; r < registers.size(); r++) {
			// P atom = allPredicates.get(initValAtomsIndex + r);

			if (registers.get(r) == null)
				initRegAb.put(r, null);
			else {
				P atom = allPredicates.get(initValAtomsIndex + notNullInd);
				initRegAb.put(r, mintermsForPredicates.get(atom).get(0)); // There should be only 1 minterm for atom
				notNullInd++;
			}
		}

		return initRegAb;
	}



	// Emptiness check

	public static <P, S> boolean isLanguageEmpty(SRA<P, S> aut, BooleanAlgebra<P, S> ba, long timeout) throws TimeoutException {
		long startTime = System.currentTimeMillis();

		if (aut.isEmpty)
			return true;

		if (aut.isMSRA)
			aut = aut.toSingleValuedSRA(ba, timeout);


		// Compute all minterms
		ArrayList<P> allPredicates = aut.getAllPredicates(timeout);
		Integer initValPos = allPredicates.size();


		for (S regVal: aut.registers) // Add initial register values to predicates
			if (regVal != null)
				allPredicates.add(ba.MkAtom(regVal));

		LinkedList<MinTerm<P>> minTerms = new LinkedList<>();

		for(Pair<P, ArrayList<Integer>> minBA: ba.GetMinterms(allPredicates))
			minTerms.add(new MinTerm<>(minBA.first, minBA.second));


		HashMap<P, LinkedList<MinTerm<P>>> mintermsForPredicates = getMintermsForPredicates(allPredicates, minTerms);
		HashMap<Integer, MinTerm<P>> initRegAbs = aut.getInitialRegAbs(allPredicates, initValPos, mintermsForPredicates);



		// Create initial state of the normalised SRA
		NormSRAState<P> initNormState = new NormSRAState<>(aut.initialState, initRegAbs);

		// reached contains the product states (p,theta) we discovered and maps
		// them to a stateId
		HashMap<NormSRAState<P>, Integer> reached = new HashMap<>();
		// toVisit contains the product states we still have not explored
		LinkedList<NormSRAState<P>> toVisit = new LinkedList<>();

		toVisit.add(initNormState);
		reached.put(initNormState, 0);

		while (!toVisit.isEmpty()) {
			NormSRAState<P> currentState = toVisit.removeFirst();

			if (aut.finalStates.contains(currentState.getStateId()))
				return false;


			for (SRAMove<P, S> move: aut.getMovesFrom(currentState.getStateId())) {
				LinkedList<NormSRAMove<P>> normMoves =
						toNormSRAMoves(ba, currentState.getRegAbs(), mintermsForPredicates, move, null);

				if (System.currentTimeMillis() - startTime > timeout)
					throw new TimeoutException();

				for (NormSRAMove<P> normMove: normMoves) {
					NormSRAState<P> nextState = normMove.to;

					getStateId(nextState, reached, toVisit);
				}

			}
			
		}

		return true;
	}

	private static HashMap<Integer, Integer> getRegMapInv(HashMap<Integer, Integer> regMap) {
		HashMap<Integer, Integer> invRegMap = new HashMap<>();

		for (Integer r: regMap.keySet())
			invRegMap.put(regMap.get(r), r);

		return invRegMap;
	}



	public HashMap<Pair<Integer, Integer>, P> getPredMap(BooleanAlgebra<P, S> ba) throws TimeoutException {
		HashMap<Pair<Integer, Integer>, P> predMap = new HashMap<>();
		Integer regSize = registers.size();

		HashMap<Integer, Integer> reached = new HashMap<>();
		LinkedList<Integer> toVisit = new LinkedList<>();

		reached.put(initialState, 0);
		toVisit.add(initialState);

		while (!toVisit.isEmpty()) {
			Integer curState = toVisit.removeLast();
			Pair<Integer, Integer> newKey;
			boolean[] defReg = new boolean[regSize + 1];
			Arrays.fill(defReg, false);

			for (SRAMove<P, S> ct : getMovesFrom(curState)) {
				Integer moveReg;

				// TODO: Check case for store moves.
				if (ct instanceof SRACheckMove)
                    // FIXME: Which register do we refer to now?
					moveReg = ct.registerIndexes.iterator().next();
				else
					moveReg = regSize; // Conventionally for fresh moves

				defReg[moveReg] = true;
				newKey = new Pair<>(curState, moveReg);

				if (predMap.containsKey(newKey)) {
					P curPred = predMap.get(newKey);
					curPred = ba.MkOr(curPred, ct.guard);
					predMap.put(newKey, curPred);
				} else {
					predMap.put(newKey, ct.guard);
				}


				if (!reached.containsKey(ct.to)) {
					toVisit.add(ct.to);
					reached.put(ct.to, reached.size() + 1);
				}
			}

			// Put False in all other positions
			for (Integer i = 0; i <= regSize; i++) {
				if (!defReg[i]) {
					newKey = new Pair<>(curState, i);
					predMap.put(newKey, ba.False());
				}
			}
		}

		return predMap;
	}


	public void complete(BooleanAlgebra<P, S> ba) throws TimeoutException {

		if (isEmpty)
			return; // empty SRA is already complete

		HashMap<Pair<Integer, Integer>, P> predMap = getPredMap(ba);
		Integer sinkState = stateCount();
		Integer regSize = registers.size();
		Integer chosenReg = 0;

		for (Pair<Integer, Integer> key: predMap.keySet()) {
			Integer state = key.first;
			Integer reg = key.second;
			P negPred = ba.MkNot(predMap.get(key));
			SRAMove<P, S> newMove;

			if (reg.equals(regSize))
				newMove = new SRACheckMove<>(state, sinkState, negPred, reg);
			else
				newMove = new SRAFreshMove<>(state, sinkState, negPred, chosenReg, regSize);


			addTransition(newMove, ba, false);
		}

		SRAMove<P,S> checkSinkLoop = new SRACheckMove<>(sinkState, sinkState, ba.True(), chosenReg);
		SRAMove<P,S> freshSinkLoop = new SRAFreshMove<>(sinkState, sinkState, ba.True(), chosenReg, regSize);

		addTransition(checkSinkLoop, ba, false);
		addTransition(freshSinkLoop, ba, false);

		isTotal = true;
	}

	public boolean isLanguageEquivalent(SRA<P,S> aut, BooleanAlgebra<P,S> ba, long timeout) throws TimeoutException {
		SRA<P,S> me = this;

		if (!isSingleValued)
			me = toSingleValuedSRA(ba, timeout);

		if (!aut.isSingleValued)
			aut = aut.toSingleValuedSRA(ba, timeout);

//		if (!me.isTotal)
//			me.complete(ba);
//
//		if (!aut.isTotal)
//			aut.complete(ba);

		return canSimulate(me, aut, ba, true, timeout);
	}

	public boolean languageIncludes(SRA<P,S> aut, BooleanAlgebra<P,S> ba, long timeout) throws TimeoutException {
		SRA<P,S> me = this;

		if (!isSingleValued)
			me = toSingleValuedSRA(ba, timeout);

		if (!aut.isSingleValued)
			aut = aut.toSingleValuedSRA(ba, timeout);

//		System.out.print("completed");
//		me.createDotFile("complete1","");
//		aut.createDotFile("complete2","");


//
//		if (!me.isTotal)
//			me.complete(ba);
//
//		if (!aut.isTotal)
//			aut.complete(ba);
//
//		System.out.println("Here");
//
//
		return canSimulate(aut, me, ba, false, timeout);
	}

//	public static <P, S> SRA<P, S> getPruned(SRA<P,S> aut) {
//
//	}

	public static <P, S> boolean canSimulate(SRA<P,S> aut1, SRA<P,S> aut2, BooleanAlgebra<P, S> ba, boolean bisimulation, long timeout)
			throws TimeoutException {

		if (aut1.isEmpty) {
			if (bisimulation && !aut2.isEmpty)
				return false;

			return true;
		}


		if(!aut1.isSingleValued)
			aut1 = aut1.toSingleValuedSRA(ba, timeout);

		if(!aut2.isSingleValued)
			aut2 = aut2.toSingleValuedSRA(ba, timeout);


		// Implement synchronised visit

		// Initial register map
		HashMap<Integer, Integer> initRegMap = new HashMap<>();
		HashMap<Integer, Integer> initRegMapInv = new HashMap<>();

		for (Integer r1 = 0; r1 < aut1.registers.size(); r1++) {
			for (Integer r2 = 0; r2 < aut2.registers.size(); r2++) {
				S r1Content = aut1.registers.get(r1);
				S r2Content = aut2.registers.get(r2);

				if (r1Content != null && r1Content.equals(r2Content)) {
					initRegMap.put(r1, r2);
					initRegMapInv.put(r2, r1);
				}
			}
		}

		// Get all predicates for both SRA
		ArrayList<P> allPredicates = aut1.getAllPredicates(timeout);
		Integer initValPos1 = allPredicates.size();

		for (P predicate: aut2.getAllPredicates(timeout))
			if (!allPredicates.contains(predicate)) // Add without duplicates
				allPredicates.add(predicate);

		for (S regVal: aut1.registers) // Add initial register values of aut1 to predicates
			if (regVal != null)
				allPredicates.add(ba.MkAtom(regVal));

		Integer initValPos2 = allPredicates.size();

		for (S regVal: aut2.registers) // Add initial register values of aut2 to predicates
			if (regVal != null)
				allPredicates.add(ba.MkAtom(regVal));


		// Computer minterms
		LinkedList<MinTerm<P>> minTerms = new LinkedList<>();

		for(Pair<P, ArrayList<Integer>> minBA: ba.GetMinterms(allPredicates))
			minTerms.add(new MinTerm<>(minBA.first, minBA.second));


		HashMap<P, LinkedList<MinTerm<P>>> mintermsForPredicates = getMintermsForPredicates(allPredicates, minTerms);
		HashMap<Integer, MinTerm<P>> initRegAbs1 = aut1.getInitialRegAbs(allPredicates, initValPos1, mintermsForPredicates);
		HashMap<Integer, MinTerm<P>> initRegAbs2 = aut2.getInitialRegAbs(allPredicates, initValPos2, mintermsForPredicates);



		// Create initial triples
		NormSRAState<P> initNormState1 = new NormSRAState<>(aut1.initialState, initRegAbs1);
		NormSRAState<P> initNormState2 = new NormSRAState<>(aut2.initialState, initRegAbs2);

		NormSimTriple<P> initTriple = new NormSimTriple<>(initNormState1, initNormState2, initRegMap);

		// reached contains the triples we have already discovered and maps them to a stateId
		HashMap<NormSimTriple<P>, Integer> reached = new HashMap<>();
		// toVisit contains the triples we have not explored yet
		LinkedList<NormSimTriple<P>> toVisit = new LinkedList<>();
		// LinkedList<NormSimTriple<P>> toVisitInv = new LinkedList<>();

		toVisit.add(initTriple);
		// toVisitInv.add()
		reached.put(initTriple, 0);



		// Keep track of outgoing reduced transitions that have already been generated
		HashMap<NormSRAState<P>, LinkedList<NormSRAMove<P>>> aut1NormOut = new HashMap<>();
		HashMap<NormSRAState<P>, LinkedList<NormSRAMove<P>>> aut2NormOut = new HashMap<>();


		while (!toVisit.isEmpty()) {
			NormSimTriple<P> currentTriple = toVisit.removeLast(); // BFS visit

			NormSRAState<P> aut1NormState = currentTriple.getState1();
			NormSRAState<P> aut2NormState = currentTriple.getState2();
			HashMap<Integer, Integer> regMap = currentTriple.getRegMap();

			if (aut1.finalStates.contains(aut1NormState.getStateId()) &&
					!aut2.finalStates.contains(aut2NormState.getStateId()))
				return false;


			if (bisimulation)
				if (aut2.finalStates.contains(aut2NormState.getStateId()) &&
						!aut1.finalStates.contains(aut1NormState.getStateId()))
					return false;


			// int currentStateID = reached.get(currentTriple);

			HashMap<Integer, MinTerm<P>> currentRegAbs1 = aut1NormState.getRegAbs();
			HashMap<Integer, MinTerm<P>> currentRegAbs2 = aut2NormState.getRegAbs();

			// Compute all the normalised moves from aut1NormState and aut2NormState
			LinkedList<NormSRAMove<P>> normMovesFromCurrent1;
			LinkedList<NormSRAMove<P>> normMovesFromCurrent2;

			if (aut1NormOut.containsKey(aut1NormState))
				normMovesFromCurrent1 = aut1NormOut.get(aut1NormState);
			else {
				normMovesFromCurrent1 = new LinkedList<>();

				for (SRAMove<P, S> move : aut1.getMovesFrom(aut1NormState.getStateId())) {
					LinkedList<NormSRAMove<P>> partialNormMoves = toNormSRAMoves(ba, currentRegAbs1, mintermsForPredicates,
							move, aut1NormState);

					normMovesFromCurrent1.addAll(partialNormMoves);
				}

				aut1NormOut.put(aut1NormState, normMovesFromCurrent1);
			}

			if (normMovesFromCurrent1.isEmpty()) // we don't need to find matching moves from aut2
				continue;

			if (aut2NormOut.containsKey(aut2NormState))
				normMovesFromCurrent2 = aut2NormOut.get(aut2NormState);
			else {
				normMovesFromCurrent2 = new LinkedList<>();

				for (SRAMove<P, S> move : aut2.getMovesFrom(aut2NormState.getStateId())) {
					LinkedList<NormSRAMove<P>> partialNormMoves = toNormSRAMoves(ba, currentRegAbs2, mintermsForPredicates,
							move, aut2NormState);

					normMovesFromCurrent2.addAll(partialNormMoves);
				}

				aut2NormOut.put(aut2NormState, normMovesFromCurrent2);
			}

			// Get new similarity triples
			LinkedList<NormSimTriple<P>> newTriples = normSimSucc(ba, normMovesFromCurrent1, normMovesFromCurrent2,
					regMap, currentRegAbs1, currentRegAbs2);

			if (newTriples == null)
				return false;

			if (bisimulation) {
				if (normMovesFromCurrent2.isEmpty()) // we don't need to find matching moves from aut1
					continue;

				LinkedList<NormSimTriple<P>> invTriples = normSimSucc(ba, normMovesFromCurrent2, normMovesFromCurrent1,
						getRegMapInv(regMap), currentRegAbs2, currentRegAbs1);

				if (invTriples == null)
					return false;
			}

			for (NormSimTriple<P> triple: newTriples)
				getStateId(triple, reached, toVisit);

		}


		return true;
	}





	// Returns all reduced bisimulation triples that need to be checked in subsequent steps
	private static <P, S> LinkedList<NormSimTriple<P>> normSimSucc(BooleanAlgebra<P, S> ba,
															   	   LinkedList<NormSRAMove<P>> normMoves1,
																   LinkedList<NormSRAMove<P>> normMoves2,
																   HashMap<Integer, Integer> regMap,
																   HashMap<Integer, MinTerm<P>> regAbs1,
																   HashMap<Integer, MinTerm<P>> regAbs2) {

		LinkedList<NormSimTriple<P>> nextTriples = new LinkedList<>();


		for (NormSRAMove<P> move1: normMoves1) {
			if (move1 instanceof NormSRACheckMove) {
				Integer r1 = move1.register;
				NormSRAMove<P> matchingMove = null;
				HashMap<Integer, Integer> newRegMap = null;

				// Case 1(a) in the paper
				if (regMap.containsKey(r1)){
					Integer r2 = regMap.get(r1);

					for (NormSRAMove<P> move2: normMoves2) {
						if (move2 instanceof NormSRACheckMove && move2.register.equals(r2)) { // Guard is the same by construction
							matchingMove = move2;
							newRegMap = new HashMap<>(regMap);
							break;
						}
					}
				}
				else {
					// Case 1(b) in the paper
					for (NormSRAMove<P> move2: normMoves2) {
						if (move2 instanceof NormSRAFreshMove && move2.guard.equals(move1.guard)) {
							matchingMove = move2;
							newRegMap = new HashMap<>(regMap);
							newRegMap.put(move1.register, move2.register);
							break;
						}
					}
				}

				if (matchingMove == null)
					return null;

				nextTriples.add(new NormSimTriple<>(move1.to, matchingMove.to, newRegMap));
			}
			else {
				// Case 2(a)

				// regInImg(r) = false iff r not in img(regMap)
				Integer regNum2 = regAbs2.size();
				boolean[] regInImg = new boolean[regNum2];
				Arrays.fill(regInImg, false);

				for (Integer r1: regMap.keySet())
					regInImg[regMap.get(r1)] = true;

				for (int r2 = 0; r2 < regNum2; r2++) {
					MinTerm<P> mintermForReg = regAbs2.get(r2);

					if (mintermForReg != null && !regInImg[r2] && mintermForReg.equals(move1.guard)) {
						NormSRAMove<P> matchingMove = null;
						HashMap<Integer, Integer> newRegMap = null;

						for (NormSRAMove<P> move2: normMoves2) {
							if (move2 instanceof NormSRACheckMove && move2.register.equals(r2)) { // Guard must be the same
								matchingMove = move2;
								newRegMap = new HashMap<>(regMap);
								newRegMap.put(move1.register, move2.register);
							}
						}

						if (matchingMove == null)
							return null;

						nextTriples.add(new NormSimTriple<>(move1.to, matchingMove.to, newRegMap));
					}
				}

				// Case 2(b)
				Integer howManyEqualToGuard1 = 1;

				for (Integer reg: regAbs1.keySet()) {
					MinTerm<P> mintermForReg = regAbs1.get(reg);

					if (mintermForReg != null && regAbs1.get(reg).equals(move1.guard))
						howManyEqualToGuard1++;
				}

				for (Integer reg: regAbs2.keySet()) {
					MinTerm<P> mintermForReg = regAbs2.get(reg);

					if (mintermForReg != null && regAbs2.get(reg).equals(move1.guard))
						howManyEqualToGuard1++;
				}

				if (ba.hasNDistinctWitnesses(move1.guard.getPredicate(), howManyEqualToGuard1)) {
					NormSRAMove<P> matchingMove = null;
					HashMap<Integer, Integer> newRegMap = null;

					for (NormSRAMove<P> move2: normMoves2) {
						if (move2 instanceof NormSRAFreshMove && move2.guard.equals(move1.guard)) { // Guard must be the same
							matchingMove = move2;
							newRegMap = new HashMap<>(regMap);
							newRegMap.put(move1.register, move2.register);
							break;
						}
					}

					if (matchingMove == null)
						return null;

					nextTriples.add(new NormSimTriple<>(move1.to, matchingMove.to, newRegMap));
				}
			}

		}

		return nextTriples;
	}


	/**
	 * Compiles <code>this</code> down to an equivalent Single-valued SRA
	 *
	 * @throws TimeoutException
	 */
	public SRA<P, S> toSingleValuedSRA(BooleanAlgebra<P, S> ba, long timeout) throws TimeoutException {

        long startTime = System.currentTimeMillis();

        // FIXME: check that isMSRA is false if and only if isSingleValued is true
		// FIXME: check that, when isSingleValued == false, moves gets translated properly
        if (isSingleValued)
            return MkSRA(getTransitions(), initialState, finalStates, getRegisters(), ba, false);

        // If the automaton is empty return the empty SRA
        if (isEmpty)
            return getEmptySRA(ba);

        // components of target SRA
        Collection<SRAMove<P, S>> transitions = new ArrayList<SRAMove<P, S>>();
        LinkedList<S> newRegisters = new LinkedList<S>(registers);
        Collection<Integer> newFinalStates = new ArrayList<Integer>();

        HashMap<S, ArrayList<Integer>> valueToRegisters = new HashMap<S, ArrayList<Integer>>();
        for (Integer index = 0; index < registers.size(); index++) {
            S registerValue = registers.get(index);
            if (valueToRegisters.containsKey(registerValue)) {
                valueToRegisters.get(registerValue).add(index);
            } else {
         //       if (registerValue != null) {
                    ArrayList<Integer> registersForValue = new ArrayList<Integer>();
                    registersForValue.add(index);
                    valueToRegisters.put(registerValue, registersForValue);
          //      }
            }
        }

        HashMap<Integer, Integer> initialMap = new HashMap<Integer, Integer>();
        for (ArrayList<Integer> repeatedRegisters : valueToRegisters.values()) {
            Integer firstElement = repeatedRegisters.get(0);
            initialMap.put(firstElement, firstElement);

            for (int i = 1; i < repeatedRegisters.size(); i++) {
                initialMap.put(repeatedRegisters.get(i), firstElement);
                newRegisters.set(repeatedRegisters.get(i), null);
            }
        }


        // reached contains the states (p,f) we discovered and maps
        // them to a stateId
        HashMap<Pair<Integer, HashMap<Integer,Integer>>, Integer> reached = new HashMap<Pair<Integer, HashMap<Integer,Integer>>, Integer>();
        // toVisit contains the product states we still have not explored
        LinkedList<Pair<Integer, HashMap<Integer,Integer>>> toVisit = new LinkedList<Pair<Integer, HashMap<Integer,Integer>>>();

        // The initial state is the pair consisting of the initial state (q0,f0)
        Pair<Integer, HashMap<Integer,Integer>> initPair = new Pair<Integer, HashMap<Integer,Integer>>(initialState, initialMap);
        reached.put(initPair, 0);
        toVisit.add(initPair);

        // Explore the product automaton until no new states can be reached
        while (!toVisit.isEmpty()) {

            Pair<Integer, HashMap<Integer, Integer>> currentState = toVisit.removeLast(); // BFS visit
            int currentStateID = reached.get(currentState);
            HashMap<Integer, Integer> currentMap = currentState.second;

            for (MSRAMove<P, S> ct : getMovesFromAsMA(currentState.first)) {
				LinkedList<SRAMove<P, S>> SRAMoves = new LinkedList<>();

				if (System.currentTimeMillis() - startTime > timeout)
					throw new TimeoutException();

                if (!ct.E.isEmpty()) {
                    // Case 1 in the paper: check whether there is a register r such that currentMap(E) = r
                    Set<Integer> repeatedRegisters = new HashSet<>();
                    for (Integer registerE : ct.E) {
                        Integer registerEImg = currentMap.get(registerE);
                        if (registerEImg != null)
                            repeatedRegisters.add(registerEImg);
                    }

                    if (repeatedRegisters.size() == 1)
                        SRAMoves.add(new SRACheckMove<P, S>(currentStateID, null, ct.guard, repeatedRegisters.iterator().next()));
                } else {

					// Compute inverse
					HashMap<Integer, LinkedList<Integer>> inverseMap = new HashMap<>();

					for (Integer i = 0; i < registers.size(); i++) {
						Integer registerImg = currentMap.get(i);

						LinkedList<Integer> inverseImg;

						if (inverseMap.get(registerImg) == null) {
							inverseImg = new LinkedList<>();
							inverseMap.put(registerImg, inverseImg);
						}
						else
							inverseImg = inverseMap.get(registerImg);

						inverseImg.add(i);
					}

					// Case 2 in the paper: check whether inverseMap(r) is included in U, for some r
					for (Integer i = 0; i < registers.size(); i++) {
						LinkedList<Integer> inverseImg = inverseMap.get(i);

						if (inverseImg == null || ct.U.containsAll(inverseImg)) {
							SRAMoves.add(new SRAFreshMove<P, S>(currentStateID, null, ct.guard, i, registers.size()));
							break;
						}
					}


                    // Case 3 in the paper: check whether inverseMap(r) is empty, for some r
					for (Integer i = 0; i < registers.size(); i++)
						if (inverseMap.get(i) == null)
	                        SRAMoves.add(new SRACheckMove<P, S>(currentStateID, null, ct.guard, i));
                }


                for (SRAMove<P, S> transition : SRAMoves) {
                    if (transition.isSatisfiable(ba)) {
                        HashMap<Integer, Integer> nextMap = new HashMap<>(currentMap);
                        // FIXME: Which register do we refer to now?
                        Integer transitionRegister = transition.registerIndexes.iterator().next();

                        for (Integer registersToUpdate : ct.U)
                            nextMap.put(registersToUpdate, transitionRegister);

                        Pair<Integer, HashMap<Integer, Integer>> nextState = new Pair<>(ct.to, nextMap);
                        transition.to = getStateId(nextState, reached, toVisit);
                        if (finalStates.contains(ct.to))
                            newFinalStates.add(transition.to);
                        transitions.add(transition);
                    }
                }

            }
        }
        return MkSRA(transitions, initialState, newFinalStates, newRegisters, ba);
    }

	/**
	 * If <code>state<code> belongs to reached returns reached(state) otherwise
	 * add state to reached and to toVisit and return corresponding id
	 */
	public static <T> int getStateId(T state, Map<T, Integer> reached, LinkedList<T> toVisit) {
		if (!reached.containsKey(state)) {
			int newId = reached.size();
			reached.put(state, newId);
			toVisit.add(state);
			return newId;
		} else
			return reached.get(state);
	}

	// ------------------------------------------------------
	// Getters
	// ------------------------------------------------------

	/**
	 * @return the isEmpty
	 */
	public boolean isEmpty() {
		return isEmpty;
	}

	/**
	 * @return the isDeterministic
	 */
	public boolean isDeterministic() {
		return isDeterministic;
	}

    /**
	 * @return the isTotal
	 */
	public boolean isTotal() {
		return isTotal;
	}

    // ------------------------------------------------------
    // Boolean automata operations
    // ------------------------------------------------------

    /**
     * Computes the intersection with <code>aut</code> as a new SRA
     *
     * @throws TimeoutException
     */
    public SRA<P, S> intersectionWith(SRA<P, S> aut, BooleanAlgebra<P, S> ba, long timeout) throws TimeoutException {
        return intersection(this, aut, ba, timeout);
    }

    /**
     * Computes the intersection with <code>aut</code> as a new SRA
     *
     * @throws TimeoutException
     */
    public SRA<P, S> intersectionWith(SRA<P, S> aut, BooleanAlgebra<P, S> ba) throws TimeoutException {
        return intersection(this, aut, ba, Long.MAX_VALUE);
    }

    /**
     * Computes the intersection with <code>aut1</code> and <code>aut2</code> as
     * a new SRA
     *
     * @throws TimeoutException
     */
    public static <A, B> SRA<A, B> intersection(SRA<A, B> aut1, SRA<A, B> aut2, BooleanAlgebra<A, B> ba, long timeout)
            throws TimeoutException {

        long startTime = System.currentTimeMillis();

        // if one of the automata is empty return the empty SRA
        if (aut1.isEmpty || aut2.isEmpty)
            return getEmptySRA(ba);

        // components of new SRA
        Collection<SRAMove<A, B>> transitions = new ArrayList<SRAMove<A, B>>();
        Integer initialState = 0;
        Collection<Integer> finalStates = new ArrayList<Integer>();
        LinkedList<B> registers = new LinkedList<B>();
       
        // intersection registers are the union of register components
        registers.addAll(aut1.getRegisters());
        registers.addAll(aut2.getRegisters());

        // reached contains the product states (p1,p2) we discovered and maps
        // them to a stateId
        HashMap<Pair<Integer, Integer>, Integer> reached = new HashMap<Pair<Integer, Integer>, Integer>();
        // toVisit contains the product states we still have not explored
        LinkedList<Pair<Integer, Integer>> toVisit = new LinkedList<Pair<Integer, Integer>>();

        // The initial state is the pair consisting of the initial
        // states of aut1 and aut2
        Pair<Integer, Integer> initPair = new Pair<Integer, Integer>(aut1.initialState, aut2.initialState);
        reached.put(initPair, 0);
        toVisit.add(initPair);

        // Explore the product automaton until no new states can be reached
        while (!toVisit.isEmpty()) {

            Pair<Integer, Integer> currentState = toVisit.removeFirst();
            int currentStateID = reached.get(currentState);

            // Try to pair transitions out of both automata
            for (MSRAMove<A, B> ct1 : aut1.getMovesFromAsMA(currentState.first))
                for (MSRAMove<A, B> ct2 : aut2.getMovesFromAsMA(currentState.second)) {

                    if (System.currentTimeMillis() - startTime > timeout)
                        throw new TimeoutException();

                    // create conjunction of the two guards and create
                    // transition only if the conjunction is satisfiable
                    A intersGuard = ba.MkAnd(ct1.guard, ct2.guard);

                    // create union of the two E sets.
                    Set<Integer> intersE = new HashSet<Integer>();
                    intersE.addAll(ct1.E);
                    for (Integer registerE : ct2.E)
                        intersE.add(registerE + ct1.E.size());

                    // create union fo the two U sets.
                    Set<Integer> intersU = new HashSet<Integer>();
                    intersU.addAll(ct1.U);
                    for (Integer registerU : ct2.U)
                        intersU.add(registerU + ct1.U.size());
                    
                    // construct potential transition.
                    MSRAMove<A, B> transition = new MSRAMove<A, B>(currentStateID, null, intersGuard, intersE, intersU);

                    // if it is satisfiable, add nextStateID and update iteration lists.
                    if (transition.isSatisfiable(ba)) {
                        Pair<Integer, Integer> nextState = new Pair<Integer, Integer>(ct1.to, ct2.to);
                        transition.to = getStateId(nextState, reached, toVisit);
                        if (aut1.finalStates.contains(ct1.to) || aut2.finalStates.contains(ct2.to))
                            finalStates.add(transition.to);
                        transitions.add(transition);
                    }
                }
        }

        return MkSRA(transitions, initialState, finalStates, registers, ba);
    }

	// ------------------------------------------------------
	// Other automata operations
	// ------------------------------------------------------

    /**
     * Creates a normalized copy of the SRA where all transitions between states
     * are collapsed taking their union, and states are renamed with 0,1,...     
     *
     * @throws TimeoutException
     */
    public SRA<P, S> normalize(BooleanAlgebra<P, S> ba) throws TimeoutException {
        return getNormalized(this, ba);
    }

    /**
     * Creates a normalized copy of <code>aut<code> where all transitions
     * between states are collapsed taking their union
     *
     * @throws TimeoutException
     */
    public static <A, B> SRA<A, B> getNormalized(SRA<A, B> aut, BooleanAlgebra<A, B> ba) throws TimeoutException {

        if (aut.isEmpty)
            return getEmptySRA(ba);

        // components of new SRA
        Collection<SRAMove<A, B>> transitions = new ArrayList<SRAMove<A, B>>();
        Integer initialState = aut.initialState;
        Collection<Integer> finalStates = new HashSet<Integer>(aut.finalStates);
        LinkedList<B> registers = aut.registers;

        // New moves
        Map<Pair<Integer, Integer>, Pair<A, Integer>> checkMoves = new HashMap<Pair<Integer, Integer>, Pair<A, Integer>>();
        Map<Pair<Integer, Integer>, Pair<A, Integer>> freshMoves = new HashMap<Pair<Integer, Integer>, Pair<A, Integer>>();
        Map<Pair<Integer, Integer>, Pair<A, Integer>> storeMoves = new HashMap<Pair<Integer, Integer>, Pair<A, Integer>>();
        Map<Pair<Integer, Integer>, Pair<A, Pair<Set<Integer>, Set<Integer>>>> MAMoves = new HashMap<Pair<Integer, Integer>, Pair<A, Pair<Set<Integer>, Set<Integer>>>>();

        // Create disjunction of all rules between same state and with the same operation
        for (SRAMove<A, B> move : aut.getMovesFrom(aut.states)) {
            Pair<Integer, Integer> fromTo = new Pair<Integer, Integer>(move.from, move.to);
            if (move.isMultipleAssignment()) {
                if (MAMoves.containsKey(fromTo))
                    MAMoves.put(fromTo, new Pair<A, Pair<Set<Integer>, Set<Integer>>>(ba.MkOr(move.guard, MAMoves.get(fromTo).first),
                            new Pair<Set<Integer>, Set<Integer>>(move.asMultipleAssignment(registers).getFirst().E, move.asMultipleAssignment(registers).getFirst().U)));
                else
                    MAMoves.put(fromTo, new Pair<A, Pair<Set<Integer>, Set<Integer>>>(move.guard,
                            new Pair<Set<Integer>, Set<Integer>>(move.asMultipleAssignment(registers).getFirst().E, move.asMultipleAssignment(registers).getFirst().U)));
            } else if (move.isFresh()) {
                if (freshMoves.containsKey(fromTo))
                    freshMoves.put(fromTo, new Pair<A, Integer>(ba.MkOr(move.guard, freshMoves.get(fromTo).first), move.U.iterator().next()));
                else
                    freshMoves.put(fromTo, new Pair<A, Integer>(move.guard, move.U.iterator().next()));
            } else if (move.isStore()) {
                if (storeMoves.containsKey(fromTo))
                    storeMoves.put(fromTo, new Pair<A, Integer>(ba.MkOr(move.guard, storeMoves.get(fromTo).first), move.U.iterator().next()));
                else
                    storeMoves.put(fromTo, new Pair<A, Integer>(move.guard, move.U.iterator().next()));
            } else {
                if (checkMoves.containsKey(fromTo))
                    checkMoves.put(fromTo, new Pair<A, Integer>(ba.MkOr(move.guard, checkMoves.get(fromTo).first), move.E.iterator().next()));
                else
                    checkMoves.put(fromTo, new Pair<A, Integer>(move.guard, move.E.iterator().next()));
            }
        }

        // Create the new transition function
        for (Pair<Integer, Integer> p : checkMoves.keySet())
            transitions.add(new SRACheckMove<A, B>(p.first, p.second, checkMoves.get(p).first, checkMoves.get(p).second));
        for (Pair<Integer, Integer> p : freshMoves.keySet())
            transitions.add(new SRAFreshMove<A, B>(p.first, p.second, freshMoves.get(p).first, freshMoves.get(p).second, registers.size()));
        for (Pair<Integer, Integer> p : storeMoves.keySet())
            transitions.add(new SRAStoreMove<A, B>(p.first, p.second, storeMoves.get(p).first, storeMoves.get(p).second));
        for (Pair<Integer, Integer> p : MAMoves.keySet())
            transitions.add(new MSRAMove<A, B>(p.first, p.second, MAMoves.get(p).first, MAMoves.get(p).second.first, MAMoves.get(p).second.second));

        return MkSRA(transitions, initialState, finalStates, registers, ba, false, false);
    }

	// ------------------------------------------------------
	// Reachability methods
	// ------------------------------------------------------

	// creates a new SRA where all unreachable or dead states have been removed
	private static <A, B> SRA<A, B> removeDeadOrUnreachableStates(SRA<A, B> aut, BooleanAlgebra<A, B> ba)
			throws TimeoutException {

		// components of new SRA
		Collection<SRAMove<A, B>> transitions = new ArrayList<SRAMove<A, B>>();
		Integer initialState = 0;
		Collection<Integer> finalStates = new HashSet<Integer>();
        LinkedList<B> registers = aut.registers;

		HashSet<Integer> initStates = new HashSet<Integer>();
		initStates.add(aut.initialState);
		Collection<Integer> reachableFromInit = aut.getReachableStatesFrom(initStates);
		Collection<Integer> reachingFinal = aut.getReachingStates(aut.finalStates);

		Collection<Integer> aliveStates = new HashSet<Integer>();

		// Computes states that reachable from initial state and can reach a
		// final state
		for (Integer state : reachableFromInit)
			if (reachingFinal.contains(state)) {
				aliveStates.add(state);
			}

		if (aliveStates.size() == 0)
			return getEmptySRA(ba);

		for (Integer state : aliveStates)
			for (SRAMove<A, B> t : aut.getTransitionsFrom(state))
				if (aliveStates.contains(t.to))
					transitions.add(t);

		initialState = aut.initialState;

		for (Integer state : aut.finalStates)
			if (aliveStates.contains(state))
				finalStates.add(state);

		return MkSRA(transitions, initialState, finalStates, registers, ba, false, false);
	}

	// Computes states that reachable from states
	private Collection<Integer> getReachableStatesFrom(Collection<Integer> states) {
		HashSet<Integer> result = new HashSet<Integer>();
		for (Integer state : states)
			visitForward(state, result);
		return result;
	}

	// Computes states that can reach states
	private Collection<Integer> getReachingStates(Collection<Integer> states) {
		HashSet<Integer> result = new HashSet<Integer>();
		for (Integer state : states)
			visitBackward(state, result);
		return result;
	}

	// DFS accumulates in reached
	private void visitForward(Integer state, HashSet<Integer> reached) {
		if (!reached.contains(state)) {
			reached.add(state);
			for (SRAMove<P, S> t : this.getTransitionsFrom(state)) {
				Integer nextState = t.to;
				visitForward(nextState, reached);
			}
		}
	}

	// backward DFS accumulates in reached
	private void visitBackward(Integer state, HashSet<Integer> reached) {
		if (!reached.contains(state)) {
			reached.add(state);
			for (SRAMove<P, S> t : this.getTransitionsTo(state)) {
				Integer pNormState = t.from;
				visitBackward(pNormState, reached);
			}
		}
	}

	// ------------------------------------------------------
	// Properties accessing methods
	// ------------------------------------------------------

	/**
	 * Returns the set of transitions starting at state <code>s</code>
	 */
	public Collection<SRAMove<P, S>> getTransitionsFrom(Integer state) {
		Collection<SRAMove<P, S>> moves = new HashSet<SRAMove<P, S>>();
		moves.addAll(getCheckMovesFrom(state));
        moves.addAll(getFreshMovesFrom(state));
        moves.addAll(getStoreMovesFrom(state));
        moves.addAll(getMAMovesFrom(state));
		return moves;
	}

	/**
	 * Returns the set of transitions to state <code>s</code>
	 */
	public Collection<SRAMove<P, S>> getTransitionsTo(Integer state) {
		Collection<SRAMove<P, S>> moves = new HashSet<SRAMove<P, S>>();
		moves.addAll(getCheckMovesTo(state));
        moves.addAll(getFreshMovesTo(state));
        moves.addAll(getStoreMovesTo(state));
        moves.addAll(getMAMovesTo(state));
		return moves;
	}

	/**
	 * Returns the set of transitions starting set of states
	 */
	public Collection<SRAMove<P, S>> getTransitionsFrom(Collection<Integer> stateSet) {
		Collection<SRAMove<P, S>> transitions = new LinkedList<SRAMove<P, S>>();
		for (Integer state : stateSet)
			transitions.addAll(getTransitionsFrom(state));
		return transitions;
	}

	/**
	 * Returns the set of transitions to a set of states
	 */
	public Collection<SRAMove<P, S>> getTransitionsTo(Collection<Integer> stateSet) {
		Collection<SRAMove<P, S>> transitions = new LinkedList<SRAMove<P, S>>();
		for (Integer state : stateSet)
			transitions.addAll(getTransitionsTo(state));
		return transitions;
	}

	/**
	 * Returns the set of check transitions to state <code>state</code>
	 */
	public Collection<SRACheckMove<P, S>> getCheckMovesTo(Integer state) {
		return checkMovesTo.computeIfAbsent(state, k -> new HashSet<SRACheckMove<P, S>>());
	}

	/**
	 * Returns the set of check transitions to states <code>stateSet</code>
	 */
	public Collection<SRACheckMove<P, S>> getCheckMovesTo(Collection<Integer> stateSet) {
		Collection<SRACheckMove<P, S>> transitions = new LinkedList<SRACheckMove<P, S>>();
		for (Integer state : stateSet)
			transitions.addAll(getCheckMovesTo(state));
		return transitions;
	}

    /**
	 * Returns the set of check transitions from state <code>state</code>
	 */
    public Collection<SRACheckMove<P, S>> getCheckMovesFrom(Integer state) {
        return checkMovesFrom.computeIfAbsent(state, k -> new HashSet<SRACheckMove<P, S>>());
    }

    /**
     * Returns the set of check transitions from states <code>stateSet</code>
     */
    public Collection<SRACheckMove<P, S>> getCheckMovesFrom(Collection<Integer> stateSet) {
        Collection<SRACheckMove<P, S>> transitions = new LinkedList<SRACheckMove<P, S>>();
        for (Integer state : stateSet)
            transitions.addAll(getCheckMovesFrom(state));
        return transitions;
    }

    /**
     * Returns the set of fresh transitions to state <code>state</code>
     */
    public Collection<SRAFreshMove<P, S>> getFreshMovesTo(Integer state) {
        return freshMovesTo.computeIfAbsent(state, k -> new HashSet<SRAFreshMove<P, S>>());
    }

    /**
     * Returns the set of fresh transitions to states <code>stateSet</code>
     */
    public Collection<SRAFreshMove<P, S>> getFreshMovesTo(Collection<Integer> stateSet) {
        Collection<SRAFreshMove<P, S>> transitions = new LinkedList<SRAFreshMove<P, S>>();
        for (Integer state : stateSet)
            transitions.addAll(getFreshMovesTo(state));
        return transitions;
    }

	/**
	 * Returns the set of fresh transitions to state <code>s</code>
	 */
	public Collection<SRAFreshMove<P, S>> getFreshMovesFrom(Integer state) {
		return freshMovesFrom.computeIfAbsent(state, k -> new HashSet<SRAFreshMove<P, S>>());
	}

    /**
     * Returns the set of fresh moves from states <code>stateSet</code>
     */
    public Collection<SRAFreshMove<P, S>> getFreshMovesFrom(Collection<Integer> stateSet) {
        Collection<SRAFreshMove<P, S>> transitions = new LinkedList<SRAFreshMove<P, S>>();
        for (Integer state : stateSet)
            transitions.addAll(getFreshMovesFrom(state));
        return transitions;
    }

    /**
     * Returns the set of store transitions to state <code>state</code>
     */
    public Collection<SRAStoreMove<P, S>> getStoreMovesTo(Integer state) {
        return storeMovesTo.computeIfAbsent(state, k -> new HashSet<SRAStoreMove<P, S>>());
    }

    /**
     * Returns the set of store transitions to states <code>stateSet</code>
     */
    public Collection<SRAStoreMove<P, S>> getStoreMovesTo(Collection<Integer> stateSet) {
        Collection<SRAStoreMove<P, S>> transitions = new LinkedList<SRAStoreMove<P, S>>();
        for (Integer state : stateSet)
            transitions.addAll(getStoreMovesTo(state));
        return transitions;
    }

    /**
     * Returns the set of store transitions to state <code>s</code>
     */
    public Collection<SRAStoreMove<P, S>> getStoreMovesFrom(Integer state) {
        return storeMovesFrom.computeIfAbsent(state, k -> new HashSet<SRAStoreMove<P, S>>());
    }

    /**
     * Returns the set of store moves from states <code>stateSet</code>
     */
    public Collection<SRAStoreMove<P, S>> getStoreMovesFrom(Collection<Integer> stateSet) {
        Collection<SRAStoreMove<P, S>> transitions = new LinkedList<SRAStoreMove<P, S>>();
        for (Integer state : stateSet)
            transitions.addAll(getStoreMovesFrom(state));
        return transitions;
    }

    /**
     * Returns the set of multiple assignment transitions to state <code>state</code>
     */
    public Collection<MSRAMove<P, S>> getMAMovesTo(Integer state) {
        return MAMovesTo.computeIfAbsent(state, k -> new HashSet<MSRAMove<P, S>>());
    }

    /**
     * Returns the set of multiple assignment transitions to states <code>stateSet</code>
     */
    public Collection<MSRAMove<P, S>> getMAMovesTo(Collection<Integer> stateSet) {
        Collection<MSRAMove<P, S>> transitions = new LinkedList<MSRAMove<P, S>>();
        for (Integer state : stateSet)
            transitions.addAll(getMAMovesTo(state));
        return transitions;
    }

    /**
     * Returns the set of multiple assignment transitions from state <code>state</code>
     */
    public Collection<MSRAMove<P, S>> getMAMovesFrom(Integer state) {
        return MAMovesFrom.computeIfAbsent(state, k -> new HashSet<MSRAMove<P, S>>());
    }

    /**
     * Returns the set of multiple assignment transitions from states <code>stateSet</code>
     */
    public Collection<MSRAMove<P, S>> getMAMovesFrom(Collection<Integer> stateSet) {
        Collection<MSRAMove<P, S>> transitions = new LinkedList<MSRAMove<P, S>>();
        for (Integer state : stateSet)
            transitions.addAll(getMAMovesFrom(state));
        return transitions;
    }

	/**
	 * Returns the set of all transitions
	 */
	public Collection<SRAMove<P, S>> getTransitions() {
		Collection<SRAMove<P, S>> transitions = new LinkedList<SRAMove<P, S>>();
		for (Integer state : states)
			transitions.addAll(getTransitionsFrom(state));
		return transitions;
	}

    @Override
	public Object clone() {
		SRA<P, S> cl = new SRA<P, S>();

		cl.isDeterministic = isDeterministic;
		cl.isTotal = isTotal;
		cl.isEmpty = isEmpty;

		cl.maxStateId = maxStateId;
		cl.transitionCount = transitionCount;

		cl.states = new HashSet<Integer>(states);
		cl.initialState = initialState;
		cl.finalStates = new HashSet<Integer>(finalStates);

		cl.checkMovesFrom = new HashMap<Integer, Collection<SRACheckMove<P, S>>>(checkMovesFrom);
		cl.checkMovesTo = new HashMap<Integer, Collection<SRACheckMove<P, S>>>(checkMovesTo);

        cl.freshMovesFrom = new HashMap<Integer, Collection<SRAFreshMove<P, S>>>(freshMovesFrom);
        cl.freshMovesTo = new HashMap<Integer, Collection<SRAFreshMove<P, S>>>(freshMovesTo);

        cl.storeMovesFrom = new HashMap<Integer, Collection<SRAStoreMove<P, S>>>(storeMovesFrom);
        cl.storeMovesTo = new HashMap<Integer, Collection<SRAStoreMove<P, S>>>(storeMovesTo);

        cl.MAMovesFrom = new HashMap<Integer, Collection<MSRAMove<P, S>>>(MAMovesFrom);
        cl.MAMovesTo = new HashMap<Integer, Collection<MSRAMove<P, S>>>(MAMovesTo);

		return cl;
	}

}
