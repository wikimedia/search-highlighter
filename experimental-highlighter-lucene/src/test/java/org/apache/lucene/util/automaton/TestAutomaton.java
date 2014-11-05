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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.XBytesRefBuilder;
import org.apache.lucene.util.IntsRef;
import org.apache.lucene.util.XIntsRefBuilder;
import org.apache.lucene.util.LuceneTestCase;
import org.apache.lucene.util.TestUtil;
import org.apache.lucene.util.XUnicodeUtil;
import org.apache.lucene.util.automaton.AutomatonTestUtil.RandomAcceptedStrings;
import org.apache.lucene.util.fst.XUtil;

import static org.apache.lucene.util.automaton.XOperations.DEFAULT_MAX_DETERMINIZED_STATES;

public class TestAutomaton extends LuceneTestCase {

  public void testBasic() throws Exception {
    XAutomaton a = new XAutomaton();
    int start = a.createState();
    int x = a.createState();
    int y = a.createState();
    int end = a.createState();
    a.setAccept(end, true);

    a.addTransition(start, x, 'a', 'a');
    a.addTransition(start, end, 'd', 'd');
    a.addTransition(x, y, 'b', 'b');
    a.addTransition(y, end, 'c', 'c');
    a.finishState();
  }

  public void testReduceBasic() throws Exception {
    XAutomaton a = new XAutomaton();
    int start = a.createState();
    int end = a.createState();
    a.setAccept(end, true);
    // Should collapse to a-b:
    a.addTransition(start, end, 'a', 'a');
    a.addTransition(start, end, 'b', 'b');
    a.addTransition(start, end, 'm', 'm');
    // Should collapse to x-y:
    a.addTransition(start, end, 'x', 'x');
    a.addTransition(start, end, 'y', 'y');

    a.finishState();
    assertEquals(3, a.getNumTransitions(start));
    XTransition scratch = new XTransition();
    a.initTransition(start, scratch);
    a.getNextTransition(scratch);
    assertEquals('a', scratch.min);
    assertEquals('b', scratch.max);
    a.getNextTransition(scratch);
    assertEquals('m', scratch.min);
    assertEquals('m', scratch.max);
    a.getNextTransition(scratch);
    assertEquals('x', scratch.min);
    assertEquals('y', scratch.max);
  }

  public void testSameLanguage() throws Exception {
    XAutomaton a1 = XAutomata.makeString("foobar");
    XAutomaton a2 = XOperations.removeDeadStates(XOperations.concatenate(
                            XAutomata.makeString("foo"),
                            XAutomata.makeString("bar")));
    assertTrue(XOperations.sameLanguage(a1, a2));
  }

  public void testCommonPrefix() throws Exception {
    XAutomaton a = XOperations.concatenate(
                            XAutomata.makeString("foobar"),
                            XAutomata.makeAnyString());
    assertEquals("foobar", XOperations.getCommonPrefix(a));
  }

  public void testConcatenate1() throws Exception {
    XAutomaton a = XOperations.concatenate(
                            XAutomata.makeString("m"),
                            XAutomata.makeAnyString());
    assertTrue(XOperations.run(a, "m"));
    assertTrue(XOperations.run(a, "me"));
    assertTrue(XOperations.run(a, "me too"));
  }

  public void testConcatenate2() throws Exception {
    XAutomaton a = XOperations.concatenate(Arrays.asList(
                            XAutomata.makeString("m"),
                            XAutomata.makeAnyString(),
                            XAutomata.makeString("n"),
                            XAutomata.makeAnyString()));
    a = XOperations.determinize(a, DEFAULT_MAX_DETERMINIZED_STATES);
    assertTrue(XOperations.run(a, "mn"));
    assertTrue(XOperations.run(a, "mone"));
    assertFalse(XOperations.run(a, "m"));
    assertFalse(XOperations.isFinite(a));
  }

  public void testUnion1() throws Exception {
    XAutomaton a = XOperations.union(Arrays.asList(
                            XAutomata.makeString("foobar"),
                            XAutomata.makeString("barbaz")));
    a = XOperations.determinize(a, DEFAULT_MAX_DETERMINIZED_STATES);
    assertTrue(XOperations.run(a, "foobar"));
    assertTrue(XOperations.run(a, "barbaz"));

    assertMatches(a, "foobar", "barbaz");
  }

  public void testUnion2() throws Exception {
    XAutomaton a = XOperations.union(Arrays.asList(
                            XAutomata.makeString("foobar"),
                            XAutomata.makeString(""),
                            XAutomata.makeString("barbaz")));
    a = XOperations.determinize(a, DEFAULT_MAX_DETERMINIZED_STATES);
    assertTrue(XOperations.run(a, "foobar"));
    assertTrue(XOperations.run(a, "barbaz"));
    assertTrue(XOperations.run(a, ""));

    assertMatches(a, "", "foobar", "barbaz");
  }

  public void testMinimizeSimple() throws Exception {
    XAutomaton a = XAutomata.makeString("foobar");
    XAutomaton aMin = XMinimizationOperations.minimize(a, DEFAULT_MAX_DETERMINIZED_STATES);

    assertTrue(XOperations.sameLanguage(a, aMin));
  }

