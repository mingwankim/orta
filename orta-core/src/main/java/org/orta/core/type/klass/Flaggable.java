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



import org.objectweb.asm.Opcodes;

public interface Flaggable {

  int getAccess();

  default boolean isPackagePrivate() {
    return !isSet(Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED | Opcodes.ACC_PUBLIC);
  }

  default boolean isSet(int flag) {
    return (getAccess() & flag) != 0;
  }

  default boolean isPublic() {
    return isSet(Opcodes.ACC_PUBLIC);
  }

  default boolean isFinal() {
    return isSet(Opcodes.ACC_FINAL);
  }

  default boolean isSynthetic() {
    return isSet(Opcodes.ACC_SYNTHETIC);
  }

  interface MemberFlag extends Flaggable {

    default boolean isPrivate() {
      return isSet(Opcodes.ACC_PRIVATE);
    }

    default boolean isProtected() {
      return isSet(Opcodes.ACC_PROTECTED);
    }

    default boolean isStatic() {
      return isSet(Opcodes.ACC_STATIC);
    }
  }

  interface KlassFlag extends Flaggable {
/*
    default boolean isSuper() {
      return isSet(Opcodes.ACC_SUPER);
    }
    */
  }

  interface GeneralKlassFlag extends Flaggable, /* For inner classes */
          MemberFlag, /* For non-inner classes */ KlassFlag {
    default boolean isAbstract() {
      return isSet(Opcodes.ACC_ABSTRACT);
    }

    default boolean isInterface() {
      return isSet(Opcodes.ACC_INTERFACE);
    }

    default boolean isEnumerationClass() {
      return isSet(Opcodes.ACC_ENUM);
    }

    default boolean isAnnotationClass() {
      return isSet(Opcodes.ACC_ANNOTATION);
    }
  }

  interface FieldFlag extends Flaggable, MemberFlag {

    default boolean isVolatile() {
      return isSet(Opcodes.ACC_VOLATILE);
    }

    default boolean isTransient() {
      return isSet(Opcodes.ACC_TRANSIENT);
    }

    default boolean isEnumElement() {
      return isSet(Opcodes.ACC_ENUM);
    }
  }

  interface MethodFlag extends Flaggable, MemberFlag {

    default boolean isImplementation() {
      return !isSet(Opcodes.ACC_ABSTRACT);
    }

    default boolean isOverridable() {
      return isSet(Opcodes.ACC_FINAL | Opcodes.ACC_PRIVATE);
    }

    default boolean isSynced() {
      return isSet(Opcodes.ACC_SYNCHRONIZED);
    }

    default boolean isBridge() {
      return isSet(Opcodes.ACC_BRIDGE);
    }

    default boolean hasVarArgs() {
      return isSet(Opcodes.ACC_VARARGS);
    }

    default boolean isNative() {
      return isSet(Opcodes.ACC_NATIVE);
    }

    default boolean isAbstract() {
      return isSet(Opcodes.ACC_ABSTRACT);
    }

    default boolean isStrict() {
      return isSet(Opcodes.ACC_STRICT);
    }
  }
}
