/*
 * dk.brics.automaton
 * 
 * Copyright (c) 2001-2009 Anders Moeller
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. The name of the author may not be used to endorse or promote products
 *    derived from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
 * THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.apache.lucene.util.automaton;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.lucene.util.ArrayUtil;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.IntsRef;
import org.apache.lucene.util.RamUsageEstimator;
import org.apache.lucene.util.XBytesRefBuilder;
import org.apache.lucene.util.XIntsRefBuilder;

/**
 * Automata operations.
 * 
 * @lucene.experimental
 */
final public class XOperations {
  /**
   * Default maximum number of states that {@link XOperations#determinize} should create.
   */
  public static final int DEFAULT_MAX_DETERMINIZED_STATES = 10000;

  private XOperations() {}

  /**
   * Returns an automaton that accepts the concatenation of the languages of the
   * given automata.
   * <p>
   * Complexity: linear in total number of states.
   */
  static public XAutomaton concatenate(XAutomaton a1, XAutomaton a2) {
    return concatenate(Arrays.asList(a1, a2));
  }

  /**
   * Returns an automaton that accepts the concatenation of the languages of the
   * given automata.
   * <p>
   * Complexity: linear in total number of states.
   */
  static public XAutomaton concatenate(List<XAutomaton> l) {
    XAutomaton result = new XAutomaton();

    // First pass: create all states
    for(XAutomaton a : l) {
      if (a.getNumStates() == 0) {
        result.finishState();
        return result;
      }
      int numStates = a.getNumStates();
      for(int s=0;s<numStates;s++) {
        result.createState();
      }
    }

    // Second pass: add transitions, carefully linking accept
    // states of A to init state of next A:
    int stateOffset = 0;
    XTransition t = new XTransition();
    for(int i=0;i<l.size();i++) {
      XAutomaton a = l.get(i);
      int numStates = a.getNumStates();

      XAutomaton nextA = (i == l.size()-1) ? null : l.get(i+1);

      for(int s=0;s<numStates;s++) {
        int numTransitions = a.initTransition(s, t);
        for(int j=0;j<numTransitions;j++) {
          a.getNextTransition(t);
          result.addTransition(stateOffset + s, stateOffset + t.dest, t.min, t.max);
        }

        if (a.isAccept(s)) {
          XAutomaton followA = nextA;
          int followOffset = stateOffset;
          int upto = i+1;
          while (true) {
            if (followA != null) {
              // Adds a "virtual" epsilon transition:
              numTransitions = followA.initTransition(0, t);
              for(int j=0;j<numTransitions;j++) {
                followA.getNextTransition(t);
                result.addTransition(stateOffset + s, followOffset + numStates + t.dest, t.min, t.max);
              }
              if (followA.isAccept(0)) {
                // Keep chaining if followA accepts empty string
                followOffset += followA.getNumStates();
                followA = (upto == l.size()-1) ? null : l.get(upto+1);
                upto++;
              } else {
                break;
              }
            } else {
              result.setAccept(stateOffset + s, true);
              break;
            }
          }
        }
      }

      stateOffset += numStates;
    }

    if (result.getNumStates() == 0) {
      result.createState();
    }

    result.finishState();

    return result;
  }

  /**
   * Returns an automaton that accepts the union of the empty string and the
   * language of the given automaton.
   * <p>
   * Complexity: linear in number of states.
   */
  static public XAutomaton optional(XAutomaton a) {
    XAutomaton result = new XAutomaton();
    result.createState();
    result.setAccept(0, true);
    if (a.getNumStates() > 0) {
      result.copy(a);
      result.addEpsilon(0, 1);
    }
    result.finishState();
    return result;
  }
  
  /**
   * Returns an automaton that accepts the Kleene star (zero or more
   * concatenated repetitions) of the language of the given automaton. Never
   * modifies the input automaton language.
   * <p>
   * Complexity: linear in number of states.
   */
  static public XAutomaton repeat(XAutomaton a) {
    XAutomaton.Builder builder = new XAutomaton.Builder();
    builder.createState();
    builder.setAccept(0, true);
    builder.copy(a);

    XTransition t = new XTransition();
    int count = a.initTransition(0, t);
    for(int i=0;i<count;i++) {
      a.getNextTransition(t);
      builder.addTransition(0, t.dest+1, t.min, t.max);
    }

    int numStates = a.getNumStates();
    for(int s=0;s<numStates;s++) {
      if (a.isAccept(s)) {
        count = a.initTransition(0, t);
        for(int i=0;i<count;i++) {
          a.getNextTransition(t);
          builder.addTransition(s+1, t.dest+1, t.min, t.max);
        }
      }
    }

    return builder.finish();
  }

  /**
   * Returns an automaton that accepts <code>min</code> or more concatenated
   * repetitions of the language of the given automaton.
   * <p>
   * Complexity: linear in number of states and in <code>min</code>.
   */
  static public XAutomaton repeat(XAutomaton a, int min) {
    if (min == 0) {
      return repeat(a);
    }
    List<XAutomaton> as = new ArrayList<>();
    while (min-- > 0) {
      as.add(a);
    }
    as.add(repeat(a));
    return concatenate(as);
  }
  