  public void testMinimize2() throws Exception {
    XAutomaton a = XOperations.union(Arrays.asList(XAutomata.makeString("foobar"),
                                                           XAutomata.makeString("boobar")));
    XAutomaton aMin = XMinimizationOperations.minimize(a, DEFAULT_MAX_DETERMINIZED_STATES);
    assertTrue(XOperations.sameLanguage(XOperations.determinize(
      XOperations.removeDeadStates(a), DEFAULT_MAX_DETERMINIZED_STATES), aMin));
  }

  public void testReverse() throws Exception {
    XAutomaton a = XAutomata.makeString("foobar");
    XAutomaton ra = XOperations.reverse(a);
    XAutomaton a2 = XOperations.determinize(XOperations.reverse(ra),
      DEFAULT_MAX_DETERMINIZED_STATES);
    
    assertTrue(XOperations.sameLanguage(a, a2));
  }

  public void testOptional() throws Exception {
    XAutomaton a = XAutomata.makeString("foobar");
    XAutomaton a2 = XOperations.optional(a);
    a2 = XOperations.determinize(a2, DEFAULT_MAX_DETERMINIZED_STATES);
    
    assertTrue(XOperations.run(a, "foobar"));
    assertFalse(XOperations.run(a, ""));
    assertTrue(XOperations.run(a2, "foobar"));
    assertTrue(XOperations.run(a2, ""));
  }

  public void testRepeatAny() throws Exception {
    XAutomaton a = XAutomata.makeString("zee");
    XAutomaton a2 = XOperations.determinize(XOperations.repeat(a),
      DEFAULT_MAX_DETERMINIZED_STATES);
    assertTrue(XOperations.run(a2, ""));
    assertTrue(XOperations.run(a2, "zee"));    
    assertTrue(XOperations.run(a2, "zeezee"));
    assertTrue(XOperations.run(a2, "zeezeezee"));
  }

  public void testRepeatMin() throws Exception {
    XAutomaton a = XAutomata.makeString("zee");
    XAutomaton a2 = XOperations.determinize(XOperations.repeat(a, 2),
      DEFAULT_MAX_DETERMINIZED_STATES);
    assertFalse(XOperations.run(a2, ""));
    assertFalse(XOperations.run(a2, "zee"));    
    assertTrue(XOperations.run(a2, "zeezee"));
    assertTrue(XOperations.run(a2, "zeezeezee"));
  }

  public void testRepeatMinMax1() throws Exception {
    XAutomaton a = XAutomata.makeString("zee");
    XAutomaton a2 = XOperations.determinize(XOperations.repeat(a, 0, 2),
      DEFAULT_MAX_DETERMINIZED_STATES);
    assertTrue(XOperations.run(a2, ""));
    assertTrue(XOperations.run(a2, "zee"));    
    assertTrue(XOperations.run(a2, "zeezee"));
    assertFalse(XOperations.run(a2, "zeezeezee"));
  }

  public void testRepeatMinMax2() throws Exception {
    XAutomaton a = XAutomata.makeString("zee");
    XAutomaton a2 = XOperations.determinize(XOperations.repeat(a, 2, 4),
      DEFAULT_MAX_DETERMINIZED_STATES);
    assertFalse(XOperations.run(a2, ""));
    assertFalse(XOperations.run(a2, "zee"));    
    assertTrue(XOperations.run(a2, "zeezee"));
    assertTrue(XOperations.run(a2, "zeezeezee"));
    assertTrue(XOperations.run(a2, "zeezeezeezee"));
    assertFalse(XOperations.run(a2, "zeezeezeezeezee"));
  }

  public void testComplement() throws Exception {
    XAutomaton a = XAutomata.makeString("zee");
    XAutomaton a2 = XOperations.determinize(XOperations.complement(a,
      DEFAULT_MAX_DETERMINIZED_STATES), DEFAULT_MAX_DETERMINIZED_STATES);
    assertTrue(XOperations.run(a2, ""));
    assertFalse(XOperations.run(a2, "zee"));    
    assertTrue(XOperations.run(a2, "zeezee"));
    assertTrue(XOperations.run(a2, "zeezeezee"));
  }

  public void testInterval() throws Exception {
    XAutomaton a = XOperations.determinize(XAutomata.makeInterval(17, 100, 3),
      DEFAULT_MAX_DETERMINIZED_STATES);
    assertFalse(XOperations.run(a, ""));
    assertTrue(XOperations.run(a, "017"));
    assertTrue(XOperations.run(a, "100"));
    assertTrue(XOperations.run(a, "073"));
  }

  public void testCommonSuffix() throws Exception {
    XAutomaton a = new XAutomaton();
    int init = a.createState();
    int fini = a.createState();
    a.setAccept(init, true);
    a.setAccept(fini, true);
    a.addTransition(init, fini, 'm');
    a.addTransition(fini, fini, 'm');
    a.finishState();
    assertEquals(0, XOperations.getCommonSuffixBytesRef(a,
      DEFAULT_MAX_DETERMINIZED_STATES).length);
  }

