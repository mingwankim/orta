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



import com.google.common.collect.ImmutableSet;
import org.orta.core.type.AnalysisSession;
import org.orta.core.type.TypeHelper;
import org.orta.core.type.klass.Klass;
import org.orta.core.type.klass.KlassMethod;
import org.orta.core.type.klass.MethodDescriptor;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public class ImpactFactory {

  private static final Logger logger = LoggerFactory.getLogger(
          ImpactFactory.class);
  private final Map<KlassMethod, ImpactUnit> singleInvocations = new HashMap<>();
  private final Map<Klass, ImpactUnit> objectCreations = new HashMap<>();
  private final Map<Klass, ImpactUnit> objectRefs = new HashMap<>();
  private final Map<ImpactUnit, ImpactUnit> dynamicInvocations = new HashMap<>();

  private void clinit(@NonNull Klass kls, @NonNull Set<ImpactUnit> units) {
    Deque<Klass> klasses = new LinkedList<>();
    klasses.add(kls);
    while (!klasses.isEmpty()) {
      Klass ptr = klasses.pop();
      units.add(objectRefs.computeIfAbsent(ptr, ClassInitializerImpact::new));
      klasses.addAll(ptr.getInterfaces());
      Klass parent = ptr.getSuperClass();
      if (parent != null) {
        klasses.add(parent);
      }
    }
  }

  private void singleInvoke(@Nullable KlassMethod method, @NonNull Set<ImpactUnit> units) {
    if (method != null) {
      if (method.getMethodName().equals("<init>")) {
        // In case of <init> invocation using super.
        clinit(method.getDeclaringClass(), units);
      }

      units.add(singleInvocations.computeIfAbsent(method, SingleInvocationImpact::new));
    }
  }

  private void object(@NonNull Klass type, @NonNull Set<ImpactUnit> units) {
    clinit(type, units);
    units.add(objectCreations.computeIfAbsent(type, ObjectCreationImpact::new));
  }

  private void dynamicInvoke(@NonNull Klass owner, @NonNull String name,
                             @NonNull MethodDescriptor desc,
                             @NonNull Set<ImpactUnit> units) {
    DynamicInvocationImpact impact = new DynamicInvocationImpact(owner, name, desc);
    units.add(dynamicInvocations.computeIfAbsent(impact, Function.identity()));
  }

  public ImpactBuilder builder() {
    return new ImpactBuilder();
  }

  private void singleInvoke(Klass kls, String name, MethodDescriptor desc, Set<ImpactUnit> units) {
    KlassMethod method = TypeHelper.resolveMethod(kls, name, desc);
    singleInvoke(method, units);
  }

  @SuppressWarnings("UnusedReturnValue")
  public class ImpactBuilder {

    private final Set<ImpactUnit> units = new HashSet<>();

    public ImpactBuilder invokeDefaultConstructor(Klass kls) {
//      if (kls.isConcrete()) {
      object(kls, units);
      AnalysisSession session = kls.getSession();
      singleInvoke(kls, "<init>", session.getOrCreateMethodDescriptor("()V"), units);
//      } else {
//        implicit(kls);
//      }
      return this;
    }

    public ImmutableSet<ImpactUnit> build() {
      return ImmutableSet.copyOf(units);
    }

    public ImpactBuilder invokeInterface(Klass ownerType, String name, MethodDescriptor desc) {
//      if (ownerType.isConcrete()) {
      dynamicInvoke(ownerType, name, desc, units);
//      }
      return this;
    }

    public ImpactBuilder invokeSpecial(Klass ownerType, String name, MethodDescriptor descType) {
//      if (ownerType.isConcrete()) {
      singleInvoke(ownerType, name, descType, units);
//      } else {
//        implicit(ownerType);
//      }
      return this;
    }

    public ImpactBuilder invokeStatic(Klass kls, String name, MethodDescriptor desc) {
//      if (kls.isConcrete()) {
      ImpactFactory.this.clinit(kls, units);
      singleInvoke(kls, name, desc, units);
//      } else {
//        implicit(kls);
//      }
      return this;
    }

    public ImpactBuilder invokeVirtual(Klass ownerType, String name, MethodDescriptor descType) {
//      if (ownerType.isConcrete()) {
      dynamicInvoke(ownerType, name, descType, units);
//      }
      return this;
    }

    public ImpactBuilder createObject(Klass kls) {
//      if (kls.isReachable()) {
      object(kls, units);
//      } else {
//        implicit(kls);
//      }
      return this;
    }

    public ImpactBuilder referenceType(Klass kls) {
//      if (kls.isConcrete()) {
      ImpactFactory.this.clinit(kls, units);
//      } else {
//        implicit(kls);
//      }
      return this;
    }

    public ImpactBuilder invokeSpecial(KlassMethod m) {
      invokeSpecial(m.getDeclaringClass(), m.getMethodName(), m.getDescriptor());
      return this;
    }

    public ImpactBuilder clinit(Klass type) {
      ImpactFactory.this.clinit(type, units);
      return this;
    }
  }
}
