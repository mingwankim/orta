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
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.MultimapBuilder;
import org.orta.core.cg.impacts.DynamicImpactResolver;
import org.orta.core.cg.impacts.ImpactUnit;
import org.orta.core.cg.impacts.ImpactVisitor;
import org.orta.core.cg.rta.RTA;
import org.orta.core.type.AnalysisSession;
import org.orta.core.type.TypeHelper;
import org.orta.core.type.klass.Klass;
import org.orta.core.type.klass.KlassMethod;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

public class ORTACallGraphBuilder {

  private static final Logger logger = LoggerFactory.getLogger(ORTACallGraphBuilder.class);
  private final ListMultimap<OrderingKey, EdgeType> availableOrderings = MultimapBuilder.hashKeys().linkedListValues().build();
  private final Map<BitSet, OrderingKey> orderingKeys = new HashMap<>();
  private final PriorityQueue<OrderingKey> candidates = new PriorityQueue<>();
  private final ImpactMap impactMap = new ImpactMap();
  private final PriorityQueue<OrderingKey> smallMaximums = new PriorityQueue<>();
  private final Set<OrderingKey> smallMaximumsSet = new HashSet<>();
  private final Set<OrderingKey> maximums = new HashSet<>();

  private ORTACallGraphBuilder() {
  }

  private static BitSet mergeKlassBits(OrderingKey lhs, OrderingKey rhs) {
    BitSet newBits = (BitSet) lhs.getKlasses().clone();
    newBits.or(rhs.getKlasses());
    return newBits;
  }

  public static ORTACallGraphBuilder plan(Set<Klass> entryKlasses) {
    Preconditions.checkState(entryKlasses.size() > 2);
    long t = System.currentTimeMillis();
    ORTACallGraphBuilder builder = new ORTACallGraphBuilder();

    long k = System.currentTimeMillis();
    int count = 0;
    for (Klass klass : entryKlasses) {
      builder.add(count++, klass);
    }
    logger.info("add(): {}", (System.currentTimeMillis() - k) / 1000.0);

    builder.advanceMaximums();
    builder.findMaximals();
    logger.info("plan(): {}", (System.currentTimeMillis() - t) / 1000.0);
    return builder;
  }

  public static Map<String, CallGraph> build(AnalysisSession sess, Set<Klass> entryKlasses) {
    if (entryKlasses.size() <= 2) {
      Map<String, CallGraph> result = new HashMap<>();
      RTA rta = RTA.get();
      for (Klass k : entryKlasses) {
        result.put(k.toString(), rta.createCallGraph(sess, ImmutableSet.of(k), TypeHelper.resolveInvocableMethods(k)));
      }

      return result;
    }

    return plan(entryKlasses).constructGraphs(sess);
  }

  public static void validate(AnalysisSession sess, Set<Klass> klasses) {
    plan(klasses).validate(sess);
  }

  private void add(int id, Klass klass) {
    ImpactBitSet impactBitSet = new ImpactBitSet();
    Set<KlassMethod> methods = TypeHelper.resolveInvocableMethods(klass);
    impactMap.set(methods, impactBitSet);

    BitSet klasses = new BitSet();
    klasses.set(id);
    OrderingKey node = new OrderingKey(klasses, new Placeholder(impactBitSet, new InitialPlaceholder(klass, methods)));
    orderingKeys.put(klasses, node);
    addSmallMaximum(node);
  }

  private void addSmallMaximum(OrderingKey key) {
    smallMaximums.add(key);
    smallMaximumsSet.add(key);
  }

  private void updateCandidates(OrderingKey source) {
    if (!maximums.contains(source) && !smallMaximumsSet.contains(source)) {
      if (!candidates.isEmpty()) {
        int srcScore = source.getPlaceholder().getScore();
        int winnerScore = candidates.peek().getPlaceholder().getScore();
        if (srcScore >= winnerScore) {
          iterateMaximals(source);
        } else {
          addSmallMaximum(source);
        }
      } else {
        iterateMaximals(source);
      }
    }
  }

  private OrderingKey getNextSmall() {
    while (!smallMaximums.isEmpty()) {
      OrderingKey key = smallMaximums.poll();
      if (smallMaximumsSet.remove(key)) {
        return key;
      }
    }

    return null;
  }

  private OrderingKey peekNextSmall() {
    while (!smallMaximums.isEmpty()) {
      OrderingKey key = smallMaximums.peek();
      if (smallMaximumsSet.contains(key)) {
        return key;
      }
      smallMaximums.remove();
    }

    return null;
  }