  public void testReverseRandom1() throws Exception {
    int ITERS = atLeast(100);
    for(int i=0;i<ITERS;i++) {
      XAutomaton a = AutomatonTestUtil.randomAutomaton(random());
      XAutomaton ra = XOperations.reverse(a);
      XAutomaton rra = XOperations.reverse(ra);
      assertTrue(XOperations.sameLanguage(
        XOperations.determinize(XOperations.removeDeadStates(a), DEFAULT_MAX_DETERMINIZED_STATES),
        XOperations.determinize(XOperations.removeDeadStates(rra), DEFAULT_MAX_DETERMINIZED_STATES)));
    }
  }

  public void testReverseRandom2() throws Exception {
    int ITERS = atLeast(100);
    for(int iter=0;iter<ITERS;iter++) {
      //System.out.println("TEST: iter=" + iter);
      XAutomaton a = AutomatonTestUtil.randomAutomaton(random());
      if (random().nextBoolean()) {
        a = XOperations.removeDeadStates(a);
      }
      XAutomaton ra = XOperations.reverse(a);
      XAutomaton rda = XOperations.determinize(ra, DEFAULT_MAX_DETERMINIZED_STATES);

      if (XOperations.isEmpty(a)) {
        assertTrue(XOperations.isEmpty(rda));
        continue;
      }

      RandomAcceptedStrings ras = new RandomAcceptedStrings(a);

      for(int iter2=0;iter2<20;iter2++) {
        // Find string accepted by original automaton
        int[] s = ras.getRandomAcceptedString(random());

        // Reverse it
        for(int j=0;j<s.length/2;j++) {
          int x = s[j];
          s[j] = s[s.length-j-1];
          s[s.length-j-1] = x;
        }
        //System.out.println("TEST:   iter2=" + iter2 + " s=" + Arrays.toString(s));

        // Make sure reversed automaton accepts it
        assertTrue(XOperations.run(rda, new IntsRef(s, 0, s.length)));
      }
    }
  }

  public void testAnyStringEmptyString() throws Exception {
    XAutomaton a = XOperations.determinize(XAutomata.makeAnyString(),
      DEFAULT_MAX_DETERMINIZED_STATES);
    assertTrue(XOperations.run(a, ""));
  }

  public void testBasicIsEmpty() throws Exception {
    XAutomaton a = new XAutomaton();
    a.createState();
    assertTrue(XOperations.isEmpty(a));
  }

  public void testRemoveDeadTransitionsEmpty() throws Exception {
    XAutomaton a = XAutomata.makeEmpty();
    XAutomaton a2 = XOperations.removeDeadStates(a);
    assertTrue(XOperations.isEmpty(a2));
  }

  public void testInvalidAddTransition() throws Exception {
    XAutomaton a = new XAutomaton();
    int s1 = a.createState();
    int s2 = a.createState();
    a.addTransition(s1, s2, 'a');
    a.addTransition(s2, s2, 'a');
    try {
      a.addTransition(s1, s2, 'b');
      fail("didn't hit expected exception");
    } catch (IllegalStateException ise) {
      // expected
    }
  }

  public void testBuilderRandom() throws Exception {
    int ITERS = atLeast(100);
    for(int iter=0;iter<ITERS;iter++) {
      XAutomaton a = AutomatonTestUtil.randomAutomaton(random());

      // Just get all transitions, shuffle, and build a new automaton with the same transitions:
      List<XTransition> allTrans = new ArrayList<>();
      int numStates = a.getNumStates();
      for(int s=0;s<numStates;s++) {
        int count = a.getNumTransitions(s);
        for(int i=0;i<count;i++) {
          XTransition t = new XTransition();
          a.getTransition(s, i, t);
          allTrans.add(t);
        }
      }

      XAutomaton.Builder builder = new XAutomaton.Builder();
      for(int i=0;i<numStates;i++) {
        int s = builder.createState();
        builder.setAccept(s, a.isAccept(s));
      }

      Collections.shuffle(allTrans, random());
      for(XTransition t : allTrans) {
        builder.addTransition(t.source, t.dest, t.min, t.max);
      }

      assertTrue(XOperations.sameLanguage(
        XOperations.determinize(XOperations.removeDeadStates(a), DEFAULT_MAX_DETERMINIZED_STATES),
        XOperations.determinize(XOperations.removeDeadStates(builder.finish()),
          DEFAULT_MAX_DETERMINIZED_STATES)));
    }
  }

  public void testIsTotal() throws Exception {
    assertFalse(XOperations.isTotal(new XAutomaton()));
    XAutomaton a = new XAutomaton();
    int init = a.createState();
    int fini = a.createState();
    a.setAccept(fini, true);
    a.addTransition(init, fini, Character.MIN_CODE_POINT, Character.MAX_CODE_POINT);
    a.finishState();
    assertFalse(XOperations.isTotal(a));
    a.addTransition(fini, fini, Character.MIN_CODE_POINT, Character.MAX_CODE_POINT);
    a.finishState();
    assertFalse(XOperations.isTotal(a));
    a.setAccept(init, true);
    assertTrue(XOperations.isTotal(XMinimizationOperations.minimize(a,
      DEFAULT_MAX_DETERMINIZED_STATES)));
  }