  /**
   * Returns an automaton that accepts between <code>min</code> and
   * <code>max</code> (including both) concatenated repetitions of the language
   * of the given automaton.
   * <p>
   * Complexity: linear in number of states and in <code>min</code> and
   * <code>max</code>.
   */
  static public XAutomaton repeat(XAutomaton a, int min, int max) {
    if (min > max) {
      return XAutomata.makeEmpty();
    }

    XAutomaton b;
    if (min == 0) {
      b = XAutomata.makeEmptyString();
    } else if (min == 1) {
      b = new XAutomaton();
      b.copy(a);
    } else {
      List<XAutomaton> as = new ArrayList<>();
      for(int i=0;i<min;i++) {
        as.add(a);
      }
      b = concatenate(as);
    }

    Set<Integer> prevAcceptStates = toSet(b, 0);

    for(int i=min;i<max;i++) {
      int numStates = b.getNumStates();
      b.copy(a);
      for(int s : prevAcceptStates) {
        b.addEpsilon(s, numStates);
      }
      prevAcceptStates = toSet(a, numStates);
    }

    b.finishState();

    return b;
  }

  private static Set<Integer> toSet(XAutomaton a, int offset) {
    int numStates = a.getNumStates();
    BitSet isAccept = a.getAcceptStates();
    Set<Integer> result = new HashSet<Integer>();
    int upto = 0;
    while (upto < numStates && (upto = isAccept.nextSetBit(upto)) != -1) {
      result.add(offset+upto);
      upto++;
    }

    return result;
  }
  
  /**
   * Returns a (deterministic) automaton that accepts the complement of the
   * language of the given automaton.
   * <p>
   * Complexity: linear in number of states if already deterministic and
   *  exponential otherwise.
   * @param maxDeterminizedStates maximum number of states determinizing the
   *  automaton can result in.  Set higher to allow more complex queries and
   *  lower to prevent memory exhaustion.
   */
  static public XAutomaton complement(XAutomaton a, int maxDeterminizedStates) {
    a = totalize(determinize(a, maxDeterminizedStates));
    int numStates = a.getNumStates();
    for (int p=0;p<numStates;p++) {
      a.setAccept(p, !a.isAccept(p));
    }
    return removeDeadStates(a);
  }
  
  /**
   * Returns a (deterministic) automaton that accepts the intersection of the
   * language of <code>a1</code> and the complement of the language of
   * <code>a2</code>. As a side-effect, the automata may be determinized, if not
   * already deterministic.
   * <p>
   * Complexity: quadratic in number of states if a2 already deterministic and
   *  exponential in number of a2's states otherwise.
   */
  static public XAutomaton minus(XAutomaton a1, XAutomaton a2, int maxDeterminizedStates) {
    if (XOperations.isEmpty(a1) || a1 == a2) {
      return XAutomata.makeEmpty();
    }
    if (XOperations.isEmpty(a2)) {
      return a1;
    }
    return intersection(a1, complement(a2, maxDeterminizedStates));
  }
  
  /**
   * Returns an automaton that accepts the intersection of the languages of the
   * given automata. Never modifies the input automata languages.
   * <p>
   * Complexity: quadratic in number of states.
   */
  static public XAutomaton intersection(XAutomaton a1, XAutomaton a2) {
    if (a1 == a2) {
      return a1;
    }
    if (a1.getNumStates() == 0) {
      return a1;
    }
    if (a2.getNumStates() == 0) {
      return a2;
    }
    XTransition[][] transitions1 = a1.getSortedTransitions();
    XTransition[][] transitions2 = a2.getSortedTransitions();
    XAutomaton c = new XAutomaton();
    c.createState();
    LinkedList<XStatePair> worklist = new LinkedList<>();
    HashMap<XStatePair,XStatePair> newstates = new HashMap<>();
    XStatePair p = new XStatePair(0, 0, 0);
    worklist.add(p);
    newstates.put(p, p);
    while (worklist.size() > 0) {
      p = worklist.removeFirst();
      c.setAccept(p.s, a1.isAccept(p.s1) && a2.isAccept(p.s2));
      XTransition[] t1 = transitions1[p.s1];
      XTransition[] t2 = transitions2[p.s2];
      for (int n1 = 0, b2 = 0; n1 < t1.length; n1++) {
        while (b2 < t2.length && t2[b2].max < t1[n1].min)
          b2++;
        for (int n2 = b2; n2 < t2.length && t1[n1].max >= t2[n2].min; n2++)
          if (t2[n2].max >= t1[n1].min) {
            XStatePair q = new XStatePair(t1[n1].dest, t2[n2].dest);
            XStatePair r = newstates.get(q);
            if (r == null) {
              q.s = c.createState();
              worklist.add(q);
              newstates.put(q, q);
              r = q;
            }
            int min = t1[n1].min > t2[n2].min ? t1[n1].min : t2[n2].min;
            int max = t1[n1].max < t2[n2].max ? t1[n1].max : t2[n2].max;
            c.addTransition(p.s, r.s, min, max);
          }
      }
    }
    c.finishState();

    return removeDeadStates(c);
  }