  private void advanceMaximums() {
    while (candidates.isEmpty()) {
      OrderingKey key = getNextSmall();
      if (key == null) {
        return;
      }

      updateCandidates(key);
    }

    OrderingKey source = peekNextSmall();
    if (source == null) {
      return;
    }

    int winnerScore = candidates.peek().getPlaceholder().getScore();
    int threshold = source.getPlaceholder().getScore();
    while (winnerScore <= threshold) {
      source = getNextSmall();
      if (source == null) {
        return;
      }

      iterateMaximals(source);

      OrderingKey next = peekNextSmall();
      if (next == null) {
        break;
      }

      threshold = next.getPlaceholder().getScore();
      winnerScore = candidates.peek().getPlaceholder().getScore();
    }
  }

  private void iterateMaximals(OrderingKey source) {
    if (maximums.isEmpty()) {
      maximums.add(source);
      return;
    }

    int srcScore = source.getPlaceholder().getScore();
    Iterator<OrderingKey> iterator = maximums.iterator();
    OrderingKey target = iterator.next();
    while (true) {
      int targetScore = target.getPlaceholder().getScore();

      OrderingKey computed = compute(source, target);
      if (computed != null) {
        int score = computed.getPlaceholder().getScore();

        boolean isSourceDominant = srcScore == score;
        boolean isTargetDominant = targetScore == score;

        if (isTargetDominant && isSourceDominant) {
          iterator.remove();
//          Preconditions.checkState(!maximals.contains(computed) && !iteratedMaximals.contains(computed));
//          Preconditions.checkState(!maximals.contains(source) && !iteratedMaximals.contains(source));
//          Preconditions.checkState(!maximals.contains(target) && !iteratedMaximals.contains(target));
          computed.makeDelegator(source);
          computed.makeDelegator(target);
          source = computed;
          srcScore = score;
        } else {
          EdgeType edge;
          if (isSourceDominant) {
            edge = new SourceDominantType(source, target);
          } else if (isTargetDominant) {
            edge = new TargetDominantType(source, target);
          } else {
            edge = new SiblingsType(source, target);
          }

          List<EdgeType> edges = availableOrderings.get(computed);
          if (edges.isEmpty()) {
            candidates.add(computed);
          }
          edges.add(edge);
        }
      }

      if (iterator.hasNext()) {
        target = iterator.next();
      } else {
        break;
      }
    }

    maximums.add(source);
  }

  private OrderingKey merge(OrderingKey lhs, OrderingKey rhs) {
    lhs = lhs.self();
    rhs = rhs.self();
    if (lhs == rhs) {
      return lhs;
    }
    BitSet klasses = mergeKlassBits(lhs, rhs);
    OrderingKey key = orderingKeys.get(klasses);
    if (key == null) {
      key = new OrderingKey(klasses, lhs);
      orderingKeys.put(klasses, key);
    }

    if (key != lhs) {
      discardFromMaximal(lhs);
    }

    if (key != rhs) {
      discardFromMaximal(rhs);
    }

    key.makeDelegator(lhs);
    key.makeDelegator(rhs);

    return key;
  }

  private OrderingKey connectDominant(OrderingKey parent, OrderingKey dominant, OrderingKey child) {
    if (child.hasParent()) {
      return null;
    }

    OrderingKey computed = merge(parent, dominant);
    connect(computed, child);
    return computed;
  }

  private void discardFromMaximal(OrderingKey self) {
    if (!smallMaximumsSet.remove(self)) {
      maximums.remove(self);
    }
  }

  private OrderingKey compute(OrderingKey lhs, OrderingKey rhs) {
    BitSet newBits = mergeKlassBits(lhs, rhs);
    OrderingKey key = orderingKeys.get(newBits);
    if (key == null) {
      ImpactBitSet lhsph = lhs.getPlaceholder().getImpactBits();
      ImpactBitSet rhsph = rhs.getPlaceholder().getImpactBits();
      key = new OrderingKey(newBits, new Placeholder(lhsph.and(rhsph)));
      orderingKeys.put(newBits, key);
    }

    int score = key.getPlaceholder().getScore();
    if (score <= 0) {
      return null;
    }

    return key;
  }

  private void connect(OrderingKey parent, OrderingKey child) {
    child = child.self();
    discardFromMaximal(child);
    child.setParent(parent);
  }

