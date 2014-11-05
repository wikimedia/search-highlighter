package org.apache.lucene.util.automaton;

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import static org.apache.lucene.util.automaton.XOperations.DEFAULT_MAX_DETERMINIZED_STATES;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.IntsRef;
import org.apache.lucene.util.LuceneTestCase;
import org.apache.lucene.util.TestUtil;
import org.apache.lucene.util.XIntsRefBuilder;
import org.apache.lucene.util.XUnicodeUtil;
import org.apache.lucene.util.fst.XUtil;

import com.carrotsearch.randomizedtesting.generators.RandomInts;

public class TestOperations extends LuceneTestCase {
  /** Test string union. */
  public void testStringUnion() {
    List<BytesRef> strings = new ArrayList<>();
    for (int i = RandomInts.randomIntBetween(random(), 0, 1000); --i >= 0;) {
      strings.add(new BytesRef(TestUtil.randomUnicodeString(random())));
    }

    Collections.sort(strings);
    XAutomaton union = XAutomata.makeStringUnion(strings);
    assertTrue(union.isDeterministic());
    assertFalse(XOperations.hasDeadStatesFromInitial(union));
    
    XAutomaton naiveUnion = naiveUnion(strings);
    assertTrue(naiveUnion.isDeterministic());
    assertFalse(XOperations.hasDeadStatesFromInitial(naiveUnion));

    
    assertTrue(XOperations.sameLanguage(union, naiveUnion));
  }

  private static XAutomaton naiveUnion(List<BytesRef> strings) {
    XAutomaton[] eachIndividual = new XAutomaton[strings.size()];
    int i = 0;
    for (BytesRef bref : strings) {
      eachIndividual[i++] = XAutomata.makeString(bref.utf8ToString());
    }
    return XOperations.determinize(XOperations.union(Arrays.asList(eachIndividual)),
      DEFAULT_MAX_DETERMINIZED_STATES);
  }

  /** Test concatenation with empty language returns empty */
  public void testEmptyLanguageConcatenate() {
    XAutomaton a = XAutomata.makeString("a");
    XAutomaton concat = XOperations.concatenate(a, XAutomata.makeEmpty());
    assertTrue(XOperations.isEmpty(concat));
  }
  
  /** Test optimization to concatenate() with empty String to an NFA */
  public void testEmptySingletonNFAConcatenate() {
    XAutomaton singleton = XAutomata.makeString("");
    XAutomaton expandedSingleton = singleton;
    // an NFA (two transitions for 't' from initial state)
    XAutomaton nfa = XOperations.union(XAutomata.makeString("this"),
        XAutomata.makeString("three"));
    XAutomaton concat1 = XOperations.concatenate(expandedSingleton, nfa);
    XAutomaton concat2 = XOperations.concatenate(singleton, nfa);
    assertFalse(concat2.isDeterministic());
    assertTrue(XOperations.sameLanguage(XOperations.determinize(concat1, 100),
                                       XOperations.determinize(concat2, 100)));
    assertTrue(XOperations.sameLanguage(XOperations.determinize(nfa, 100),
                                       XOperations.determinize(concat1, 100)));
    assertTrue(XOperations.sameLanguage(XOperations.determinize(nfa, 100),
                                       XOperations.determinize(concat2, 100)));
  }