  /** Returns true if these two automata accept exactly the
   *  same language.  This is a costly computation!  Note
   *  also that a1 and a2 will be determinized as a side
   *  effect.  Both automata must be determinized and have
   *  no dead states! */
  public static boolean sameLanguage(XAutomaton a1, XAutomaton a2) {
    if (a1 == a2) {
      return true;
    }
    return subsetOf(a2, a1) && subsetOf(a1, a2);
  }

  // TODO: move to test-framework?
  /** Returns true if this automaton has any states that cannot
   *  be reached from the initial state or cannot reach an accept state.
   *  Cost is O(numTransitions+numStates). */
  public static boolean hasDeadStates(XAutomaton a) {
    BitSet liveStates = getLiveStates(a);
    int numLive = liveStates.cardinality();
    int numStates = a.getNumStates();
    assert numLive <= numStates: "numLive=" + numLive + " numStates=" + numStates + " " + liveStates;
    return numLive < numStates;
  }

  // TODO: move to test-framework?
  /** Returns true if there are dead states reachable from an initial state. */
  public static boolean hasDeadStatesFromInitial(XAutomaton a) {
    BitSet reachableFromInitial = getLiveStatesFromInitial(a);
    BitSet reachableFromAccept = getLiveStatesToAccept(a);
    reachableFromInitial.andNot(reachableFromAccept);
    return reachableFromInitial.isEmpty() == false;
  }

  // TODO: move to test-framework?
  /** Returns true if there are dead states that reach an accept state. */
  public static boolean hasDeadStatesToAccept(XAutomaton a) {
    BitSet reachableFromInitial = getLiveStatesFromInitial(a);
    BitSet reachableFromAccept = getLiveStatesToAccept(a);
    reachableFromAccept.andNot(reachableFromInitial);
    return reachableFromAccept.isEmpty() == false;
  }

  /**
   * Returns true if the language of <code>a1</code> is a subset of the language
   * of <code>a2</code>. Both automata must be determinized and must have no dead
   * states.
   * <p>
   * Complexity: quadratic in number of states.
   */
  public static boolean subsetOf(XAutomaton a1, XAutomaton a2) {
    if (a1.isDeterministic() == false) {
      throw new IllegalArgumentException("a1 must be deterministic");
    }
    if (a2.isDeterministic() == false) {
      throw new IllegalArgumentException("a2 must be deterministic");
    }
    assert hasDeadStatesFromInitial(a1) == false;
    assert hasDeadStatesFromInitial(a2) == false;
    if (a1.getNumStates() == 0) {
      // Empty language is alwyas a subset of any other language
      return true;
    } else if (a2.getNumStates() == 0) {
      return isEmpty(a1);
    }

    // TODO: cutover to iterators instead
    XTransition[][] transitions1 = a1.getSortedTransitions();
    XTransition[][] transitions2 = a2.getSortedTransitions();
    LinkedList<XStatePair> worklist = new LinkedList<>();
    HashSet<XStatePair> visited = new HashSet<>();
    XStatePair p = new XStatePair(0, 0);
    worklist.add(p);
    visited.add(p);
    while (worklist.size() > 0) {
      p = worklist.removeFirst();
      if (a1.isAccept(p.s1) && a2.isAccept(p.s2) == false) {
        return false;
      }
      XTransition[] t1 = transitions1[p.s1];
      XTransition[] t2 = transitions2[p.s2];
      for (int n1 = 0, b2 = 0; n1 < t1.length; n1++) {
        while (b2 < t2.length && t2[b2].max < t1[n1].min) {
          b2++;
        }
        int min1 = t1[n1].min, max1 = t1[n1].max;

        for (int n2 = b2; n2 < t2.length && t1[n1].max >= t2[n2].min; n2++) {
          if (t2[n2].min > min1) {
            return false;
          }
          if (t2[n2].max < Character.MAX_CODE_POINT) {
            min1 = t2[n2].max + 1;
          } else {
            min1 = Character.MAX_CODE_POINT;
            max1 = Character.MIN_CODE_POINT;
          }
          XStatePair q = new XStatePair(t1[n1].dest, t2[n2].dest);
          if (!visited.contains(q)) {
            worklist.add(q);
            visited.add(q);
          }
        }
        if (min1 <= max1) {
          return false;
        }
      }
    }
    return true;
  }

  /**
   * Returns an automaton that accepts the union of the languages of the given
   * automata.
   * <p>
   * Complexity: linear in number of states.
   */
  public static XAutomaton union(XAutomaton a1, XAutomaton a2) {
    return union(Arrays.asList(a1, a2));
  }

  /**
   * Returns an automaton that accepts the union of the languages of the given
   * automata.
   * <p>
   * Complexity: linear in number of states.
   */
  public static XAutomaton union(Collection<XAutomaton> l) {
    XAutomaton result = new XAutomaton();

    // Create initial state:
    result.createState();

    // Copy over all automata
    for(XAutomaton a : l) {
      result.copy(a);
    }
    
    // Add epsilon transition from new initial state
    int stateOffset = 1;
    for(XAutomaton a : l) {
      if (a.getNumStates() == 0) {
        continue;
      }
      result.addEpsilon(0, stateOffset);
      stateOffset += a.getNumStates();
    }

    result.finishState();

    return removeDeadStates(result);
  }