  private void validate(AnalysisSession sess) {
    OrderedCallGraphAlgorithm algorithm = RTA.get();
    Map<String, CallGraph> result = new HashMap<>();
    Deque<OrderingKey> analysisQueue = new LinkedList<>();
    Map<BitSet, OrderedCallGraph> intermediateGraphs = new HashMap<>();
    for (OrderingKey key : smallMaximums) {
      analysisQueue.addLast(key);
    }

    for (OrderingKey key : maximums) {
      analysisQueue.addLast(key);
    }

    while (!analysisQueue.isEmpty()) {
      OrderingKey node = analysisQueue.removeFirst();
      OrderingKey parent = node.getParent();
      OrderedCallGraph accCG;
      Placeholder ph = node.getPlaceholder();
      Collection<InitialPlaceholder> initials = ph.getInitials();
      Collection<OrderingKey> children = node.getChildren();
      if (!children.isEmpty() || initials.size() > 1) {
        if (!children.isEmpty()) {
          if (parent == null) {
            accCG = algorithm.createOrderedCallGraph(sess);
          } else {
            OrderedCallGraph prev = intermediateGraphs.get(parent.getKlasses());
            accCG = algorithm.createOrderedCallGraph(prev, sess.getFakeCaller());
          }

          Preconditions.checkState(intermediateGraphs.put(node.getKlasses(), accCG) == null);
        } else if (parent == null) {
          accCG = algorithm.createOrderedCallGraph(sess);
        } else {
          OrderedCallGraph prev = intermediateGraphs.get(parent.getKlasses());
          accCG = algorithm.createOrderedCallGraph(prev, sess.getFakeCaller());
        }

        for (InitialPlaceholder iph : initials) {
          OrderedCallGraph finalCG = algorithm.createOrderedCallGraph(accCG, sess.getFakeCaller());
          Klass klass = iph.getKlass();
          result.put(klass.getTypeName(), finalCG);
        }
      } else {
        if (parent != null) {
          OrderedCallGraph prevCG = intermediateGraphs.get(parent.getKlasses());
          accCG = algorithm.createOrderedCallGraph(prevCG, sess.getFakeCaller());
        } else {
          accCG = algorithm.createOrderedCallGraph(sess);
        }

        for (InitialPlaceholder iph : initials) {
          Klass klass = iph.getKlass();
          result.put(klass.getTypeName(), accCG);
        }
      }

      for (OrderingKey child : node.getChildren()) {
        analysisQueue.addLast(child);
      }
    }
  }

  private Map<String, CallGraph> constructGraphs(AnalysisSession sess) {
    long t = System.currentTimeMillis();
    RTA algorithm = RTA.get();
    Map<String, CallGraph> result = new HashMap<>();
    Deque<OrderingKey> analysisQueue = new LinkedList<>();
    Map<BitSet, OrderedCallGraph> intermediateGraphs = new HashMap<>();
    for (OrderingKey key : smallMaximums) {
      analysisQueue.addLast(key);
    }

    for (OrderingKey key : maximums) {
      analysisQueue.addLast(key);
    }

    while (!analysisQueue.isEmpty()) {
      long y = System.currentTimeMillis();
      OrderingKey node = analysisQueue.removeFirst();
      OrderingKey parent = node.getParent();
      OrderedCallGraph accCG;
      Placeholder ph = node.getPlaceholder();
      Collection<InitialPlaceholder> initials = ph.getInitials();
      Collection<OrderingKey> children = node.getChildren();
      if (parent != null) {
        OrderedCallGraph prevCG = intermediateGraphs.get(parent.getKlasses());
        accCG = algorithm.createOrderedCallGraph(prevCG, sess.getFakeCaller());
      } else {
        accCG = algorithm.createOrderedCallGraph(sess);
      }

      if (!children.isEmpty() || initials.size() > 1) {
        if (parent == null) {
          ph.accept(accCG, impactMap);
        } else {
          ph.accept(accCG, parent.getPlaceholder(), impactMap);
        }

        intermediateGraphs.put(node.getKlasses(), accCG);

        for (InitialPlaceholder iph : initials) {
          OrderedCallGraph finalCG = algorithm.createOrderedCallGraph(accCG, sess.getFakeCaller());
          Klass klass = iph.accept(finalCG);
          Preconditions.checkState(result.put(klass.getTypeName(), finalCG) == null);
        }
      } else {
        for (InitialPlaceholder iph : initials) {
          Klass klass = iph.accept(accCG);
          Preconditions.checkState(result.put(klass.getTypeName(), accCG) == null);
        }
      }

      for (OrderingKey child : node.getChildren()) {
        analysisQueue.addLast(child);
      }
    }

    logger.info("constructGraph(): {}", (System.currentTimeMillis() - t) / 1000.0);

    return result;
  }

  private void findMaximals() {
    while (!candidates.isEmpty()) {
      OrderingKey node = candidates.remove();
      boolean created = false;
      List<EdgeType> edges = availableOrderings.removeAll(node);
      node = node.self();
      for (EdgeType edge : edges) {
        OrderingKey next = edge.tryConnect(node);
        if (next != null) {
          created = true;
          if (node != next) {
            node = merge(node, next).self();
          }
        }
      }

      if (created) {
        updateCandidates(node);
      }

      advanceMaximums();
    }
  }

