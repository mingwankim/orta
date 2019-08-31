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



import org.orta.core.type.TypeHelper;
import org.orta.core.type.klass.Klass;
import org.orta.core.type.klass.KlassMethod;
import org.orta.core.type.klass.MethodDescriptor;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

public class DynamicInvocationImpact implements DynamicImpactResolver, ImpactUnit {

  private static final Logger logger = LoggerFactory.getLogger(
          DynamicInvocationImpact.class);

  @NonNull
  private final Klass klass;

  @NonNull
  private final String name;

  @NonNull
  private final MethodDescriptor desc;

  DynamicInvocationImpact(@NonNull Klass klass, @NonNull String name,
                          @NonNull MethodDescriptor desc) {
    this.klass = klass;
    this.name = name;
    this.desc = desc;
  }

  @Override
  public void apply(@NonNull ImpactVisitor visitor) {
    visitor.addDynamicImpact(this, this);
  }

  @Override
  public boolean isObjectCreated() {
    return false;
  }

  @Override
  public boolean isStaticInvoke() {
    return false;
  }

  @Override
  public Klass getType() {
    return klass;
  }

  @Override
  public boolean isDispatched() {
    return true;
  }

  @Override
  public int invokeCount() {
    return Integer.MAX_VALUE;
  }

  @NonNull
  @Override
  public Klass getReceiverType() {
    return klass;
  }

  @Override
  public @Nullable KlassMethod resolveCallee(Klass instantiatedKlass) {
    KlassMethod method = resolveCandidate(instantiatedKlass);
    if (method != null && method.isPublic()) {
      return method;
    }

    return null;
  }

  private KlassMethod resolveCandidate(Klass instantiatedKlass) {
    KlassMethod method;
    if (instantiatedKlass.isConcrete()) {
      method = TypeHelper.resolveMethod(instantiatedKlass, name, desc);
      if (method == null) {
        logger.warn("No method is found with the method signature: {} from {}", this,
                instantiatedKlass);
      } else if (!(method.isImplementation())) {
        method = null;
      }
    } else {
      method = null;
    }

    return method;
  }

  @Override
  @Nullable
  @SuppressWarnings("nullness:argument.type.incompatible")  // null values is used in hashmap
  public KlassMethod resolveCallee(@NonNull Klass instantiatedKlass, KlassMethod caller) {
    if (caller == null) {
      return resolveCallee(instantiatedKlass);
    }

    KlassMethod method = resolveCandidate(instantiatedKlass);
    if (method != null && method.isAccessableTo(caller)) {
      return method;
    }

    return null;
  }

  @Override
  public int hashCode() {
    return Objects.hash(klass, name, desc);
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == null) return false;
    if (obj == this) return true;
    if (obj instanceof DynamicInvocationImpact) {
      DynamicInvocationImpact o = (DynamicInvocationImpact) obj;
      return o.klass.equals(klass) && o.name.equals(name) && o.desc.equals(desc);
    }

    return false;
  }

  @Override
  public String toString() {
    return "DynamicCallSite: " + klass.getInternalName() + "." + name + desc;
  }
}
