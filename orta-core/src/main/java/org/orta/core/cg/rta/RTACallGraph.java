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



import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;
import com.google.common.graph.EndpointPair;
import com.google.common.graph.GraphBuilder;
import com.google.common.graph.MutableGraph;
import com.google.common.graph.Traverser;
import org.orta.core.cg.CallGraph;
import org.orta.core.cg.impacts.ImpactUnit;
import org.orta.core.cg.impacts.ImpactVisitor;
import org.orta.core.type.klass.KlassMethod;
import org.checkerframework.checker.nullness.qual.NonNull;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

class RTACallGraph implements CallGraph {

  final MutableGraph<KlassMethod> cg = GraphBuilder.directed().allowsSelfLoops(true)
          .build();
  private final PointsToGraph pag;
  private final KlassMethod fakeRoot;
  private ImpactVisitor visitor;

  RTACallGraph(PointsToGraph pag, KlassMethod fakeRoot) {
    this.pag = pag;
    this.fakeRoot = fakeRoot;
  }

  RTACallGraph(KlassMethod fakeRoot) {
    this(new SinglePointsToGraph(), fakeRoot);
  }

  public boolean isUpdated() {
    return !cg.nodes().isEmpty();
  }

  private ImpactVisitor getVisitor() {
    if (visitor == null) {
      visitor = pag.createVisitor(this, fakeRoot);
    }

    return visitor;
  }

  MutableGraph<KlassMethod> getGraph() {
    return cg;
  }

  PointsToGraph getPAG() {
    return pag;
  }

//  public Sets.SetView<EndpointPair<IKlassMethod>> getEdges() {
//    Sets.SetView<EndpointPair<IKlassMethod>> ident = Sets.union(cg.edges(), ImmutableSet.of());
//    return Streams.stream(iterateParent())
//        .reduce(ident, (s, cg) -> Sets.union(s, cg.cg.edges()), Sets::union);
//  }
//
//  public Sets.SetView<IKlassMethod> getMethods() {
//    Sets.SetView<IKlassMethod> ident = Sets.union(cg.methods(), ImmutableSet.of());
//    return Streams.stream(iterateParent())
//        .reduce(ident, (s, cg) -> Sets.union(s, cg.cg.methods()), Sets::union);
//  }
//
//  @Override
//  protected boolean isKnownInvocation(IKlassMethod caller, IKlassMethod callee) {
//    return cg.hasEdgeConnecting(caller, callee);
//  }

  @SuppressWarnings("BooleanMethodIsAlwaysInverted")
  public boolean markVisited(@NonNull KlassMethod method) {
    return cg.addNode(method);
  }

  public void registerInvocation(@NonNull KlassMethod caller, @NonNull KlassMethod callee) {
    cg.putEdge(caller, callee);
  }

  @Override
  public void addImpactUnit(ImpactUnit u) {
    getVisitor().acceptImpactUnit(u);
  }

  @Override
  public void registerImplicitInvocation(KlassMethod invoked) {
    registerInvocation(fakeRoot, invoked);
  }

  @Override
  public Iterable<KlassMethod> getReachables(Set<KlassMethod> klassMethods) {
    Set<KlassMethod> entries = new HashSet<>(klassMethods);
    entries.add(fakeRoot);
    return Traverser.forGraph(cg).breadthFirst(entries);
  }

  @Override
  public void addEntry(KlassMethod m) {
    getVisitor().acceptEntryMethod(m);
  }

  @Override
  public SetView<KlassMethod> getCallersOf(KlassMethod callee) {
    return Sets.union(cg.predecessors(callee), ImmutableSet.of());
  }

  @Override
  public Iterator<KlassMethod> nodes() {
    return cg.nodes().iterator();
  }

  @Override
  public SetView<EndpointPair<KlassMethod>> edges() {
    return Sets.union(cg.edges(), ImmutableSet.of());
  }

  @Override
  public Iterable<KlassMethod> getReachables(KlassMethod node) {
    return Traverser.forGraph(cg).breadthFirst(Arrays.asList(node, fakeRoot));
  }

  @Override
  public Set<KlassMethod> successors(KlassMethod currentNode) {
    return cg.successors(currentNode);
  }
}