  public void testGetRandomAcceptedString() throws Throwable {
    final int ITER1 = atLeast(100);
    final int ITER2 = atLeast(100);
    for(int i=0;i<ITER1;i++) {

      final XRegExp re = new XRegExp(AutomatonTestUtil.randomRegexp(random()), XRegExp.NONE);
      //System.out.println("TEST i=" + i + " re=" + re);
      final XAutomaton a = XOperations.determinize(re.toAutomaton(), DEFAULT_MAX_DETERMINIZED_STATES);
      assertFalse(XOperations.isEmpty(a));

      final AutomatonTestUtil.RandomAcceptedStrings rx = new AutomatonTestUtil.RandomAcceptedStrings(a);
      for(int j=0;j<ITER2;j++) {
        //System.out.println("TEST: j=" + j);
        int[] acc = null;
        try {
          acc = rx.getRandomAcceptedString(random());
          final String s = XUnicodeUtil.newString(acc, 0, acc.length);
          //a.writeDot("adot");
          assertTrue(XOperations.run(a, s));
        } catch (Throwable t) {
//          System.out.println("regexp: " + re);
          if (acc != null) {
//            System.out.println("fail acc re=" + re + " count=" + acc.length);
            for(int k=0;k<acc.length;k++) {
//              System.out.println("  " + Integer.toHexString(acc[k]));
            }
          }
          throw t;
        }
      }
    }
  }
  /**
   * tests against the original brics implementation.
   */
  public void testIsFinite() {
    int num = atLeast(200);
    for (int i = 0; i < num; i++) {
      XAutomaton a = AutomatonTestUtil.randomAutomaton(random());
      assertEquals(AutomatonTestUtil.isFiniteSlow(a), XOperations.isFinite(a));
    }
  }

  /** Pass false for testRecursive if the expected strings
   *  may be too long */
  private Set<IntsRef> getFiniteStrings(XAutomaton a, int limit, boolean testRecursive) {
    Set<IntsRef> result = XOperations.getFiniteStrings(a, limit);
    if (testRecursive) {
      assertEquals(AutomatonTestUtil.getFiniteStringsRecursive(a, limit), result);
    }
    return result;
  }
  
  /**
   * Basic test for getFiniteStrings
   */
  public void testFiniteStringsBasic() {
    XAutomaton a = XOperations.union(XAutomata.makeString("dog"), XAutomata.makeString("duck"));
    a = XMinimizationOperations.minimize(a, DEFAULT_MAX_DETERMINIZED_STATES);
    Set<IntsRef> strings = getFiniteStrings(a, -1, true);
    assertEquals(2, strings.size());
    XIntsRefBuilder dog = new XIntsRefBuilder();
    XUtil.toIntsRef(new BytesRef("dog"), dog);
    assertTrue(strings.contains(dog.get()));
    XIntsRefBuilder duck = new XIntsRefBuilder();
    XUtil.toIntsRef(new BytesRef("duck"), duck);
    assertTrue(strings.contains(duck.get()));
  }

  public void testFiniteStringsEatsStack() {
    char[] chars = new char[50000];
    TestUtil.randomFixedLengthUnicodeString(random(), chars, 0, chars.length);
    String bigString1 = new String(chars);
    TestUtil.randomFixedLengthUnicodeString(random(), chars, 0, chars.length);
    String bigString2 = new String(chars);
    XAutomaton a = XOperations.union(XAutomata.makeString(bigString1), XAutomata.makeString(bigString2));
    Set<IntsRef> strings = getFiniteStrings(a, -1, false);
    assertEquals(2, strings.size());
    XIntsRefBuilder scratch = new XIntsRefBuilder();
    XUtil.toUTF32(bigString1.toCharArray(), 0, bigString1.length(), scratch);
    assertTrue(strings.contains(scratch.get()));
    XUtil.toUTF32(bigString2.toCharArray(), 0, bigString2.length(), scratch);
    assertTrue(strings.contains(scratch.get()));
  }

