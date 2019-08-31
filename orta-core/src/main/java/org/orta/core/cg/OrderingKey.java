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


import com.google.common.base.Preconditions;

import java.util.BitSet;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

class OrderingKey implements Comparable<OrderingKey> {
  private final int klassesSize;  // this field is used for compareTo()
  private final BitSet klasses;
  private OrderingKey parent;
  private Placeholder ph;
  private Set<OrderingKey> children;
  private OrderingKey delegatorOf;

  OrderingKey(BitSet klasses, Placeholder ph) {
    this.klasses = klasses;
    klassesSize = this.klasses.cardinality();
    this.ph = ph;
    this.children = new HashSet<>();
  }

  OrderingKey(BitSet klasses, OrderingKey same) {
    this.klasses = klasses;
    klassesSize = this.klasses.cardinality();
    this.ph = same.ph.withoutInitial();
    this.children = new HashSet<>();
  }

  void makeDelegator(OrderingKey src) {
    if (delegatorOf != null) {
      self().makeDelegator(src);
    } else {
      src = src.self();
      if (src == this) {
        return;
      }

      Preconditions.checkState(src.parent == null && parent == null);
      src.delegatorOf = this;

      for (OrderingKey child : src.children) {
        child = child.self();
        if (children.add(child)) {
          child.parent = this;
        }
      }

      src.parent = null;
      src.children = children;
      if (ph.hasInitial()) {
        ph.addInitial(src.ph);
        src.ph = ph;
      } else {
        ph = src.ph;
      }
    }
  }

  public OrderingKey getParent() {
    return parent;
  }

  void setParent(OrderingKey parent) {
    if (delegatorOf != null) {
      delegatorOf.setParent(parent);
    } else {
      parent = parent.self();
      Preconditions.checkState(this.parent == null);
      this.parent = parent;
      parent.children.add(this);
    }
  }

  @Override
  public String toString() {
    return isDelegated() ? "delegatorOf: " + delegatorOf.toString() : klasses.toString();
  }

  public Placeholder getPlaceholder() {
    return ph;
  }

  Collection<OrderingKey> getChildren() {
    return children;
  }

  @Override
  public int compareTo(OrderingKey o) {
    if (o == null) {
      return 1;
    } else if (o == this) {
      return 0;
    }

    int diff = o.ph.getScore() - ph.getScore();
    if (diff == 0) {
      diff = klassesSize - o.klassesSize;
      if (diff == 0) {
        int idx = -1;
        do {
          int self = klasses.nextSetBit(idx + 1);
          int other = o.klasses.nextSetBit(idx + 1);
          diff = self - other;
          if (diff != 0) {
            return diff;
          }

          idx = self;
        } while (idx >= 0);

        throw new RuntimeException();
      }
    }

    return diff;
  }

  public boolean hasParent() {
    return self().parent != null;
  }

  OrderingKey self() {
    OrderingKey key = this;
    while (key.delegatorOf != null) {
      key = key.delegatorOf;
    }

    return key;
  }

  BitSet getKlasses() {
    return self().klasses;
  }

  public boolean isDelegated() {
    return delegatorOf != null;
  }
}
