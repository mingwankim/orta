package org.orta.core.type.locator;

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



import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.stream.Stream;

public interface ClassSource {

  @NonNull String getInternalName();

  @Nullable String getSuperClass();

  @NonNull Stream<String> streamInterfaces();

  int computeAccess();

  @NonNull Stream<MethodSource> streamMethods();

  @NonNull Stream<FieldSource> streamFields();

  boolean isReachable();

  boolean isConcrete();

//  final class InnerClassAttr {
//
//    private final int access;
//    private final Type innerClassName;
//    private final Type outerClassName;
//
//    InnerClassAttr(@Nullable String innerName, @Nullable String outerName, int access) {
//      if (innerName != null) {
//        this.innerClassName = Type.getObjectType(innerName);
//      } else {
//        this.innerClassName = null;
//      }
//
//      if (outerName != null) {
//        this.outerClassName = Type.getObjectType(outerName);
//      } else {
//        this.outerClassName = null;
//      }
//
//      this.access = access;
//    }
//
//    public int getAccess() {
//      return access;
//    }
//
//    public Type getInnerClass() {
//      return innerClassName;
//    }
//
//    public Type getOuterClass() {
//      return outerClassName;
//    }
//
//    @Override
//    public boolean equals(@Nullable Object obj) {
//      if (obj == null) {
//        return false;
//      }
//      if (obj == this) {
//        return true;
//      }
//      if (obj instanceof InnerClassAttr) {
//        InnerClassAttr o = (InnerClassAttr) obj;
//        return o.access == access && Objects.equals(innerClassName, o.innerClassName) && Objects
//            .equals(outerClassName, o.outerClassName);
//      }
//
//      return false;
//    }
//
//    @Override
//    public int hashCode() {
//      return Objects.hash(access, innerClassName, outerClassName);
//    }
//
//    @Override
//    public String toString() {
//      if (outerClassName != null) {
//        return outerClassName.toString() + " is enclosing " + innerClassName.toString()
//            + ", access flags = " + Integer.toBinaryString(access);
//      } else {
//        return "AnonymousClass, access flags = " + Integer.toBinaryString(access);
//      }
//    }
//  }
}