  // Simple custom ArrayList<Transition>
  private final static class TransitionList {
    // dest, min, max
    int[] transitions = new int[3];
    int next;

    public void add(XTransition t) {
      if (transitions.length < next+3) {
        transitions = ArrayUtil.grow(transitions, next+3);
      }
      transitions[next] = t.dest;
      transitions[next+1] = t.min;
      transitions[next+2] = t.max;
      next += 3;
    }
  }

  // Holds all transitions that start on this int point, or
  // end at this point-1
  private final static class PointTransitions implements Comparable<PointTransitions> {
    int point;
    final TransitionList ends = new TransitionList();
    final TransitionList starts = new TransitionList();

    @Override
    public int compareTo(PointTransitions other) {
      return point - other.point;
    }

    public void reset(int point) {
      this.point = point;
      ends.next = 0;
      starts.next = 0;
    }

    @Override
    public boolean equals(Object other) {
      return ((PointTransitions) other).point == point;
    }

    @Override
    public int hashCode() {
      return point;
    }
  }

  private final static class PointTransitionSet {
    int count;
    PointTransitions[] points = new PointTransitions[5];

    private final static int HASHMAP_CUTOVER = 30;
    private final HashMap<Integer,PointTransitions> map = new HashMap<>();
    private boolean useHash = false;

    private PointTransitions next(int point) {
      // 1st time we are seeing this point
      if (count == points.length) {
        final PointTransitions[] newArray = new PointTransitions[ArrayUtil.oversize(1+count, RamUsageEstimator.NUM_BYTES_OBJECT_REF)];
        System.arraycopy(points, 0, newArray, 0, count);
        points = newArray;
      }
      PointTransitions points0 = points[count];
      if (points0 == null) {
        points0 = points[count] = new PointTransitions();
      }
      points0.reset(point);
      count++;
      return points0;
    }

    private PointTransitions find(int point) {
      if (useHash) {
        final Integer pi = point;
        PointTransitions p = map.get(pi);
        if (p == null) {
          p = next(point);
          map.put(pi, p);
        }
        return p;
      } else {
        for(int i=0;i<count;i++) {
          if (points[i].point == point) {
            return points[i];
          }
        }

        final PointTransitions p = next(point);
        if (count == HASHMAP_CUTOVER) {
          // switch to HashMap on the fly
          assert map.size() == 0;
          for(int i=0;i<count;i++) {
            map.put(points[i].point, points[i]);
          }
          useHash = true;
        }
        return p;
      }
    }

    public void reset() {
      if (useHash) {
        map.clear();
        useHash = false;
      }
      count = 0;
    }

    public void sort() {
      // Tim sort performs well on already sorted arrays:
      if (count > 1) ArrayUtil.timSort(points, 0, count);
    }

    public void add(XTransition t) {
      find(t.min).starts.add(t);
      find(1+t.max).ends.add(t);
    }

    @Override
    public String toString() {
      StringBuilder s = new StringBuilder();
      for(int i=0;i<count;i++) {
        if (i > 0) {
          s.append(' ');
        }
        s.append(points[i].point).append(':').append(points[i].starts.next/3).append(',').append(points[i].ends.next/3);
      }
      return s.toString();
    }
  }