  public void testMinimizeEmpty() throws Exception {
    XAutomaton a = new XAutomaton();
    int init = a.createState();
    int fini = a.createState();
    a.addTransition(init, fini, 'a');
    a.finishState();
    a = XMinimizationOperations.minimize(a, DEFAULT_MAX_DETERMINIZED_STATES);
    assertEquals(0, a.getNumStates());
  }

  public void testMinus() throws Exception {
    XAutomaton a1 = XAutomata.makeString("foobar");
    XAutomaton a2 = XAutomata.makeString("boobar");
    XAutomaton a3 = XAutomata.makeString("beebar");
    XAutomaton a = XOperations.union(Arrays.asList(a1, a2, a3));
    if (random().nextBoolean()) {
      a = XOperations.determinize(a, DEFAULT_MAX_DETERMINIZED_STATES);
    } else if (random().nextBoolean()) {
      a = XMinimizationOperations.minimize(a, DEFAULT_MAX_DETERMINIZED_STATES);
    }
    assertMatches(a, "foobar", "beebar", "boobar");

    XAutomaton a4 = XOperations.determinize(XOperations.minus(a, a2,
      DEFAULT_MAX_DETERMINIZED_STATES), DEFAULT_MAX_DETERMINIZED_STATES);
    
    assertTrue(XOperations.run(a4, "foobar"));
    assertFalse(XOperations.run(a4, "boobar"));
    assertTrue(XOperations.run(a4, "beebar"));
    assertMatches(a4, "foobar", "beebar");

    a4 = XOperations.determinize(XOperations.minus(a4, a1,
      DEFAULT_MAX_DETERMINIZED_STATES), DEFAULT_MAX_DETERMINIZED_STATES);
    assertFalse(XOperations.run(a4, "foobar"));
    assertFalse(XOperations.run(a4, "boobar"));
    assertTrue(XOperations.run(a4, "beebar"));
    assertMatches(a4, "beebar");

    a4 = XOperations.determinize(XOperations.minus(a4, a3,
      DEFAULT_MAX_DETERMINIZED_STATES), DEFAULT_MAX_DETERMINIZED_STATES);
    assertFalse(XOperations.run(a4, "foobar"));
    assertFalse(XOperations.run(a4, "boobar"));
    assertFalse(XOperations.run(a4, "beebar"));
    assertMatches(a4);
  }

  public void testOneInterval() throws Exception {
    XAutomaton a = XAutomata.makeInterval(999, 1032, 0);
    a = XOperations.determinize(a, DEFAULT_MAX_DETERMINIZED_STATES);
    assertTrue(XOperations.run(a, "0999"));
    assertTrue(XOperations.run(a, "00999"));
    assertTrue(XOperations.run(a, "000999"));
  }

  public void testAnotherInterval() throws Exception {
    XAutomaton a = XAutomata.makeInterval(1, 2, 0);
    a = XOperations.determinize(a, DEFAULT_MAX_DETERMINIZED_STATES);
    assertTrue(XOperations.run(a, "01"));
  }

  public void testIntervalRandom() throws Exception {
    int ITERS = atLeast(100);
    for(int iter=0;iter<ITERS;iter++) {
      int min = TestUtil.nextInt(random(), 0, 100000);
      int max = TestUtil.nextInt(random(), min, min+100000);
      int digits;
      if (random().nextBoolean()) {
        digits = 0;
      } else {
        String s = Integer.toString(max);
        digits = TestUtil.nextInt(random(), s.length(), 2*s.length());
      }
      StringBuilder b = new StringBuilder();
      for(int i=0;i<digits;i++) {
        b.append('0');
      }
      String prefix = b.toString();

      XAutomaton a = XOperations.determinize(XAutomata.makeInterval(min, max, digits),
        DEFAULT_MAX_DETERMINIZED_STATES);
      if (random().nextBoolean()) {
        a = XMinimizationOperations.minimize(a, DEFAULT_MAX_DETERMINIZED_STATES);
      }
      String mins = Integer.toString(min);
      String maxs = Integer.toString(max);
      if (digits > 0) {
        mins = prefix.substring(mins.length()) + mins;
        maxs = prefix.substring(maxs.length()) + maxs;
      }
      assertTrue(XOperations.run(a, mins));
      assertTrue(XOperations.run(a, maxs));

      for(int iter2=0;iter2<100;iter2++) {
        int x = random().nextInt(2*max);
        boolean expected = x >= min && x <= max;
        String sx = Integer.toString(x);
        if (sx.length() < digits) {
          // Left-fill with 0s
          sx = b.substring(sx.length()) + sx;
        } else if (digits == 0) {
          // Left-fill with random number of 0s:
          int numZeros = random().nextInt(10);
          StringBuilder sb = new StringBuilder();
          for(int i=0;i<numZeros;i++) {
            sb.append('0');
          }
          sb.append(sx);
          sx = sb.toString();
        }
        assertEquals(expected, XOperations.run(a, sx));
      }
    }
  }

