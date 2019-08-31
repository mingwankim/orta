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



import java.util.BitSet;
import java.util.Objects;

public class ImpactBitSet implements Comparable<ImpactBitSet> {

  private final BitSet dynBit;
  private final BitSet objBit;
  private final BitSet staticBit;
  private int cardinality = -1;

  public ImpactBitSet(BitSet dyn, BitSet stat, BitSet obj) {
    this.dynBit = dyn;
    this.staticBit = stat;
    this.objBit = obj;
  }

  public ImpactBitSet() {
    this.dynBit = new BitSet();
    this.staticBit = new BitSet();
    this.objBit = new BitSet();
  }

  private static BitSet and(BitSet a, BitSet b) {
    BitSet n = (BitSet) a.clone();
    n.and(b);
    return n;
  }

  @Override
  public int compareTo(ImpactBitSet o) {
    return o.getCardinality() - getCardinality();
  }

  public int getCardinality() {
    if (cardinality == -1) {
      cardinality = objBit.cardinality();
      if (cardinality != 0) {
        cardinality += dynBit.cardinality();
      }
      cardinality += staticBit.cardinality();
    }

    return cardinality;
  }

  @Override
  public int hashCode() {
    return Objects.hash(objBit, dynBit, staticBit);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj instanceof ImpactBitSet) {
      ImpactBitSet bit = (ImpactBitSet) obj;
      return bit.dynBit.equals(dynBit) && bit.staticBit.equals(staticBit) && bit.objBit
              .equals(objBit);
    }

    return false;
  }

  public ImpactBitSet and(
          ImpactBitSet o) {
    return new ImpactBitSet(and(o.dynBit, dynBit), and(o.staticBit, staticBit),
            and(o.objBit, objBit));
  }

  public void setObj(int bit) {
    objBit.set(bit);
  }

  public void setStatic(int bit) {
    staticBit.set(bit);
  }

  public void setDynamic(int bit) {
    dynBit.set(bit);
  }

  public BitSet obj() {
    return objBit;
  }

  public BitSet stat() {
    return staticBit;
  }

  public BitSet dyn() {
    return dynBit;
  }
}