  /**
   * Determinizes the given automaton.
   * <p>
   * Worst case complexity: exponential in number of states.
   * @param maxDeterminizedStates Maximum number of states created when
   *   determinizing.  Higher numbers allow this operation to consume more
   *   memory but allow more complex automatons.  Use
   *   DEFAULT_MAX_DETERMINIZED_STATES as a decent default if you don't know
   *   how many to allow.
   * @throws XTooComplexToDeterminizeException if determinizing a creates an
   *   automaton with more than maxDeterminizedStates
   */
  public static XAutomaton determinize(XAutomaton a, int maxDeterminizedStates) {
    if (a.isDeterministic()) {
      // Already determinized
      return a;
    }
    if (a.getNumStates() <= 1) {
      // Already determinized
      return a;
    }

    // subset construction
    XAutomaton.Builder b = new XAutomaton.Builder();

    //System.out.println("DET:");
    //a.writeDot("/l/la/lucene/core/detin.dot");

    XSortedIntSet.FrozenIntSet initialset = new XSortedIntSet.FrozenIntSet(0, 0);

    // Create state 0:
    b.createState();

    LinkedList<XSortedIntSet.FrozenIntSet> worklist = new LinkedList<>();
    Map<XSortedIntSet.FrozenIntSet,Integer> newstate = new HashMap<>();

    worklist.add(initialset);

    b.setAccept(0, a.isAccept(0));
    newstate.put(initialset, 0);

    // like Set<Integer,PointTransitions>
    final PointTransitionSet points = new PointTransitionSet();

    // like SortedMap<Integer,Integer>
    final XSortedIntSet statesSet = new XSortedIntSet(5);

    XTransition t = new XTransition();

    while (worklist.size() > 0) {
      XSortedIntSet.FrozenIntSet s = worklist.removeFirst();
      //System.out.println("det: pop set=" + s);

      // Collate all outgoing transitions by min/1+max:
      for(int i=0;i<s.values.length;i++) {
        final int s0 = s.values[i];
        int numTransitions = a.getNumTransitions(s0);
        a.initTransition(s0, t);
        for(int j=0;j<numTransitions;j++) {
          a.getNextTransition(t);
          points.add(t);
        }
      }

      if (points.count == 0) {
        // No outgoing transitions -- skip it
        continue;
      }

      points.sort();

      int lastPoint = -1;
      int accCount = 0;

      final int r = s.state;

      for(int i=0;i<points.count;i++) {

        final int point = points.points[i].point;

        if (statesSet.upto > 0) {
          assert lastPoint != -1;

          statesSet.computeHash();
          
          Integer q = newstate.get(statesSet);
          if (q == null) {
            q = b.createState();
            if (q >= maxDeterminizedStates) {
              throw new XTooComplexToDeterminizeException(a, maxDeterminizedStates);
            }
            final XSortedIntSet.FrozenIntSet p = statesSet.freeze(q);
            //System.out.println("  make new state=" + q + " -> " + p + " accCount=" + accCount);
            worklist.add(p);
            b.setAccept(q, accCount > 0);
            newstate.put(p, q);
          } else {
            assert (accCount > 0 ? true:false) == b.isAccept(q): "accCount=" + accCount + " vs existing accept=" +
              b.isAccept(q) + " states=" + statesSet;
          }

          // System.out.println("  add trans src=" + r + " dest=" + q + " min=" + lastPoint + " max=" + (point-1));

          b.addTransition(r, q, lastPoint, point-1);
        }

        // process transitions that end on this point
        // (closes an overlapping interval)
        int[] transitions = points.points[i].ends.transitions;
        int limit = points.points[i].ends.next;
        for(int j=0;j<limit;j+=3) {
          int dest = transitions[j];
          statesSet.decr(dest);
          accCount -= a.isAccept(dest) ? 1:0;
        }
        points.points[i].ends.next = 0;

        // process transitions that start on this point
        // (opens a new interval)
        transitions = points.points[i].starts.transitions;
        limit = points.points[i].starts.next;
        for(int j=0;j<limit;j+=3) {
          int dest = transitions[j];
          statesSet.incr(dest);
          accCount += a.isAccept(dest) ? 1:0;
        }
        lastPoint = point;
        points.points[i].starts.next = 0;
      }
      points.reset();
      assert statesSet.upto == 0: "upto=" + statesSet.upto;
    }

    XAutomaton result = b.finish();
    assert result.isDeterministic();
    return result;
  }

  /**
   * Returns true if the given automaton accepts no strings.
   */
  public static boolean isEmpty(XAutomaton a) {
    if (a.getNumStates() == 0) {
      // Common case: no states
      return true;
    }
    if (a.isAccept(0) == false && a.getNumTransitions(0) == 0) {
      // Common case: just one initial state
      return true;
    }
    if (a.isAccept(0) == true) {
      // Apparently common case: it accepts the damned empty string
      return false;
    }
    
    LinkedList<Integer> workList = new LinkedList<>();
    BitSet seen = new BitSet(a.getNumStates());
    workList.add(0);
    seen.set(0);

    XTransition t = new XTransition();
    while (workList.isEmpty() == false) {
      int state = workList.removeFirst();
      if (a.isAccept(state)) {
        return false;
      }
      int count = a.initTransition(state, t);
      for(int i=0;i<count;i++) {
        a.getNextTransition(t);
        if (seen.get(t.dest) == false) {
          workList.add(t.dest);
          seen.set(t.dest);
        }
      }
    }

    return true;
  }
  
  /**
   * Returns true if the given automaton accepts all strings.  The automaton must be minimized.
   */
  public static boolean isTotal(XAutomaton a) {
    if (a.isAccept(0) && a.getNumTransitions(0) == 1) {
      XTransition t = new XTransition();
      a.getTransition(0, 0, t);
      return t.dest == 0 && t.min == Character.MIN_CODE_POINT
          && t.max == Character.MAX_CODE_POINT;
    }
    return false;
  }
  
  /**
   * Returns true if the given string is accepted by the automaton.  The input must be deterministic.
   * <p>
   * Complexity: linear in the length of the string.
   * <p>
   * <b>Note:</b> for full performance, use the {@link XRunAutomaton} class.
   */
  public static boolean run(XAutomaton a, String s) {
    assert a.isDeterministic();
    int state = 0;
    for (int i = 0, cp = 0; i < s.length(); i += Character.charCount(cp)) {
      int nextState = a.step(state, cp = s.codePointAt(i));
      if (nextState == -1) {
        return false;
      }
      state = nextState;
    }
    return a.isAccept(state);
  }

  /**
   * Returns true if the given string (expressed as unicode codepoints) is accepted by the automaton.  The input must be deterministic.
   * <p>
   * Complexity: linear in the length of the string.
   * <p>
   * <b>Note:</b> for full performance, use the {@link XRunAutomaton} class.
   */
  public static boolean run(XAutomaton a, IntsRef s) {
    assert a.isDeterministic();
    int state = 0;
    for (int i=0;i<s.length;i++) {
      int nextState = a.step(state, s.ints[s.offset+i]);
      if (nextState == -1) {
        return false;
      }
      state = nextState;
    }
    return a.isAccept(state);
  }

