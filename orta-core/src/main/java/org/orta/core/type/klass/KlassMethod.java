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



import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableSet;
import org.orta.core.cg.impacts.ImpactUnit;
import org.orta.core.type.AnalysisSession;
import org.orta.core.type.locator.MethodSource;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.objectweb.asm.Type;

import java.util.function.Supplier;

public class KlassMethod implements Flaggable.MethodFlag {

  private final int access;
  @NonNull
  private final Supplier<ImmutableSet<ImpactUnit>> body;
  @NonNull
  private final Klass declaringClass;
  @NonNull
  private final String methodName;
  @NonNull
  private final MethodDescriptor descriptor;

  private final boolean isPolymorphicSignature;

  private final boolean isReachable;

  public KlassMethod(@NonNull Klass klass, @NonNull MethodSource x,
                     @NonNull AnalysisSession analysisSession) {
    this.declaringClass = klass;
    this.access = x.getAccess();
    this.descriptor = analysisSession
            .getOrCreateMethodDescriptor(Type.getMethodType(x.getDescriptor()));
    this.methodName = x.getMethodName();
    this.body = Suppliers.memoize(() -> x.getImpacts().apply(analysisSession));
    this.isPolymorphicSignature = x.isPolymorphicSignature();
    this.isReachable = klass.isReachable() && x.isReachable();
  }

//  public KlassMethod(Klass declaringClass, IMethodDescriptor desc, AccessModifier accLevel,
//      EnumSet<Modifier> access, ImmutableSet<ImpactUnit> body) {
//    this.declaringClass = declaringClass;
//    this.descriptor = desc;
//    this.body = body;
//    this.access = access;
//    this.accLevel = accLevel;
//  }

  public boolean isReachable() {
    return isReachable;
  }

  public boolean isPolymorphicSignature() {
    return isPolymorphicSignature;
  }

  @NonNull
  public Klass getDeclaringClass() {
    return declaringClass;
  }

  @NonNull
  public MethodDescriptor getDescriptor() {
    return descriptor;
  }

  public ImmutableSet<ImpactUnit> getBody() {
    return body.get();
  }

  @Override
  public String toString() {
    return getSignature();
  }

  public int getAccess() {
    return access;
  }

  public boolean isOverriddenBy(@NonNull KlassMethod method) {
    return method.isOverriding(this);
  }

//	default boolean isInvokableFrom(Klass callerKlass) {
//		Klass thisKlass = getDeclaringClass();
//		if (thisKlass == callerKlass)
//			return true;
//
//		VisibleLevel visibleLevel = callerKlass.getVisibleLevelFrom(thisKlass);
//		AccessModifier accLevel = getAccessModifier();
//
//		switch (visibleLevel) {
//		case Class:
//			return true;
//		case Package:
//			if (accLevel == AccessModifier.PackagePrivate)
//				return true;
//		case Subclass:
//			if (accLevel == AccessModifier.Protected)
//				return true;
//		case World:
//			if (accLevel == AccessModifier.Public)
//				return true;
//		case None:
//			return false;
//		default:
//			throw new RuntimeException("Unexpected accessModifier: " + accLevel);
//		}
//	}

//	default boolean isInvokableFrom(IKlassMethod caller) {
//		if (this == caller)
//			return true;
//
//		return isInvokableFrom(caller.getDeclaringClass());
//	}

  public boolean isOverriding(@NonNull KlassMethod method) {
    if (method == this) {
      return true;
    }

    MethodDescriptor thisDesc = getDescriptor();
    MethodDescriptor givenDesc = method.getDescriptor();
    if (!thisDesc.hasSameParameter(givenDesc)) {
      return false;
    }

    if (!thisDesc.hasCovariantReturnTypeOf(givenDesc)) {
      return false;
    }

    return getDeclaringClass().inherits(method.getDeclaringClass());
  }

  @NonNull
  public String getMethodName() {
    return methodName;
  }

  public String getSignature() {
    return declaringClass.getTypeName() + "." + getSelector();
  }

  public String getSelector() {
    return methodName + descriptor;
  }

  public boolean isAccessableTo(KlassMethod caller) {
    if (caller == null) {
      return false;
    }

    Klass thisKlass = getDeclaringClass();
    Klass callerKlass = getDeclaringClass();

    if (callerKlass instanceof FakeCallerKlass) {
      return true;
    }

    if (isPublic()) {
      return true;
    }

    if (thisKlass.equals(callerKlass)) {
      // cover isPrivate()
      return true;
    }

    if (isProtected() && callerKlass.inherits(thisKlass)) {
      return true;
    }

    return thisKlass.isAccessibleTo(callerKlass);
  }
}