  private void assertMatches(XAutomaton a, String... strings) {
    Set<IntsRef> expected = new HashSet<>();
    for(String s : strings) {
      XIntsRefBuilder ints = new XIntsRefBuilder();
      expected.add(XUtil.toUTF32(s, ints));
    }

    assertEquals(expected, XOperations.getFiniteStrings(XOperations.determinize(a,
      DEFAULT_MAX_DETERMINIZED_STATES), -1)); 
  }

  public void testConcatenatePreservesDet() throws Exception {
    XAutomaton a1 = XAutomata.makeString("foobar");
    assertTrue(a1.isDeterministic());
    XAutomaton a2 = XAutomata.makeString("baz");
    assertTrue(a2.isDeterministic());
    assertTrue((XOperations.concatenate(Arrays.asList(a1, a2)).isDeterministic()));
  }

  public void testRemoveDeadStates() throws Exception {
    XAutomaton a = XOperations.concatenate(Arrays.asList(XAutomata.makeString("x"),
                                                                      XAutomata.makeString("y")));
    assertEquals(4, a.getNumStates());
    a = XOperations.removeDeadStates(a);
    assertEquals(3, a.getNumStates());
  }

  public void testRemoveDeadStatesEmpty1() throws Exception {
    XAutomaton a = new XAutomaton();
    a.finishState();
    assertTrue(XOperations.isEmpty(a));
    assertTrue(XOperations.isEmpty(XOperations.removeDeadStates(a)));
  }

  public void testRemoveDeadStatesEmpty2() throws Exception {
    XAutomaton a = new XAutomaton();
    a.finishState();
    assertTrue(XOperations.isEmpty(a));
    assertTrue(XOperations.isEmpty(XOperations.removeDeadStates(a)));
  }

  public void testRemoveDeadStatesEmpty3() throws Exception {
    XAutomaton a = new XAutomaton();
    int init = a.createState();
    int fini = a.createState();
    a.addTransition(init, fini, 'a');
    XAutomaton a2 = XOperations.removeDeadStates(a);
    assertEquals(0, a2.getNumStates());
  }

  public void testConcatEmpty() throws Exception {
    // If you concat empty automaton to anything the result should still be empty:
    XAutomaton a = XOperations.concatenate(XAutomata.makeEmpty(),
                                                        XAutomata.makeString("foo"));
    assertEquals(new HashSet<IntsRef>(), XOperations.getFiniteStrings(a, -1));

    a = XOperations.concatenate(XAutomata.makeString("foo"),
                                         XAutomata.makeEmpty());
    assertEquals(new HashSet<IntsRef>(), XOperations.getFiniteStrings(a, -1));
  }

  public void testSeemsNonEmptyButIsNot1() throws Exception {
    XAutomaton a = new XAutomaton();
    // Init state has a transition but doesn't lead to accept
    int init = a.createState();
    int s = a.createState();
    a.addTransition(init, s, 'a');
    a.finishState();
    assertTrue(XOperations.isEmpty(a));
  }

  public void testSeemsNonEmptyButIsNot2() throws Exception {
    XAutomaton a = new XAutomaton();
    int init = a.createState();
    int s = a.createState();
    a.addTransition(init, s, 'a');
    // An orphan'd accept state
    s = a.createState();
    a.setAccept(s, true);
    a.finishState();
    assertTrue(XOperations.isEmpty(a));
  }

  public void testSameLanguage1() throws Exception {
    XAutomaton a = XAutomata.makeEmptyString();
    XAutomaton a2 = XAutomata.makeEmptyString();
    int state = a2.createState();
    a2.addTransition(0, state, 'a');
    a2.finishState();
    assertTrue(XOperations.sameLanguage(XOperations.removeDeadStates(a),
                                            XOperations.removeDeadStates(a2)));
  }

  private XAutomaton randomNoOp(XAutomaton a) {
    switch (random().nextInt(7)) {
    case 0:
      if (VERBOSE) {
//        System.out.println("  randomNoOp: determinize");
      }
      return XOperations.determinize(a, DEFAULT_MAX_DETERMINIZED_STATES);
    case 1:
      if (a.getNumStates() < 100) {
        if (VERBOSE) {
//          System.out.println("  randomNoOp: minimize");
        }
        return XMinimizationOperations.minimize(a, DEFAULT_MAX_DETERMINIZED_STATES);
      } else {
        if (VERBOSE) {
//          System.out.println("  randomNoOp: skip op=minimize: too many states (" + a.getNumStates() + ")");
        }
        return a;
      }
    case 2:
      if (VERBOSE) {
//        System.out.println("  randomNoOp: removeDeadStates");
      }
      return XOperations.removeDeadStates(a);
    case 3:
      if (VERBOSE) {
//        System.out.println("  randomNoOp: reverse reverse");
      }
      a = XOperations.reverse(a);
      a = randomNoOp(a);
      return XOperations.reverse(a);
    case 4:
      if (VERBOSE) {
//        System.out.println("  randomNoOp: concat empty string");
      }
      return XOperations.concatenate(a, XAutomata.makeEmptyString());
    case 5:
      if (VERBOSE) {
//        System.out.println("  randomNoOp: union empty automaton");
      }
      return XOperations.union(a, XAutomata.makeEmpty());
    case 6:
      if (VERBOSE) {
//        System.out.println("  randomNoOp: do nothing!");
      }
      return a;
    }
    assert false;
    return null;
  }

