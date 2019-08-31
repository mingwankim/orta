package org.orta.core.cg.impacts;

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



import org.orta.core.cg.CallGraph;
import org.orta.core.type.TypeHelper;
import org.orta.core.type.klass.Klass;
import org.orta.core.type.klass.KlassMethod;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.Logger;

import java.util.Collection;
import java.util.LinkedList;
import java.util.Queue;

public abstract class DefaultImpactVisitor implements ImpactVisitor {

  @NonNull
  protected final CallGraph cg;
  @NonNull
  private final Queue<KlassMethod> methodWorklist = new LinkedList<>();
  private final Logger logger;
  @Nullable
  protected KlassMethod currentCaller;

  public DefaultImpactVisitor(CallGraph cg, Logger logger) {
    this.cg = cg;
    this.logger = logger;
  }

  protected abstract boolean isVisitedBefore(DynamicImpactResolver resolver, KlassMethod method);

  @Override
  @SuppressWarnings("nullness:methodref.return.invalid")
  // stream filter seems not analyzed correctly.
  public void addDynamicImpact(ImpactUnit u, @NonNull DynamicImpactResolver resolver) {
    // If the caller is defined
    // 1. the resolver should be not visited before with the caller.
    // 2. do not care whether the resolver is visited before
    // If the caller is not defined (maybe the placeholder or implicit invocation)
    // 1. the resolver should not be visited before.
    if (isVisitedBefore(resolver, currentCaller)) {
      return;
    }

    handleNewlyAddedResolver(resolver);
  }

  protected abstract void handleNewlyAddedResolver(DynamicImpactResolver resolver);

  @Override
  public void implicitInvoke(ImpactUnit unit, KlassMethod method) {
    addInvocation(null, method);
  }

  @Override
  public void acceptEntryMethod(KlassMethod m) {
    addWorkList(m);
    consumeWorkList();
  }

  @Override
  public void acceptImpactUnit(ImpactUnit impactUnit) {
    this.currentCaller = null;
    impactUnit.apply(this);
    consumeWorkList();
  }

  private void consumeWorkList() {
    while (!methodWorklist.isEmpty()) {
      this.currentCaller = methodWorklist.remove();
      currentCaller.getBody().forEach(u -> u.apply(this));
    }
  }

  private void addWorkList(@NonNull KlassMethod invoked) {
    if (cg.markVisited(invoked)) {
      methodWorklist.add(invoked);
    }
  }

  protected void addInvocation(@Nullable KlassMethod caller, @Nullable KlassMethod invoked) {
    if (invoked == null) {
      return;
    }

    addWorkList(invoked);
    if (caller == null) {
      cg.registerImplicitInvocation(invoked);
    } else {
      cg.registerInvocation(caller, invoked);
    }
  }

  protected abstract boolean isVisitedBefore(Klass type);

  @Override
  public void instantiateType(ImpactUnit unit, Klass type) {
    if (isVisitedBefore(type)) {
      return;
    }

    if (type.isConcrete()) {
      handleNewlyInstantiated(type);
    } else {
      TypeHelper.resolveInvocableMethods(type).forEach(method -> implicitInvoke(unit, method));
    }
  }

  protected void resolveInvocation(Klass type, DynamicImpactResolver resolver) {
    addInvocation(currentCaller, resolver.resolveCallee(type, currentCaller));
  }

  protected void resolveInvocation(Klass type, DynamicImpactResolver candidateResolver, Collection<KlassMethod> callers) {
    if (callers.isEmpty()) {
      addInvocation(null, candidateResolver.resolveCallee(type));
    } else {
      for (KlassMethod caller : callers) {
        addInvocation(caller, candidateResolver.resolveCallee(type, caller));
      }
    }
  }

  protected abstract void handleNewlyInstantiated(Klass type);

  @Override
  public void registerInvoked(ImpactUnit unit, @NonNull KlassMethod method) {
    addInvocation(this.currentCaller, method);
  }
}