  /**
   * Returns the set of live states. A state is "live" if an accept state is
   * reachable from it and if it is reachable from the initial state.
   */
  private static BitSet getLiveStates(XAutomaton a) {
    BitSet live = getLiveStatesFromInitial(a);
    live.and(getLiveStatesToAccept(a));
    return live;
  }

  /** Returns bitset marking states reachable from the initial state. */
  private static BitSet getLiveStatesFromInitial(XAutomaton a) {
    int numStates = a.getNumStates();
    BitSet live = new BitSet(numStates);
    if (numStates == 0) {
      return live;
    }
    LinkedList<Integer> workList = new LinkedList<>();
    live.set(0);
    workList.add(0);

    XTransition t = new XTransition();
    while (workList.isEmpty() == false) {
      int s = workList.removeFirst();
      int count = a.initTransition(s, t);
      for(int i=0;i<count;i++) {
        a.getNextTransition(t);
        if (live.get(t.dest) == false) {
          live.set(t.dest);
          workList.add(t.dest);
        }
      }
    }

    return live;
  }

  /** Returns bitset marking states that can reach an accept state. */
  private static BitSet getLiveStatesToAccept(XAutomaton a) {
    XAutomaton.Builder builder = new XAutomaton.Builder();

    // NOTE: not quite the same thing as what SpecialOperations.reverse does:
    XTransition t = new XTransition();
    int numStates = a.getNumStates();
    for(int s=0;s<numStates;s++) {
      builder.createState();
    }
    for(int s=0;s<numStates;s++) {
      int count = a.initTransition(s, t);
      for(int i=0;i<count;i++) {
        a.getNextTransition(t);
        builder.addTransition(t.dest, s, t.min, t.max);
      }
    }
    XAutomaton a2 = builder.finish();

    LinkedList<Integer> workList = new LinkedList<>();
    BitSet live = new BitSet(numStates);
    BitSet acceptBits = a.getAcceptStates();
    int s = 0;
    while (s < numStates && (s = acceptBits.nextSetBit(s)) != -1) {
      live.set(s);
      workList.add(s);
      s++;
    }

    while (workList.isEmpty() == false) {
      s = workList.removeFirst();
      int count = a2.initTransition(s, t);
      for(int i=0;i<count;i++) {
        a2.getNextTransition(t);
        if (live.get(t.dest) == false) {
          live.set(t.dest);
          workList.add(t.dest);
        }
      }
    }

    return live;
  }

  /**
   * Removes transitions to dead states (a state is "dead" if it is not
   * reachable from the initial state or no accept state is reachable from it.)
   */
  public static XAutomaton removeDeadStates(XAutomaton a) {
    int numStates = a.getNumStates();
    BitSet liveSet = getLiveStates(a);

    int[] map = new int[numStates];

    XAutomaton result = new XAutomaton();
    //System.out.println("liveSet: " + liveSet + " numStates=" + numStates);
    for(int i=0;i<numStates;i++) {
      if (liveSet.get(i)) {
        map[i] = result.createState();
        result.setAccept(map[i], a.isAccept(i));
      }
    }

    XTransition t = new XTransition();

    for (int i=0;i<numStates;i++) {
      if (liveSet.get(i)) {
        int numTransitions = a.initTransition(i, t);
        // filter out transitions to dead states:
        for(int j=0;j<numTransitions;j++) {
          a.getNextTransition(t);
          if (liveSet.get(t.dest)) {
            result.addTransition(map[i], map[t.dest], t.min, t.max);
          }
        }
      }
    }

    result.finishState();
    assert hasDeadStates(result) == false;
    return result;
  }

  /**
   * Finds the largest entry whose value is less than or equal to c, or 0 if
   * there is no such entry.
   */
  static int findIndex(int c, int[] points) {
    int a = 0;
    int b = points.length;
    while (b - a > 1) {
      int d = (a + b) >>> 1;
      if (points[d] > c) b = d;
      else if (points[d] < c) a = d;
      else return d;
    }
    return a;
  }
  
  /**
   * Returns true if the language of this automaton is finite.  The
   * automaton must not have any dead states.
   */
  public static boolean isFinite(XAutomaton a) {
    if (a.getNumStates() == 0) {
      return true;
    }
    return isFinite(new XTransition(), a, 0, new BitSet(a.getNumStates()), new BitSet(a.getNumStates()));
  }
  
  /**
   * Checks whether there is a loop containing state. (This is sufficient since
   * there are never transitions to dead states.)
   */
  // TODO: not great that this is recursive... in theory a
  // large automata could exceed java's stack
  private static boolean isFinite(XTransition scratch, XAutomaton a, int state, BitSet path, BitSet visited) {
    path.set(state);
    int numTransitions = a.initTransition(state, scratch);
    for(int t=0;t<numTransitions;t++) {
      a.getTransition(state, t, scratch);
      if (path.get(scratch.dest) || (!visited.get(scratch.dest) && !isFinite(scratch, a, scratch.dest, path, visited))) {
        return false;
      }
    }
    path.clear(state);
    visited.set(state);
    return true;
  }
  
