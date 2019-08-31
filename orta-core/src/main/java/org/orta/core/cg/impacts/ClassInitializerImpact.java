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

public class ClassInitializerImpact implements ImpactUnit {

  private final Klass klass;

  public ClassInitializerImpact(Klass klass) {
    this.klass = klass;
  }

  @Override
  public void apply(@NonNull ImpactVisitor visitor) {
    visitor.implicitInvoke(this, klass.getClassInitializer());
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
    return klass;
  }

  @Override
  public boolean isDispatched() {
    return false;
  }

  @Override
  public int invokeCount() {
    KlassMethod m = klass.getClassInitializer();
    return m != null && !m.getBody().isEmpty() ? 1 : 0;
  }

  @Override
  public int hashCode() {
    return klass.hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == null) {
      return false;
    }
    if (obj == this) {
      return true;
    }
    if (obj instanceof ClassInitializerImpact) {
      return klass.equals(((ClassInitializerImpact) obj).klass);
    }
    return false;
  }

  @Override
  public String toString() {
    return "<clinit>: " + klass.getTypeName();
  }
}
