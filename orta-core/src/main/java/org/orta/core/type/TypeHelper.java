package org.orta.core.type;

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



import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableSet;
import com.google.common.graph.EndpointPair;
import org.orta.core.type.klass.Klass;
import org.orta.core.type.klass.KlassMethod;
import org.orta.core.type.klass.MethodDescriptor;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.objectweb.asm.Type;

import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Consumer;

public interface TypeHelper {

  static boolean isToolSpecificEdge(EndpointPair<KlassMethod> edge, AnalysisSession sess) {
    KlassMethod faker = sess.getFakeCaller();
    return edge.target().equals(faker) || edge.source().equals(faker);
  }

  @Nullable
  static Type getReferencedType(Type rawType) {
    switch (rawType.getSort()) {
      case Type.ARRAY:
        return getReferencedType(rawType.getElementType());
      case Type.OBJECT:
        return rawType;
      default:
        return null;
    }
  }

  @Nullable
  static KlassMethod resolveMethod(@NonNull Klass owner, @NonNull String name,
                                   @NonNull MethodDescriptor desc) {
    return new MethodResolver(owner, name, desc).get();
  }

  @NonNull
  static Set<KlassMethod> resolveInvocableMethods(Klass type) {
    HashBasedTable<MethodDescriptor, String, KlassMethod> methods = HashBasedTable.create();
    Consumer<KlassMethod> adder = x -> methods.put(x.getDescriptor(), x.getMethodName(), x);
    Set<Klass> interfaces = new HashSet<>(type.getInterfaces());

    type.streamDeclaringMethods()
            .filter(KlassMethod::isImplementation)
            .filter(x -> !x.isPrivate())
            .forEach(adder);

    Klass ptr = type.getSuperClass();
    while (ptr != null) {
      ptr.streamDeclaringMethods()
              .filter(KlassMethod::isImplementation)
              .filter(x -> !x.isPrivate())
              .filter(x -> !methods.contains(x.getDescriptor(), x.getMethodName()))
              .forEach(adder);

      interfaces.addAll(ptr.getInterfaces());
      ptr = ptr.getSuperClass();
    }

    BiFunction<KlassMethod, KlassMethod, KlassMethod> resolveDefaultMethods = (old, resolved) -> {
      if (old == null) {
        return resolved;
      }
      if (!old.getDeclaringClass().isInterface()) {
        return old;
      }

      if (old.isOverriddenBy(resolved)) {
        return resolved;
      }
      return old;
    };

    Deque<Klass> hierarchy = new LinkedList<>(interfaces);
    while (!hierarchy.isEmpty()) {
      Klass kls = hierarchy.removeFirst();
      kls.streamDeclaringMethods()
              .filter(KlassMethod::isImplementation)
              .forEach(x -> methods
                      .put(x.getDescriptor(), x.getMethodName(),
                              resolveDefaultMethods
                                      .apply(methods.get(x.getDescriptor(), x.getMethodName()), x)));

      kls.streamInterfaces().filter(interfaces::add).forEach(hierarchy::addFirst);
    }

    return methods.values().stream()
            .collect(ImmutableSet.toImmutableSet());
  }
}