  /**
   * Returns the longest string that is a prefix of all accepted strings and
   * visits each state at most once.  The automaton must be deterministic.
   * 
   * @return common prefix
   */
  public static String getCommonPrefix(XAutomaton a) {
    if (a.isDeterministic() == false) {
      throw new IllegalArgumentException("input automaton must be deterministic");
    }
    StringBuilder b = new StringBuilder();
    HashSet<Integer> visited = new HashSet<>();
    int s = 0;
    boolean done;
    XTransition t = new XTransition();
    do {
      done = true;
      visited.add(s);
      if (a.isAccept(s) == false && a.getNumTransitions(s) == 1) {
        a.getTransition(s, 0, t);
        if (t.min == t.max && !visited.contains(t.dest)) {
          b.appendCodePoint(t.min);
          s = t.dest;
          done = false;
        }
      }
    } while (!done);

    return b.toString();
  }
  
  // TODO: this currently requites a determinized machine,
  // but it need not -- we can speed it up by walking the
  // NFA instead.  it'd still be fail fast.
  /**
   * Returns the longest BytesRef that is a prefix of all accepted strings and
   * visits each state at most once.  The automaton must be deterministic.
   * 
   * @return common prefix
   */
  public static BytesRef getCommonPrefixBytesRef(XAutomaton a) {
    XBytesRefBuilder builder = new XBytesRefBuilder();
    HashSet<Integer> visited = new HashSet<>();
    int s = 0;
    boolean done;
    XTransition t = new XTransition();
    do {
      done = true;
      visited.add(s);
      if (a.isAccept(s) == false && a.getNumTransitions(s) == 1) {
        a.getTransition(s, 0, t);
        if (t.min == t.max && !visited.contains(t.dest)) {
          builder.append((byte) t.min);
          s = t.dest;
          done = false;
        }
      }
    } while (!done);

    return builder.get();
  }

  /**
   * Returns the longest BytesRef that is a suffix of all accepted strings.
   * Worst case complexity: exponential in number of states (this calls
   * determinize).
   * @param maxDeterminizedStates maximum number of states determinizing the
   *  automaton can result in.  Set higher to allow more complex queries and
   *  lower to prevent memory exhaustion.
   * @return common suffix
   */
  public static BytesRef getCommonSuffixBytesRef(XAutomaton a, int maxDeterminizedStates) {
    // reverse the language of the automaton, then reverse its common prefix.
    XAutomaton r = XOperations.determinize(reverse(a), maxDeterminizedStates);
    BytesRef ref = getCommonPrefixBytesRef(r);
    reverseBytes(ref);
    return ref;
  }
  
  private static void reverseBytes(BytesRef ref) {
    if (ref.length <= 1) return;
    int num = ref.length >> 1;
    for (int i = ref.offset; i < ( ref.offset + num ); i++) {
      byte b = ref.bytes[i];
      ref.bytes[i] = ref.bytes[ref.offset * 2 + ref.length - i - 1];
      ref.bytes[ref.offset * 2 + ref.length - i - 1] = b;
    }
  }

  /** Returns an automaton accepting the reverse language. */
  public static XAutomaton reverse(XAutomaton a) {
    return reverse(a, null);
  }

  /** Reverses the automaton, returning the new initial states. */
  static XAutomaton reverse(XAutomaton a, Set<Integer> initialStates) {

    if (XOperations.isEmpty(a)) {
      return new XAutomaton();
    }

    int numStates = a.getNumStates();

    // Build a new automaton with all edges reversed
    XAutomaton.Builder builder = new XAutomaton.Builder();

    // Initial node; we'll add epsilon transitions in the end:
    builder.createState();

    for(int s=0;s<numStates;s++) {
      builder.createState();
    }

    // Old initial state becomes new accept state:
    builder.setAccept(1, true);

    XTransition t = new XTransition();
    for (int s=0;s<numStates;s++) {
      int numTransitions = a.getNumTransitions(s);
      a.initTransition(s, t);
      for(int i=0;i<numTransitions;i++) {
        a.getNextTransition(t);
        builder.addTransition(t.dest+1, s+1, t.min, t.max);
      }
    }

    XAutomaton result = builder.finish();

    int s = 0;
    BitSet acceptStates = a.getAcceptStates();
    while (s < numStates && (s = acceptStates.nextSetBit(s)) != -1) {
      result.addEpsilon(0, s+1);
      if (initialStates != null) {
        initialStates.add(s+1);
      }
      s++;
    }

    result.finishState();

    return result;
  }

  private static class PathNode {

    /** Which state the path node ends on, whose
     *  transitions we are enumerating. */
    public int state;

    /** Which state the current transition leads to. */
    public int to;

    /** Which transition we are on. */
    public int transition;

    /** Which label we are on, in the min-max range of the
     *  current Transition */
    public int label;

    private final XTransition t = new XTransition();

