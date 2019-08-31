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



import com.google.common.base.Strings;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

class ArrayType implements TypeLike {

  @NonNull
  private final TypeLike elementType;
  private final int dimension;
  @MonotonicNonNull
  private String desc = null;

  ArrayType(@NonNull TypeLike elementType, int dimension) {
    assert dimension > -1;

    this.elementType = elementType;
    this.dimension = dimension;
  }

  @Override
  public @NonNull String getTypeName() {
    return elementType.getTypeName() + Strings.repeat("[]", dimension);
  }

  @Override
  public @NonNull String getTypeDescriptor() {
    if (desc == null) {
      desc = Strings.repeat("[", dimension) + elementType.getTypeDescriptor();
    }

    return desc;
  }

  @Override
  public boolean isCovariantTypeOf(@NonNull TypeLike type) {
    if (type instanceof ArrayType) {
      return elementType.isCovariantTypeOf(((ArrayType) type).elementType);
    }

    return false;
  }

  @Override
  public @NonNull AnalysisSession getSession() {
    return elementType.getSession();
  }

  @NonNull
  public TypeLike getElementType() {
    return elementType;
  }

  @Nullable
  public TypeLike getElementTypeOf(Class<?> type) {
    return type.isInstance(elementType) ? elementType : null;
  }

  @Override
  public String toString() {
    return getTypeName();
  }
}
