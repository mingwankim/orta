package org.orta.diff;

/*-
 * #%L
 * orta-diff
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



import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

final class ASMComparators {

  static final Comparator<MethodNode> methodComparator = (o1, o2) -> {
    if (o1 == o2) {
      return 0;
    }
    int diff = o1.name.compareTo(o2.name);
    if (diff != 0) {
      return diff;
    }

    diff = o1.desc.compareTo(o2.desc);
    assert diff != 0;
    return diff;
  };

  static final Comparator<AnnotationNode> annotationComparator = (a, b) -> {
    int diff = a.desc.compareTo(b.desc);
    if (diff != 0) {
      return diff;
    }

    if (a.values != null && b.values != null) {
      diff = a.values.size() - b.values.size();
      if (diff != 0) {
        return diff;
      }

      Queue<ValuePair> list = new LinkedList<>();
      list.add(new ValuePair(a.values, b.values));
      while (!list.isEmpty()) {
        @SuppressWarnings("nullness")
        ValuePair pair = list.poll();
        diff = pair.computeDiff(list);
        if (diff != 0) {
          return diff;
        }
      }
    } else if (a.values == null && b.values != null) {
      return -1;
    } else if (a.values != null && b.values == null) {
      return 1;
    }

    return 0;
  };

  private static class ValuePair {

    final List<Object> av;
    final List<Object> bv;
    final int size;

    ValuePair(List<Object> av, List<Object> bv) {
      this.av = av;
      this.bv = bv;
      this.size = av.size();
    }

    int computeDiff(Queue<ValuePair> suspender) {
      int diff;
      for (int i = 0; i < size; ++i) {
        Object ai = av.get(i);
        Object bi = bv.get(i);

        if (!ai.getClass().equals(bi.getClass())) {
          return ai.getClass().getName().compareTo(bi.getClass().getName());
        }

        if (ai instanceof String[]) {
          String[] av = (String[]) ai;
          String[] bv = (String[]) bi;
          diff = av[0].compareTo(bv[0]);
          if (diff != 0) {
            return diff;
          }
          diff = av[1].compareTo(bv[1]);
          if (diff != 0) {
            return diff;
          }
        } else if (ai instanceof AnnotationNode) {
          diff = annotationComparator.compare((AnnotationNode) ai, (AnnotationNode) bi);
          if (diff != 0) {
            return diff;
          }
        } else if (ai instanceof List) {
          suspender.add(new ValuePair((List) ai, (List) bi));
        } else {
          diff = ai.toString().compareTo(bi.toString());
          if (diff != 0) {
            return diff;
          }
        }
      }

      return 0;
    }
  }
}
