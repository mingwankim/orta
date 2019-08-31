package org.orta.core.cg;

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



import org.orta.core.cg.impacts.ImpactUnit;
import org.orta.core.type.klass.KlassMethod;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Placeholder implements Comparable<Placeholder> {

  private final static Logger logger = LoggerFactory.getLogger(
          Placeholder.class);

  @NonNull
  private final ImpactBitSet pag;
  private final Set<InitialPlaceholder> iph;

  Placeholder(@NonNull ImpactBitSet pag, InitialPlaceholder iph) {
    this.pag = pag;
    if (iph != null) {
      this.iph = new HashSet<>();
      this.iph.add(iph);
    } else {
      this.iph = null;
    }
  }

  Placeholder(@NonNull ImpactBitSet pag) {
    this(pag, null);
  }

  private static boolean iterateBits(BitSet bit, List<ImpactUnit> u, OrderedCallGraph cg) {
    int idx = bit.nextSetBit(0);
    if (idx < 0) {
      return false;
    }

    do {
      cg.addImpactUnit(u.get(idx));
    } while ((idx = bit.nextSetBit(idx + 1)) >= 0);

    return true;
  }

  public int getScore() {
    return pag.getCardinality();
  }

  public ImpactBitSet getImpactBits() {
    return pag;
  }

  public Set<KlassMethod> getMethods() {
    return Collections.emptySet();
  }

  @Override
  public int compareTo(Placeholder o) {
    if (o.equals(this)) {
      return 0;
    }

    return o.getScore() - getScore();
  }

  void accept(OrderedCallGraph accCG, ORTACallGraphBuilder.ImpactMap impactMap) {
    if (iterateBits(pag.obj(), impactMap.objMap(), accCG)) {
      iterateBits(pag.dyn(), impactMap.dynMap(), accCG);
    }
    iterateBits(pag.stat(), impactMap.staticMap(), accCG);
  }

  private boolean iterateBits(BitSet target, BitSet previous, List<ImpactUnit> objMap,
                              OrderedCallGraph accCG) {
    BitSet clone = (BitSet) target.clone();
    clone.andNot(previous);
    return iterateBits(clone, objMap, accCG);
  }

  Collection<InitialPlaceholder> getInitials() {
    return iph == null ? Collections.emptyList() : iph;
  }

  public void accept(OrderedCallGraph accCG, Placeholder parent, ORTACallGraphBuilder.ImpactMap impactMap) {
    ImpactBitSet prevImpacts = parent.getImpactBits();
    if (iterateBits(pag.obj(), prevImpacts.obj(), impactMap.objMap(), accCG)) {
      iterateBits(pag.dyn(), prevImpacts.dyn(), impactMap.dynMap(), accCG);
    }
    iterateBits(pag.stat(), prevImpacts.stat(), impactMap.staticMap(), accCG);
  }

  Placeholder withoutInitial() {
    return iph == null ? this : new Placeholder(pag);
  }

  boolean hasInitial() {
    return iph != null;
  }

  void addInitial(Placeholder ph) {
    if (ph.iph != null) {
      this.iph.addAll(ph.iph);
    }
  }
}