  public static final class ImpactMap {

    private final Map<ImpactUnit, Integer> objDic = new HashMap<>();
    private final List<ImpactUnit> objMap = new ArrayList<>();
    private final Map<ImpactUnit, Integer> dynDic = new HashMap<>();
    private final List<ImpactUnit> dynMap = new ArrayList<>();
    private final Map<ImpactUnit, Integer> staticDic = new HashMap<>();
    private final List<ImpactUnit> staticMap = new ArrayList<>();
    private final Set<KlassMethod> visited = new HashSet<>();
    private ImpactBitSet impact;
    private final ImpactVisitor visitor = new ImpactVisitor() {
      @Override
      public void instantiateType(ImpactUnit unit, Klass type) {
        if (type.isConcrete()) {
          impact.setObj(addInt(unit, objDic, objMap));
        } else {
          for (KlassMethod m : TypeHelper.resolveInvocableMethods(type)) {
            if (visited.add(m)) {
              for (ImpactUnit u : m.getBody()) {
                u.apply(this);
              }
            }
          }
        }
      }

      @Override
      public void registerInvoked(ImpactUnit unit, KlassMethod callee) {
        if (visited.add(callee)) {
          impact.setStatic(addInt(unit, staticDic, staticMap));
          for (ImpactUnit u : callee.getBody()) {
            u.apply(this);
          }
        }
      }

      @Override
      public void addDynamicImpact(ImpactUnit u, DynamicImpactResolver dynamicInvocationImpact) {
        impact.setDynamic(addInt(u, dynDic, dynMap));
      }

      @Override
      public void implicitInvoke(ImpactUnit unit, KlassMethod method) {
        if (method == null || !visited.add(method)) {
          return;
        }

        impact.setStatic(addInt(unit, staticDic, staticMap));
        for (ImpactUnit u : method.getBody()) {
          u.apply(this);
        }
      }

      @Override
      public void acceptEntryMethod(KlassMethod m) {
      }

      @Override
      public void acceptImpactUnit(ImpactUnit impactUnit) {

      }
    };

    public void set(Set<KlassMethod> methods, ImpactBitSet bits) {
      impact = bits;
      visited.clear();
      for (KlassMethod m : methods) {
        if (visited.add(m)) {
          for (ImpactUnit u : m.getBody()) {
            u.apply(visitor);
          }
        }
      }
    }

    private int addInt(ImpactUnit u, Map<ImpactUnit, Integer> dic, List<ImpactUnit> map) {
      return dic.computeIfAbsent(u, (x) -> {
        int y = map.size();
        map.add(x);
        return y;
      });
    }

    List<ImpactUnit> objMap() {
      return objMap;
    }

    List<ImpactUnit> staticMap() {
      return staticMap;
    }

    List<ImpactUnit> dynMap() {
      return dynMap;
    }
  }

  private abstract static class EdgeType {
    protected final OrderingKey source;
    protected final OrderingKey target;

    EdgeType(@NonNull OrderingKey source, OrderingKey target) {
      this.source = source;
      this.target = target;
    }

    abstract OrderingKey tryConnect(OrderingKey result);
  }

  private final class SourceDominantType extends EdgeType {

    private SourceDominantType(OrderingKey source, OrderingKey target) {
      super(source, target);
    }

    @Override
    public OrderingKey tryConnect(OrderingKey result) {
      return connectDominant(result, source, target);
    }
  }

  private final class TargetDominantType extends EdgeType {

    private TargetDominantType(OrderingKey source, OrderingKey target) {
      super(source, target);
    }

    @Override
    public OrderingKey tryConnect(OrderingKey parent) {
      return connectDominant(parent, target, source);
    }
  }

  private final class SiblingsType extends EdgeType {

    private SiblingsType(OrderingKey source, OrderingKey target) {
      super(source, target);
    }

    @Override
    public OrderingKey tryConnect(OrderingKey parent) {
      OrderingKey source = this.source.self();
      OrderingKey target = this.target.self();
      parent = parent.self();
      if (source.hasParent() || target.hasParent()) {
        return null;
      }

      BitSet klasses = mergeKlassBits(target, source);
      OrderingKey key = orderingKeys.get(klasses);
      if (key != parent) {
        // klassbit is updated.
        if (key == null) {
          key = new OrderingKey(klasses, parent);
          orderingKeys.put(klasses, key);
        }
        discardFromMaximal(parent);
        key.makeDelegator(parent);
      }

      connect(key, source);
      connect(key, target);
      return key;
    }
  }

}
