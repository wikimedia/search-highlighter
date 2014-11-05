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
 * This test builds some randomish NFA/DFA and minimizes them.
 */
public class TestMinimize extends LuceneTestCase {
  /** the minimal and non-minimal are compared to ensure they are the same. */
  public void testBasic() {
    int num = atLeast(200);
    for (int i = 0; i < num; i++) {
      XAutomaton a = AutomatonTestUtil.randomAutomaton(random());
      XAutomaton la = XOperations.determinize(XOperations.removeDeadStates(a),
        DEFAULT_MAX_DETERMINIZED_STATES);
      XAutomaton lb = XMinimizationOperations.minimize(a,
        DEFAULT_MAX_DETERMINIZED_STATES);
      assertTrue(XOperations.sameLanguage(la, lb));
    }
  }
  
  /** compare minimized against minimized with a slower, simple impl.
   * we check not only that they are the same, but that #states/#transitions
   * are the same. */
  public void testAgainstBrzozowski() {
    int num = atLeast(200);
    for (int i = 0; i < num; i++) {
      XAutomaton a = AutomatonTestUtil.randomAutomaton(random());
      a = AutomatonTestUtil.minimizeSimple(a);
      XAutomaton b = XMinimizationOperations.minimize(a,
        DEFAULT_MAX_DETERMINIZED_STATES);
      assertTrue(XOperations.sameLanguage(a, b));
      assertEquals(a.getNumStates(), b.getNumStates());
      int numStates = a.getNumStates();

      int sum1 = 0;
      for(int s=0;s<numStates;s++) {
        sum1 += a.getNumTransitions(s);
      }
      int sum2 = 0;
      for(int s=0;s<numStates;s++) {
        sum2 += b.getNumTransitions(s);
      }

      assertEquals(sum1, sum2);
    }
  }
  
  /** n^2 space usage in Hopcroft minimization? */
  public void testMinimizeHuge() {
    new XRegExp("+-*(A|.....|BC)*]", XRegExp.NONE).toAutomaton(1000000);
  }
}
