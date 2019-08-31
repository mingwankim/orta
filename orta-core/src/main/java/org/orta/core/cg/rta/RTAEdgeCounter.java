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



import com.google.common.collect.HashMultiset;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multiset;
import com.google.common.graph.EndpointPair;
import com.google.common.graph.Graph;
import com.google.common.graph.MutableGraph;
import org.orta.core.cg.CallGraph;
import org.orta.core.cg.OrderedCallGraph;
import org.orta.core.type.klass.KlassMethod;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class RTAEdgeCounter {
  private static void sum(KlassMethod faker, Set<EndpointPair<KlassMethod>> edges, Multiset<EndpointPair<KlassMethod>> total) {
    for (EndpointPair<KlassMethod> edge : edges) {
      if (edge.target().equals(faker) || edge.source().equals(faker)) {
        continue;
      }

      total.add(edge);
    }
  }

  public static Iterable<Graph<KlassMethod>> iterate(Collection<CallGraph> graphs) {
    Set<Graph<KlassMethod>> visited = new HashSet<>();
    for (CallGraph cg : graphs) {
      if (cg instanceof OrderedCallGraph) {
        OrderedRTACallGraph ocg = (OrderedRTACallGraph) cg;
        for (MutableGraph<KlassMethod> mg : ocg.parents) {
          visited.add(mg);
        }

        visited.add(ocg.cg);
      } else {
        visited.add(((RTACallGraph) cg).cg);
      }
    }

    return Iterables.concat(visited);
  }

  public static Multiset<EndpointPair<KlassMethod>> count(KlassMethod faker, Collection<CallGraph> graphs) {
    Multiset<EndpointPair<KlassMethod>> counter = HashMultiset.create();
    Set<Graph<KlassMethod>> visited = new HashSet<>();
    for (CallGraph cg : graphs) {
      if (cg instanceof OrderedCallGraph) {
        OrderedRTACallGraph ocg = (OrderedRTACallGraph) cg;
        for (MutableGraph<KlassMethod> mg : ocg.parents) {
          if (visited.add(mg)) {
            sum(faker, mg.edges(), counter);
          }
        }

        if (visited.add(ocg.cg)) {
          sum(faker, ocg.cg.edges(), counter);
        }
      } else {
        sum(faker, cg.edges(), counter);
      }
    }

    return counter;
  }
}
