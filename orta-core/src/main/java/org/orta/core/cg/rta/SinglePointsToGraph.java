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



import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.SetMultimap;
import org.orta.core.cg.CallGraph;
import org.orta.core.cg.impacts.DefaultImpactVisitor;
import org.orta.core.cg.impacts.DynamicImpactResolver;
import org.orta.core.cg.impacts.ImpactVisitor;
import org.orta.core.type.klass.Klass;
import org.orta.core.type.klass.KlassMethod;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.Set;

public class SinglePointsToGraph implements PointsToGraph {
  private static final Logger logger = LoggerFactory.getLogger(
          SinglePointsToGraph.class);
  @NonNull
  private final SetMultimap<DynamicImpactResolver, KlassMethod> callers = MultimapBuilder.hashKeys()
          .hashSetValues().build();
  @NonNull
  private final Set<Klass> instantiatedTypes = new HashSet<>();

  @Override
  public ImpactVisitor createVisitor(CallGraph cg, KlassMethod fakeRoot) {
    ImpactVisitor visitor = new Visitor(cg);
    visitor.acceptEntryMethod(fakeRoot);
    return visitor;
  }

  private class Visitor extends DefaultImpactVisitor {
    public Visitor(CallGraph cg) {
      super(cg, logger);
    }

    @Override
    protected boolean isVisitedBefore(DynamicImpactResolver resolver, KlassMethod caller) {
      return !callers.put(resolver, caller);
    }

    @Override
    protected void handleNewlyAddedResolver(DynamicImpactResolver resolver) {
      Klass resolverType = resolver.getReceiverType();
      for (Klass type : instantiatedTypes) {
        if (type.inherits(resolverType)) {
          resolveInvocation(type, resolver);
        }
      }
    }

    @Override
    protected boolean isVisitedBefore(Klass type) {
      return !instantiatedTypes.add(type);
    }

    @Override
    protected void handleNewlyInstantiated(Klass type) {
      Set<Klass> rejected = new HashSet<>();
      Set<Klass> accepted = new HashSet<>();
      for (Entry<DynamicImpactResolver, Collection<KlassMethod>> entry : callers.asMap().entrySet()) {
        DynamicImpactResolver resolver = entry.getKey();
        Klass resolverType = resolver.getReceiverType();
        if (!accepted.contains(resolverType)) {
          if (rejected.contains(resolverType)) {
            continue;
          } else if (!type.inherits(resolverType)) {
            rejected.add(resolverType);
            continue;
          } else {
            accepted.add(resolverType);
          }
        }

        resolveInvocation(type, resolver, entry.getValue());
      }
    }
  }
}
