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

import org.apache.lucene.util.LuceneTestCase;

import static org.apache.lucene.util.automaton.XOperations.DEFAULT_MAX_DETERMINIZED_STATES;

/**
 * Not completely thorough, but tries to test determinism correctness
 * somewhat randomly.
 */
public class TestDeterminism extends LuceneTestCase {
  
  /** test a bunch of random regular expressions */
  public void testRegexps() throws Exception {
      int num = atLeast(500);
      for (int i = 0; i < num; i++) {
        assertAutomaton(new XRegExp(AutomatonTestUtil.randomRegexp(random()), XRegExp.NONE).toAutomaton());
      }
  }
  
  /** test against a simple, unoptimized det */
  public void testAgainstSimple() throws Exception {
    int num = atLeast(200);
    for (int i = 0; i < num; i++) {
      XAutomaton a = AutomatonTestUtil.randomAutomaton(random());
      a = AutomatonTestUtil.determinizeSimple(a);
      XAutomaton b = XOperations.determinize(a, DEFAULT_MAX_DETERMINIZED_STATES);
      // TODO: more verifications possible?
      assertTrue(XOperations.sameLanguage(a, b));
    }
  }
  
  private static void assertAutomaton(XAutomaton a) {
    a = XOperations.determinize(XOperations.removeDeadStates(a), DEFAULT_MAX_DETERMINIZED_STATES);

    // complement(complement(a)) = a
    XAutomaton equivalent = XOperations.complement(XOperations.complement(a,
      DEFAULT_MAX_DETERMINIZED_STATES), DEFAULT_MAX_DETERMINIZED_STATES);
    assertTrue(XOperations.sameLanguage(a, equivalent));
    
    // a union a = a
    equivalent = XOperations.determinize(XOperations.removeDeadStates(XOperations.union(a, a)),
      DEFAULT_MAX_DETERMINIZED_STATES);
    assertTrue(XOperations.sameLanguage(a, equivalent));
    
    // a intersect a = a
    equivalent = XOperations.determinize(XOperations.removeDeadStates(XOperations.intersection(a, a)),
      DEFAULT_MAX_DETERMINIZED_STATES);
    assertTrue(XOperations.sameLanguage(a, equivalent));
    
    // a minus a = empty
    XAutomaton empty = XOperations.minus(a, a, DEFAULT_MAX_DETERMINIZED_STATES);
    assertTrue(XOperations.isEmpty(empty));
    
    // as long as don't accept the empty string
    // then optional(a) - empty = a
    if (!XOperations.run(a, "")) {
      //System.out.println("test " + a);
      XAutomaton optional = XOperations.optional(a);
      //System.out.println("optional " + optional);
      equivalent = XOperations.minus(optional, XAutomata.makeEmptyString(),
        DEFAULT_MAX_DETERMINIZED_STATES);
      //System.out.println("equiv " + equivalent);
      assertTrue(XOperations.sameLanguage(a, equivalent));
    }
  } 
}