  private XAutomaton unionTerms(Collection<BytesRef> terms) {
    XAutomaton a;
    if (random().nextBoolean()) {
      if (VERBOSE) {
//        System.out.println("TEST: unionTerms: use union");
      }
      List<XAutomaton> as = new ArrayList<>();
      for(BytesRef term : terms) {
        as.add(XAutomata.makeString(term.utf8ToString()));
      }
      a = XOperations.union(as);
    } else {
      if (VERBOSE) {
//        System.out.println("TEST: unionTerms: use makeStringUnion");
      }
      List<BytesRef> termsList = new ArrayList<>(terms);
      Collections.sort(termsList);
      a = XAutomata.makeStringUnion(termsList);
    }

    return randomNoOp(a);
  }

  private String getRandomString() {
    //return TestUtil.randomSimpleString(random());
    return TestUtil.randomRealisticUnicodeString(random());
  }

  public void testRandomFinite() throws Exception {

    int numTerms = atLeast(10);
    int iters = atLeast(100);

    if (VERBOSE) {
//      System.out.println("TEST: numTerms=" + numTerms + " iters=" + iters);
    }

    Set<BytesRef> terms = new HashSet<>();
    while (terms.size() < numTerms) {
      terms.add(new BytesRef(getRandomString()));
    }

    XAutomaton a = unionTerms(terms);
    assertSame(terms, a);

    for(int iter=0;iter<iters;iter++) {
      if (VERBOSE) {
//        System.out.println("TEST: iter=" + iter + " numTerms=" + terms.size() + " a.numStates=" + a.getNumStates());
        /*
        System.out.println("  terms:");
        for(BytesRef term : terms) {
          System.out.println("    " + term);
        }
        */
      }
      switch(random().nextInt(15)) {

      case 0:
        // concatenate prefix
        {
          if (VERBOSE) {
//            System.out.println("  op=concat prefix");
          }
          Set<BytesRef> newTerms = new HashSet<>();
          BytesRef prefix = new BytesRef(getRandomString());
          XBytesRefBuilder newTerm = new XBytesRefBuilder();
          for(BytesRef term : terms) {
            newTerm.copyBytes(prefix);
            newTerm.append(term);
            newTerms.add(newTerm.toBytesRef());
          }
          terms = newTerms;
          boolean wasDeterministic1 = a.isDeterministic();
          a = XOperations.concatenate(XAutomata.makeString(prefix.utf8ToString()), a);
          assertEquals(wasDeterministic1, a.isDeterministic());
        }
        break;

      case 1:
        // concatenate suffix
        {
          BytesRef suffix = new BytesRef(getRandomString());
          if (VERBOSE) {
//            System.out.println("  op=concat suffix " + suffix);
          }
          Set<BytesRef> newTerms = new HashSet<>();
          XBytesRefBuilder newTerm = new XBytesRefBuilder();
          for(BytesRef term : terms) {
            newTerm.copyBytes(term);
            newTerm.append(suffix);
            newTerms.add(newTerm.toBytesRef());
          }
          terms = newTerms;
          a = XOperations.concatenate(a, XAutomata.makeString(suffix.utf8ToString()));
        }
        break;

      case 2:
        // determinize
        if (VERBOSE) {
//          System.out.println("  op=determinize");
        }
        a = XOperations.determinize(a, DEFAULT_MAX_DETERMINIZED_STATES);
        assertTrue(a.isDeterministic());
        break;

      case 3:
        if (a.getNumStates() < 100) {
          if (VERBOSE) {
//            System.out.println("  op=minimize");
          }
          // minimize
          a = XMinimizationOperations.minimize(a, DEFAULT_MAX_DETERMINIZED_STATES);
        } else if (VERBOSE) {
//          System.out.println("  skip op=minimize: too many states (" + a.getNumStates() + ")");
        }
        break;

      case 4:
        // union
        {
          if (VERBOSE) {
//            System.out.println("  op=union");
          }
          Set<BytesRef> newTerms = new HashSet<>();
          int numNewTerms = random().nextInt(5);
          while (newTerms.size() < numNewTerms) {
            newTerms.add(new BytesRef(getRandomString()));
          }
          terms.addAll(newTerms);
          XAutomaton newA = unionTerms(newTerms);
          a = XOperations.union(a, newA);
        }
        break;

      case 5:
        // optional
        {
          if (VERBOSE) {
//            System.out.println("  op=optional");
          }
          a = XOperations.optional(a);
          terms.add(new BytesRef());
        }
        break;

      case 6:
        // minus finite 
        {
          if (VERBOSE) {
//            System.out.println("  op=minus finite");
          }
          if (terms.size() > 0) {
            RandomAcceptedStrings rasl = new RandomAcceptedStrings(XOperations.removeDeadStates(a));
            Set<BytesRef> toRemove = new HashSet<>();
            int numToRemove = TestUtil.nextInt(random(), 1, (terms.size()+1)/2);
            while (toRemove.size() < numToRemove) {
              int[] ints = rasl.getRandomAcceptedString(random());
              BytesRef term = new BytesRef(XUnicodeUtil.newString(ints, 0, ints.length));
              if (toRemove.contains(term) == false) {
                toRemove.add(term);
              }
            }
            for(BytesRef term : toRemove) {
              boolean removed = terms.remove(term);
              assertTrue(removed);
            }
            XAutomaton a2 = unionTerms(toRemove);
            a = XOperations.minus(a, a2, DEFAULT_MAX_DETERMINIZED_STATES);
          }
        }
        break;

      case 7:
        {
          // minus infinite
          List<XAutomaton> as = new ArrayList<>();
          int count = TestUtil.nextInt(random(), 1, 5);
          Set<Integer> prefixes = new HashSet<>();
          while(prefixes.size() < count) {
            // prefix is a leading ascii byte; we remove <prefix>* from a
            int prefix = random().nextInt(128);
            prefixes.add(prefix);
          }

          if (VERBOSE) {
//            System.out.println("  op=minus infinite prefixes=" + prefixes);
          }

          for(int prefix : prefixes) {
            // prefix is a leading ascii byte; we remove <prefix>* from a
            XAutomaton a2 = new XAutomaton();
            int init = a2.createState();
            int state = a2.createState();
            a2.addTransition(init, state, prefix);
            a2.setAccept(state, true);
            a2.addTransition(state, state, Character.MIN_CODE_POINT, Character.MAX_CODE_POINT);
            a2.finishState();
            as.add(a2);
            Iterator<BytesRef> it = terms.iterator();
            while (it.hasNext()) {
              BytesRef term = it.next();
              if (term.length > 0 && (term.bytes[term.offset] & 0xFF) == prefix) {
                it.remove();
              }
            }
          }
          XAutomaton a2 = randomNoOp(XOperations.union(as));
          a = XOperations.minus(a, a2, DEFAULT_MAX_DETERMINIZED_STATES);
        }
        break;

      case 8:
        {
          int count = TestUtil.nextInt(random(), 10, 20);
          if (VERBOSE) {
//            System.out.println("  op=intersect infinite count=" + count);
          }
          // intersect infinite
          List<XAutomaton> as = new ArrayList<>();

          Set<Integer> prefixes = new HashSet<>();
          while(prefixes.size() < count) {
            int prefix = random().nextInt(128);
            prefixes.add(prefix);
          }
          if (VERBOSE) {
//            System.out.println("  prefixes=" + prefixes);
          }

          for(int prefix : prefixes) {
            // prefix is a leading ascii byte; we retain <prefix>* in a
            XAutomaton a2 = new XAutomaton();
            int init = a2.createState();
            int state = a2.createState();
            a2.addTransition(init, state, prefix);
            a2.setAccept(state, true);
            a2.addTransition(state, state, Character.MIN_CODE_POINT, Character.MAX_CODE_POINT);
            a2.finishState();
            as.add(a2);
            prefixes.add(prefix);
          }

          XAutomaton a2 = XOperations.union(as);
          if (random().nextBoolean()) {
            a2 = XOperations.determinize(a2, DEFAULT_MAX_DETERMINIZED_STATES);
          } else if (random().nextBoolean()) {
            a2 = XMinimizationOperations.minimize(a2, DEFAULT_MAX_DETERMINIZED_STATES);
          }
          a = XOperations.intersection(a, a2);

          Iterator<BytesRef> it = terms.iterator();
          while (it.hasNext()) {
            BytesRef term = it.next();
            if (term.length == 0 || prefixes.contains(term.bytes[term.offset]&0xff) == false) {
              if (VERBOSE) {
//                System.out.println("  drop term=" + term);
              }
              it.remove();
            } else {
              if (VERBOSE) {
//                System.out.println("  keep term=" + term);
              }
            }
          }
        }        
        break;

      case 9:
        // reverse
        {
          if (VERBOSE) {
//            System.out.println("  op=reverse");
          }
          a = XOperations.reverse(a);
          Set<BytesRef> newTerms = new HashSet<>();
          for(BytesRef term : terms) {
            newTerms.add(new BytesRef(new StringBuilder(term.utf8ToString()).reverse().toString()));
          }
          terms = newTerms;
        }
        break;

      case 10:
        if (VERBOSE) {
//          System.out.println("  op=randomNoOp");
        }
        a = randomNoOp(a);
        break;

      case 11:
        // interval
        {
          int min = random().nextInt(1000);
          int max = min + random().nextInt(50);
          // digits must be non-zero else we make cycle
          int digits = Integer.toString(max).length();
          if (VERBOSE) {
//            System.out.println("  op=union interval min=" + min + " max=" + max + " digits=" + digits);
          }
          a = XOperations.union(a, XAutomata.makeInterval(min, max, digits));
          StringBuilder b = new StringBuilder();
          for(int i=0;i<digits;i++) {
            b.append('0');
          }
          String prefix = b.toString();
          for(int i=min;i<=max;i++) {
            String s = Integer.toString(i);
            if (s.length() < digits) {
              // Left-fill with 0s
              s = prefix.substring(s.length()) + s;
            }
            terms.add(new BytesRef(s));
          }
        }
        break;

      case 12:
        if (VERBOSE) {
//          System.out.println("  op=remove the empty string");
        }
        a = XOperations.minus(a, XAutomata.makeEmptyString(), DEFAULT_MAX_DETERMINIZED_STATES);
        terms.remove(new BytesRef());
        break;

      case 13:
        if (VERBOSE) {
//          System.out.println("  op=add the empty string");
        }
        a = XOperations.union(a, XAutomata.makeEmptyString());
        terms.add(new BytesRef());
        break;

      case 14:
        // Safety in case we are really unlucky w/ the dice:
        if (terms.size() <= numTerms * 3) {
          if (VERBOSE) {
//            System.out.println("  op=concat finite automaton");
          }
          int count = random().nextBoolean() ? 2 : 3;
          Set<BytesRef> addTerms = new HashSet<>();
          while (addTerms.size() < count) {
            addTerms.add(new BytesRef(getRandomString()));
          }
          if (VERBOSE) {
            for(BytesRef term : addTerms) {
//              System.out.println("    term=" + term);
            }
          }
          XAutomaton a2 = unionTerms(addTerms);
          Set<BytesRef> newTerms = new HashSet<>();
          if (random().nextBoolean()) {
            // suffix
            if (VERBOSE) {
//              System.out.println("  do suffix");
            }
            a = XOperations.concatenate(a, randomNoOp(a2));
            XBytesRefBuilder newTerm = new XBytesRefBuilder();
            for(BytesRef term : terms) {
              for(BytesRef suffix : addTerms) {
                newTerm.copyBytes(term);
                newTerm.append(suffix);
                newTerms.add(newTerm.toBytesRef());
              }
            }
          } else {
            // prefix
            if (VERBOSE) {
//              System.out.println("  do prefix");
            }
            a = XOperations.concatenate(randomNoOp(a2), a);
            XBytesRefBuilder newTerm = new XBytesRefBuilder();
            for(BytesRef term : terms) {
              for(BytesRef prefix : addTerms) {
                newTerm.copyBytes(prefix);
                newTerm.append(term);
                newTerms.add(newTerm.toBytesRef());
              }
            }
          }

          terms = newTerms;
        }
        break;
      default:
        throw new AssertionError();
      }

      assertSame(terms, a);
      assertEquals(AutomatonTestUtil.isDeterministicSlow(a), a.isDeterministic());
    }

    assertSame(terms, a);
  }

