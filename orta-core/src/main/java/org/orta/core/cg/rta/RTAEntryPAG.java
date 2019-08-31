package org.orta.core.cg.rta;

/*-
 * #%L
 * orta-core
 * %%
 * Copyright (C) 2019 https://github.com/rts-orta
 * %%
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * 3. Neither the name of the Team ORTA nor the names of its contributors
 *    may be used to endorse or promote products derived from this software without
 *    specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */



import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import org.orta.core.cg.EntryPAG;
import org.orta.core.cg.impacts.ImpactFactory;
import org.orta.core.cg.impacts.ImpactUnit;
import org.orta.core.type.AnalysisSession;
import org.orta.core.type.klass.KlassMethod;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class RTAEntryPAG implements EntryPAG {

  private final static Logger logger = LoggerFactory.getLogger(
          RTAEntryPAG.class);
  @NonNull
  private final Collection<KlassMethod> initialReachables;
  private final Set<ImpactUnit> computedImpacts;
  private final int score;

  RTAEntryPAG(@NonNull AnalysisSession sess) {
    this.computedImpacts = fakeImpact(sess);
    this.initialReachables = Collections.emptyList();
    score = 0;
  }

  RTAEntryPAG(@NonNull AnalysisSession sess, @NonNull Collection<KlassMethod> methods,
              boolean needFlatten) {

    this.initialReachables = methods;
    Set<ImpactUnit> impacts;
    if (needFlatten) {
      impacts = new HashSet<>();
      int object = 0;
      int staticcall = 0;
      int dynamiccall = 0;
      for (KlassMethod m : methods) {
        for (ImpactUnit u : m.getBody()) {
          if (impacts.add(u) && u.getType().isConcrete()) {
            if (u.isObjectCreated()) {
              object += 1;
            } else if (u.isStaticInvoke()) {
              staticcall += 1;
            } else {
              dynamiccall += 1;
            }
          }
        }
      }

      score = object > 0 ? dynamiccall + staticcall + object : staticcall;
    } else {
      score = 0;
      impacts = fakeImpact(sess);
    }

    this.computedImpacts = impacts;
  }

  private RTAEntryPAG(@NonNull Set<ImpactUnit> impacts, int score) {
    this.initialReachables = ImmutableSet.of();
    this.computedImpacts = impacts;
    this.score = score;
  }

  private static Set<ImpactUnit> fakeImpact(@NonNull AnalysisSession sess) {
    ImpactFactory.ImpactBuilder builder = sess.createImpactBuilder();
    KlassMethod fakeRoot = sess.getFakeCaller();
    builder.invokeStatic(fakeRoot.getDeclaringClass(), fakeRoot.getMethodName(),
            fakeRoot.getDescriptor());
    return builder.build();
  }

  public static RTAEntryPAG ensureType(EntryPAG pag) {
    Preconditions.checkState(pag instanceof RTAEntryPAG);
    return (RTAEntryPAG) pag;
  }

  public Set<ImpactUnit> streamImpacts() {
    return computedImpacts;
  }

  public Collection<KlassMethod> streamMethods() {
    return initialReachables;
  }

  @Override
  public @NonNull RTAEntryPAG intersect(@NonNull EntryPAG rhs) {
    RTAEntryPAG r = ensureType(rhs);
    int staticcall = 0;
    int dynamiccall = 0;
    int object = 0;

    Set<ImpactUnit> impacts = new HashSet<>();
    for (ImpactUnit u : computedImpacts) {
      if (r.computedImpacts.contains(u) && impacts.add(u)) {
        if (u.isObjectCreated()) {
          object += 1;
        } else if (u.isStaticInvoke()) {
          staticcall += 1;
        } else {
          dynamiccall += 1;
        }
      }
    }

    int score = object > 0 ? dynamiccall + staticcall + object : staticcall;
    return new RTAEntryPAG(impacts, score);
  }

  @Override
  public @NonNull RTAEntryPAG difference(@NonNull EntryPAG rhs) {
    RTAEntryPAG r = ensureType(rhs);
    int staticcall = 0;
    int dynamiccall = 0;
    int object = 0;

    Set<ImpactUnit> impacts = new HashSet<>();
    for (ImpactUnit u : computedImpacts) {
      if (!r.computedImpacts.contains(u) && impacts.add(u)) {
        if (u.isObjectCreated()) {
          object += 1;
        } else if (u.isStaticInvoke()) {
          staticcall += 1;
        } else {
          dynamiccall += 1;
        }
      }
    }

    int score = object > 0 ? dynamiccall + staticcall + object : staticcall;
    return new RTAEntryPAG(impacts, score);
  }

  @Override
  public Set<ImpactUnit> getImpacts() {
    return computedImpacts;
  }

  @Override
  public Collection<KlassMethod> getReachables() {
    return initialReachables;
  }

  @Override
  public int getScore() {
    return computedImpacts.size();
  }

  @Override
  public RTAEntryPAG union(EntryPAG pag) {
//    RTAEntryPAG r = ensureType(pag);
//    int staticcall = 0;
//    int dynamiccall = 0;
//    int object = 0;
//
//    Set<ImpactUnit> impacts = new HashSet<>();
//    for (ImpactUnit u : computedImpacts) {
//      if (u.isObjectCreated()) {
//        object += 1;
//      } else {
//        callsite += 1;
//      }
//      impacts.add(u);
//    }
//
//    for (ImpactUnit u : r.computedImpacts) {
//      if (impacts.add(u)) {
//        if (u.isObjectCreated()) {
//          object += 1;
//        } else {
//          callsite += 1;
//        }
//      }
//    }
//
//
//    int score = object > 0 ? dynamiccall + staticcall + object : staticcall;
//    return new RTAEntryPAG(impacts, score);
    return null;
  }

  @Override
  public boolean isEmpty() {
    return getScore() == 0;
  }
}
