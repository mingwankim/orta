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



import com.google.common.collect.Iterators;
import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;
import com.google.common.graph.EndpointPair;
import com.google.common.graph.MutableGraph;
import org.orta.core.cg.OrderedCallGraph;
import org.orta.core.type.klass.KlassMethod;
import org.checkerframework.checker.nullness.qual.NonNull;

import java.util.Iterator;

class OrderedRTACallGraph extends RTACallGraph implements OrderedCallGraph {

  final MutableGraph<KlassMethod>[] parents;

  @SuppressWarnings({"initialization", "CopyConstructorMissesField"})
  OrderedRTACallGraph(
          OrderedRTACallGraph parent, KlassMethod fakeRoot) {
    super(new OrderedPointsToGraph((OrderedPointsToGraph) parent.getPAG()), fakeRoot);
    if (parent.isUpdated()) {
      int parentSize = parent.parents.length;
      parents = new MutableGraph[parentSize + 1];
      System.arraycopy(parent.parents, 0, parents, 0, parentSize);
      parents[parentSize] = parent.getGraph();
    } else {
      parents = parent.parents;
    }
  }

  @SuppressWarnings("initialization")
  OrderedRTACallGraph(KlassMethod fakeRoot) {
    super(new OrderedPointsToGraph(), fakeRoot);
    this.parents = new MutableGraph[0];
  }

  @Override
  public boolean markVisited(@NonNull KlassMethod method) {
    for (MutableGraph<KlassMethod> parent : parents) {
      if (parent.nodes().contains(method)) {
        return false;
      }
    }

    return super.markVisited(method);
  }

  @Override
  public void registerInvocation(@NonNull KlassMethod caller, @NonNull KlassMethod callee) {
    for (MutableGraph<KlassMethod> parent : parents) {
      if (parent.hasEdgeConnecting(caller, callee)) {
        return;
      }
    }

    super.registerInvocation(caller, callee);
  }

  @Override
  public SetView<KlassMethod> getCallersOf(KlassMethod callee) {
    SetView<KlassMethod> view = super.getCallersOf(callee);
    for (MutableGraph<KlassMethod> parent : parents) {
      view = Sets.union(view, parent.predecessors(callee));
    }

    return view;
  }

  @Override
  public Iterator<KlassMethod> nodes() {
    Iterator[] iters = new Iterator[parents.length + 1];
    int idx = -1;
    while (++idx < parents.length) {
      iters[idx] = parents[idx].nodes().iterator();
    }
    iters[idx] = super.nodes();

    return Iterators.concat(iters);
  }

  @Override
  public SetView<EndpointPair<KlassMethod>> edges() {
    SetView<EndpointPair<KlassMethod>> view = super.edges();
    for (MutableGraph<KlassMethod> parent : parents) {
      view = Sets.union(view, parent.edges());
    }

    return view;
  }
}
