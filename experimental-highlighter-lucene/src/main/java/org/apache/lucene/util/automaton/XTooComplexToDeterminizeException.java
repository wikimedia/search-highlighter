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

/**
 * This exception is thrown when determinizing an automaton would result in one
 * has too many states.
 */
public class XTooComplexToDeterminizeException extends RuntimeException {
  private final XAutomaton automaton;
  private final XRegExp regExp;
  private final int maxDeterminizedStates;

  public XTooComplexToDeterminizeException(XRegExp regExp, XTooComplexToDeterminizeException cause) {
    super("Determinizing " + regExp.getOriginalString() + " would result in more than " +
      cause.maxDeterminizedStates + " states.", cause);
    this.regExp = regExp;
    this.automaton = cause.automaton;
    this.maxDeterminizedStates = cause.maxDeterminizedStates;
  }

  public XTooComplexToDeterminizeException(XAutomaton automaton, int maxDeterminizedStates) {
    super("Determinizing automaton would result in more than " + maxDeterminizedStates + " states.");
    this.automaton = automaton;
    this.regExp = null;
    this.maxDeterminizedStates = maxDeterminizedStates;
  }

  public XAutomaton getAutomaton() {
    return automaton;
  }

  /**
   * Return the RegExp that caused this exception if any.
   */
  public XRegExp getRegExp() {
    return regExp;
  }

  public int getMaxDeterminizedStates() {
    return maxDeterminizedStates;
  }
}
