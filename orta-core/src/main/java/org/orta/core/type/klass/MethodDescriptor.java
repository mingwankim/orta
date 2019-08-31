package org.orta.core.type.klass;

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



import org.orta.core.type.AnalysisSession;
import org.orta.core.type.TypeLike;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.objectweb.asm.Type;

import java.util.Arrays;
import java.util.Objects;

public class MethodDescriptor {

  private final TypeLike[] parameters;
  private final TypeLike returnType;

  public MethodDescriptor(@NonNull TypeLike returnType, @NonNull TypeLike... parameters) {
    this.returnType = returnType;
    this.parameters = parameters;
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    MethodDescriptor o = (MethodDescriptor) obj;

    return Arrays.deepEquals(parameters, o.parameters)
            && Objects.equals(returnType, o.returnType);
  }

  public TypeLike[] getParameters() {
    return parameters;
  }

  public TypeLike getReturnType() {
    return returnType;
  }

  @Override
  public int hashCode() {
    return Objects.hash(Arrays.hashCode(parameters), returnType);
  }

  public boolean isAssignableTo(MethodDescriptor desc) {
    return this.equals(desc);
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder("(");
    for (TypeLike t : parameters) {
      builder.append(t.getTypeDescriptor());
    }
    return builder.append(")").append(returnType.getTypeDescriptor()).toString();
  }

  public boolean isCovariantOf(@NonNull MethodDescriptor givenDesc) {
    return hasCovariantReturnTypeOf(givenDesc) && hasCovariantParametersOf(givenDesc);
  }

  private boolean hasCovariantParametersOf(@NonNull MethodDescriptor givenDesc) {
    if (this == givenDesc) {
      return true;
    }
    TypeLike[] target = givenDesc.parameters;
    if (parameters.length != target.length) {
      return false;
    }

    for (int i = 0; i < parameters.length; ++i) {
      if (!parameters[i].isCovariantTypeOf(target[i])) {
        return false;
      }
    }

    return true;
  }

  public boolean hasSameParameter(@NonNull MethodDescriptor givenDesc) {
    if (this == givenDesc) {
      return true;
    }
    TypeLike[] target = givenDesc.parameters;
    if (parameters.length != target.length) {
      return false;
    }

    for (int i = 0; i < parameters.length; ++i) {
      if (!parameters[i].equals(target[i])) {
        return false;
      }
    }

    return true;
  }

  public boolean hasCovariantReturnTypeOf(@NonNull MethodDescriptor givenDesc) {
    if (this == givenDesc) {
      return true;
    }
    return returnType.isCovariantTypeOf(givenDesc.returnType);
  }

  public boolean isDefaultConstructorDescriptor() {
    AnalysisSession tm = returnType.getSession();
    return returnType.equals(tm.getPrimitiveType(Type.VOID_TYPE)) && parameters.length == 0;
  }

  public boolean hasSameReturnType(@NonNull MethodDescriptor desc) {
    return returnType.equals(desc.getReturnType());
  }
}
