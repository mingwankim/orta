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



import org.orta.core.type.klass.Klass;
import org.orta.core.type.klass.KlassMethod;
import org.orta.core.type.klass.MethodDescriptor;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;
import java.util.function.Function;

class MethodResolver {

  private final Klass owner;
  private final String name;
  private final MethodDescriptor desc;
  private final Set<Klass> interfaces = new HashSet<>();

  MethodResolver(Klass owner, String name, MethodDescriptor desc) {
    this.owner = owner;
    this.name = name;
    this.desc = desc;
  }

  @Nullable
  KlassMethod get() {
    KlassMethod resolved = resolveExactMethod((k) -> k.tryExactInvocation(name, desc));
    if (resolved != null) {
      return resolved;
    }
    resolved = resolveExactDefaultMethod();
    if (resolved != null) {
      return resolved;
    }

    resolved = resolveExactMethod(k -> k.tryClosestInvocation(name, desc));
    if (resolved != null) {
      return resolved;
    }

    return resolveClosestDefault();
  }

  @Nullable
  private KlassMethod resolveExactMethod(Function<Klass, KlassMethod> getter) {
    Klass ptr = owner;
    while (ptr != null) {
      KlassMethod method = getter.apply(ptr);
      if (method != null) {
        return method;
      } else {
        ptr = ptr.getSuperClass();
      }
    }

    return null;
  }

  @Nullable
  private KlassMethod resolveClosestDefault() {
    KlassMethod selected = null;
    for (Klass itf : interfaces) {
      KlassMethod resolved = itf.tryClosestInvocation(name, desc);
      if (resolved != null) {
        if (selected == null || resolved.isOverriding(selected)) {
          selected = resolved;
        }
      }
    }

    return selected;
  }

  @Nullable
  private KlassMethod resolveExactDefaultMethod() {
    Deque<Klass> queue = new LinkedList<>();
    KlassMethod selected = null;

    queue.add(owner);

    while (!queue.isEmpty()) {
      Klass kls = queue.remove();
      if (kls.isInterface()) {
        KlassMethod resolved = kls.tryExactInvocation(name, desc);
        if (resolved != null) {
          if (selected == null || selected.isOverriddenBy(resolved)) {
            selected = resolved;
          }
        }
      }

      Klass su = kls.getSuperClass();
      if (su != null) {
        queue.add(su);
      }
      kls.streamInterfaces().filter(interfaces::add).forEach(queue::add);
    }

    return selected == null || !selected.isImplementation() ? null : selected;
  }
}