    public void resetState(XAutomaton a, int state) {
      assert a.getNumTransitions(state) != 0;
      this.state = state;
      transition = 0;
      a.getTransition(state, 0, t);
      label = t.min;
      to = t.dest;
    }

    /** Returns next label of current transition, or
     *  advances to next transition and returns its first
     *  label, if current one is exhausted.  If there are
     *  no more transitions, returns -1. */
    public int nextLabel(XAutomaton a) {
      if (label > t.max) {
        // We've exhaused the current transition's labels;
        // move to next transitions:
        transition++;
        if (transition >= a.getNumTransitions(state)) {
          // We're done iterating transitions leaving this state
          return -1;
        }
        a.getTransition(state, transition, t);
        label = t.min;
        to = t.dest;
      }
      return label++;
    }
  }

  private static PathNode getNode(PathNode[] nodes, int index) {
    assert index < nodes.length;
    if (nodes[index] == null) {
      nodes[index] = new PathNode();
    }
    return nodes[index];
  }

  // TODO: this is a dangerous method ... Automaton could be
  // huge ... and it's better in general for caller to
  // enumerate & process in a single walk:

  /** Returns the set of accepted strings, up to at most
   *  <code>limit</code> strings. If more than <code>limit</code> 
   *  strings are accepted, the first limit strings found are returned. If <code>limit</code> == -1, then 
   *  the limit is infinite.  If the {@link XAutomaton} has
   *  cycles then this method might throw {@code
   *  IllegalArgumentException} but that is not guaranteed
   *  when the limit is set. */
  public static Set<IntsRef> getFiniteStrings(XAutomaton a, int limit) {
    Set<IntsRef> results = new HashSet<>();

    if (limit == -1 || limit > 0) {
      // OK
    } else {
      throw new IllegalArgumentException("limit must be -1 (which means no limit), or > 0; got: " + limit);
    }

    if (a.isAccept(0)) {
      // Special case the empty string, as usual:
      results.add(new IntsRef());
    }

    if (a.getNumTransitions(0) > 0 && (limit == -1 || results.size() < limit)) {

      int numStates = a.getNumStates();

      // Tracks which states are in the current path, for
      // cycle detection:
      BitSet pathStates = new BitSet(numStates);

      // Stack to hold our current state in the
      // recursion/iteration:
      PathNode[] nodes = new PathNode[4];

      pathStates.set(0);
      PathNode root = getNode(nodes, 0);
      root.resetState(a, 0);

      XIntsRefBuilder string = new XIntsRefBuilder();
      string.append(0);

      while (string.length() > 0) {

        PathNode node = nodes[string.length()-1];

        // Get next label leaving the current node:
        int label = node.nextLabel(a);

        if (label != -1) {
          string.setIntAt(string.length()-1, label);

          if (a.isAccept(node.to)) {
            // This transition leads to an accept state,
            // so we save the current string:
            results.add(string.toIntsRef());
            if (results.size() == limit) {
              break;
            }
          }

          if (a.getNumTransitions(node.to) != 0) {
            // Now recurse: the destination of this transition has
            // outgoing transitions:
            if (pathStates.get(node.to)) {
              throw new IllegalArgumentException("automaton has cycles");
            }
            pathStates.set(node.to);

            // Push node onto stack:
            if (nodes.length == string.length()) {
              PathNode[] newNodes = new PathNode[ArrayUtil.oversize(nodes.length+1, RamUsageEstimator.NUM_BYTES_OBJECT_REF)];
              System.arraycopy(nodes, 0, newNodes, 0, nodes.length);
              nodes = newNodes;
            }
            getNode(nodes, string.length()).resetState(a, node.to);
            string.setLength(string.length() + 1);
            string.grow(string.length());
          }
        } else {
          // No more transitions leaving this state,
          // pop/return back to previous state:
          assert pathStates.get(node.state);
          pathStates.clear(node.state);
          string.setLength(string.length() - 1);
        }
      }
    }

    return results;
  }

  /** Returns a new automaton accepting the same language with added
   *  transitions to a dead state so that from every state and every label
   *  there is a transition. */
  static XAutomaton totalize(XAutomaton a) {
    XAutomaton result = new XAutomaton();
    int numStates = a.getNumStates();
    for(int i=0;i<numStates;i++) {
      result.createState();
      result.setAccept(i, a.isAccept(i));
    }

    int deadState = result.createState();
    result.addTransition(deadState, deadState, Character.MIN_CODE_POINT, Character.MAX_CODE_POINT);

    XTransition t = new XTransition();
    for(int i=0;i<numStates;i++) {
      int maxi = Character.MIN_CODE_POINT;
      int count = a.initTransition(i, t);
      for(int j=0;j<count;j++) {
        a.getNextTransition(t);
        result.addTransition(i, t.dest, t.min, t.max);
        if (t.min > maxi) {
          result.addTransition(i, deadState, maxi, t.min-1);
        }
        if (t.max + 1 > maxi) {
          maxi = t.max + 1;
        }
      }

      if (maxi <= Character.MAX_CODE_POINT) {
        result.addTransition(i, deadState, maxi, Character.MAX_CODE_POINT);
      }
    }

    result.finishState();
    return result;
  }
}