  public void testRandomFiniteStrings1() {

    int numStrings = atLeast(100);
    if (VERBOSE) {
//      System.out.println("TEST: numStrings=" + numStrings);
    }

    Set<IntsRef> strings = new HashSet<IntsRef>();
    List<XAutomaton> automata = new ArrayList<>();
    XIntsRefBuilder scratch = new XIntsRefBuilder();
    for(int i=0;i<numStrings;i++) {
      String s = TestUtil.randomSimpleString(random(), 1, 200);
      automata.add(XAutomata.makeString(s));
      XUtil.toUTF32(s.toCharArray(), 0, s.length(), scratch);
      strings.add(scratch.toIntsRef());
      if (VERBOSE) {
//        System.out.println("  add string=" + s);
      }
    }

    // TODO: we could sometimes use
    // DaciukMihovAutomatonBuilder here

    // TODO: what other random things can we do here...
    XAutomaton a = XOperations.union(automata);
    if (random().nextBoolean()) {
      a = XMinimizationOperations.minimize(a, 1000000);
      if (VERBOSE) {
//        System.out.println("TEST: a.minimize numStates=" + a.getNumStates());
      }
    } else if (random().nextBoolean()) {
      if (VERBOSE) {
//        System.out.println("TEST: a.determinize");
      }
      a = XOperations.determinize(a, 1000000);
    } else if (random().nextBoolean()) {
      if (VERBOSE) {
//        System.out.println("TEST: a.removeDeadStates");
      }
      a = XOperations.removeDeadStates(a);
    }

    Set<IntsRef> actual = getFiniteStrings(a, -1, true);
    if (strings.equals(actual) == false) {
//      System.out.println("strings.size()=" + strings.size() + " actual.size=" + actual.size());
      List<IntsRef> x = new ArrayList<>(strings);
      Collections.sort(x);
      List<IntsRef> y = new ArrayList<>(actual);
      Collections.sort(y);
      int end = Math.min(x.size(), y.size());
      for(int i=0;i<end;i++) {
//        System.out.println("  i=" + i + " string=" + toString(x.get(i)) + " actual=" + toString(y.get(i)));
      }
      fail("wrong strings found");
    }
  }

  // ascii only!
  private static String toString(IntsRef ints) {
    BytesRef br = new BytesRef(ints.length);
    for(int i=0;i<ints.length;i++) {
      br.bytes[i] = (byte) ints.ints[i];
    }
    br.length = ints.length;
    return br.utf8ToString();
  }

  public void testWithCycle() throws Exception {
    try {
      XOperations.getFiniteStrings(new XRegExp("abc.*", XRegExp.NONE).toAutomaton(), -1);
      fail("did not hit exception");
    } catch (IllegalArgumentException iae) {
      // expected
    }
  }

  public void testRandomFiniteStrings2() {
    // Just makes sure we can run on any random finite
    // automaton:
    int iters = atLeast(100);
    for(int i=0;i<iters;i++) {
      XAutomaton a = AutomatonTestUtil.randomAutomaton(random());
      try {
        // Must pass a limit because the random automaton
        // can accept MANY strings:
        XOperations.getFiniteStrings(a, TestUtil.nextInt(random(), 1, 1000));
        // NOTE: cannot do this, because the method is not
        // guaranteed to detect cycles when you have a limit
        //assertTrue(Operations.isFinite(a));
      } catch (IllegalArgumentException iae) {
        assertFalse(XOperations.isFinite(a));
      }
    }
  }

  public void testInvalidLimit() {
    XAutomaton a = AutomatonTestUtil.randomAutomaton(random());
    try {
      XOperations.getFiniteStrings(a, -7);
      fail("did not hit exception");
    } catch (IllegalArgumentException iae) {
      // expected
    }
  }

  public void testInvalidLimit2() {
    XAutomaton a = AutomatonTestUtil.randomAutomaton(random());
    try {
      XOperations.getFiniteStrings(a, 0);
      fail("did not hit exception");
    } catch (IllegalArgumentException iae) {
      // expected
    }
  }

  public void testSingletonNoLimit() {
    Set<IntsRef> result = XOperations.getFiniteStrings(XAutomata.makeString("foobar"), -1);
    assertEquals(1, result.size());
    XIntsRefBuilder scratch = new XIntsRefBuilder();
    XUtil.toUTF32("foobar".toCharArray(), 0, 6, scratch);
    assertTrue(result.contains(scratch.get()));
  }

  public void testSingletonLimit1() {
    Set<IntsRef> result = XOperations.getFiniteStrings(XAutomata.makeString("foobar"), 1);
    assertEquals(1, result.size());
    XIntsRefBuilder scratch = new XIntsRefBuilder();
    XUtil.toUTF32("foobar".toCharArray(), 0, 6, scratch);
    assertTrue(result.contains(scratch.get()));
  }
}
