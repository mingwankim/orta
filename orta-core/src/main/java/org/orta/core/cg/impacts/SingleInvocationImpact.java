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



import org.orta.core.type.klass.Klass;
import org.orta.core.type.klass.KlassMethod;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

public class SingleInvocationImpact implements ImpactUnit {

  private static Logger logger = LoggerFactory.getLogger(
          SingleInvocationImpact.class);
  @NonNull
  private final KlassMethod method;

  SingleInvocationImpact(@NonNull KlassMethod m) {
    this.method = m;
  }

  @Override
  public void apply(@NonNull ImpactVisitor ctx) {
    ctx.registerInvoked(this, method);
  }

  @Override
  public boolean isObjectCreated() {
    return false;
  }

  @Override
  public boolean isStaticInvoke() {
    return true;
  }

  @Override
  public Klass getType() {
    return method.getDeclaringClass();
  }

  @Override
  public boolean isDispatched() {
    return false;
  }

  @Override
  public int invokeCount() {
    return !method.getBody().isEmpty() ? 1 : 0;
  }

  @Override
  public boolean equals(Object o) {
    if (o == null) {
      return false;
    }
    if (o == this) {
      return true;
    }

    if (o instanceof SingleInvocationImpact) {
      SingleInvocationImpact x = (SingleInvocationImpact) o;
      return Objects.equals(x.method, method);
    }

    return false;
  }

  @Override
  public int hashCode() {
    return method.hashCode();
  }

  @Override
  public String toString() {
    return "StaticInvocation: " + method.getSignature();
  }
}