  private void assertSame(Collection<BytesRef> terms, XAutomaton a) {

    try {
      assertTrue(XOperations.isFinite(a));
      assertFalse(XOperations.isTotal(a));

      XAutomaton detA = XOperations.determinize(a, DEFAULT_MAX_DETERMINIZED_STATES);

      // Make sure all terms are accepted:
      XIntsRefBuilder scratch = new XIntsRefBuilder();
      for(BytesRef term : terms) {
        XUtil.toIntsRef(term, scratch);
        assertTrue("failed to accept term=" + term.utf8ToString(), XOperations.run(detA, term.utf8ToString()));
      }

      // Use getFiniteStrings:
      Set<IntsRef> expected = new HashSet<>();
      for(BytesRef term : terms) {
        XIntsRefBuilder intsRef = new XIntsRefBuilder();
        XUtil.toUTF32(term.utf8ToString(), intsRef);
        expected.add(intsRef.toIntsRef());
      }
      Set<IntsRef> actual = XOperations.getFiniteStrings(a, -1);

      if (expected.equals(actual) == false) {
//        System.out.println("FAILED:");
        for(IntsRef term : expected) {
          if (actual.contains(term) == false) {
//            System.out.println("  term=" + term + " should be accepted but isn't");
          }
        }
        for(IntsRef term : actual) {
          if (expected.contains(term) == false) {
//            System.out.println("  term=" + term + " is accepted but should not be");
          }
        }
        throw new AssertionError("mismatch");
      }

      // Use sameLanguage:
      XAutomaton a2 = XOperations.removeDeadStates(XOperations.determinize(unionTerms(terms),
        DEFAULT_MAX_DETERMINIZED_STATES));
      assertTrue(XOperations.sameLanguage(a2, XOperations.removeDeadStates(XOperations.determinize(a,
        DEFAULT_MAX_DETERMINIZED_STATES))));

      // Do same check, in UTF8 space
      XAutomaton utf8 = randomNoOp(new XUTF32ToUTF8().convert(a));
    
      Set<IntsRef> expected2 = new HashSet<>();
      for(BytesRef term : terms) {
        XIntsRefBuilder intsRef = new XIntsRefBuilder();
        XUtil.toIntsRef(term, intsRef);
        expected2.add(intsRef.toIntsRef());
      }
      assertEquals(expected2, XOperations.getFiniteStrings(utf8, -1));
    } catch (AssertionError ae) {
//      System.out.println("TEST: FAILED: not same");
//      System.out.println("  terms (count=" + terms.size() + "):");
      for(BytesRef term : terms) {
//        System.out.println("    " + term);
      }
//      System.out.println("  automaton:");
//      System.out.println(a.toDot());
      //a.writeDot("fail");
      throw ae;
    }
  }
}
