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



import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import org.orta.core.type.AnalysisSession;
import org.orta.core.type.TypeLike;
import org.orta.core.type.locator.ClassSource;
import org.orta.core.type.locator.FieldSource;
import org.orta.core.type.locator.MethodSource;
import org.orta.core.util.Suppliers;
import org.checkerframework.checker.initialization.qual.UnderInitialization;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.dataflow.qual.SideEffectFree;
import org.objectweb.asm.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

public class Klass implements TypeLike, Flaggable.GeneralKlassFlag {
  public final static Predicate<KlassMethod> returnAlways = (m) -> true;

  private final static Logger logger = LoggerFactory.getLogger(
          Klass.class);
  private final Supplier<ImmutableList<Klass>> interfaces;
  private final Supplier<ImmutableListMultimap<String, KlassMethod>> methods;
  @NonNull
  private final String typeName;
  @NonNull
  private final String internalName;
  @NonNull
  private final Supplier<ImmutableList<KlassField>> fields;
  @NonNull
  private final Supplier<Klass> superClass;
  private final boolean isConcrete;
  //  @NonNull
//  private final Supplier<EnclosingAttrib> enclosingAttrib;
  @NonNull
  private final String packageName;
  private final int access;
  @NonNull
  private final AnalysisSession tm;
  private final boolean isReachable;

  //  public boolean isAccessibleTo(Klass declaringClass, MemberFlag member) {
//    if (isAccessibleTo(declaringClass)) {
//      if (member.isPublic()) {
//        return true;
//      }
//
//      if (member.isPrivate()) {
//        Klass ptr = this;
//        while (ptr != null) {
//          if (enclosingAttrib.is(ptr)) {
//            return true;
//          }
//          ptr = ptr.outerKlass.getPointer();
//        }
//
//        return false;
//      }
//
//      // if (member.isPackagePrivate() || member.isProtected)
//      if (packageName.equals(declaringClass.packageName)) {
//        return true;
//      }
//
//      if (member.isPackagePrivate()) {
//        return false;
//      }
//
//      // if (member.isProtected())
//      return inherits(declaringClass);
//    }
//
//    return false;
//  }
//
//  public boolean isAccessibleTo(Klass target) {
//    return target.isAccessibleFrom(this);
//  }
//
//  public boolean isAccessibleFrom(Klass origin) {
//    if (isPublic()) {
//      return outer == null || outer.isAccessibleFrom(origin);
//    }
//
//    if (outer == null) {
//      return isPublic() || packageName.equals(origin.packageName);
//    }
//
//    return origin.isAccessibleTo(outer, this);
//  }
  @MonotonicNonNull
  private String desc;

  @SuppressWarnings("initialization")
  public Klass(@NonNull AnalysisSession tm, ClassSource source) {
    this.tm = tm;
    this.internalName = source.getInternalName();
    this.typeName = internalName.replace("/", ".");
    int packageIndex = this.typeName.lastIndexOf('.');
    if (packageIndex == -1) {
      this.packageName = "";
    } else {
      this.packageName = this.typeName.substring(0, packageIndex);
    }
    this.isReachable = source.isReachable();
    this.access = source.computeAccess();
    this.isConcrete = source.isConcrete();

    // These fields are lazily analyzed because of cyclic dependencies among classes.
    this.superClass = Suppliers.memorize(() -> getKlassOrNull(source.getSuperClass()));
    this.interfaces = Suppliers.memorize(() -> resolveInterfaces(source.streamInterfaces()));
//    this.enclosingAttrib = Suppliers.memorize(() -> resolveEnclosing(tm, source));
    this.methods = Suppliers.memorize(() -> resolveMethods(source.streamMethods()));
    this.fields = Suppliers.memorize(() -> resolveFields(source.streamFields()));
  }

  private static ImmutableList<KlassField> resolveFields(Stream<FieldSource> src) {
    // TODO
    return ImmutableList.of();
  }

//  public boolean isEnclosing(Klass klass) {
//    Klass ptr = klass;
//    while (ptr != null) {
//      if (ptr.equals(this)) {
//        return true;
//      }
//      ptr = ptr.outerKlass.getPointer();
//    }
//
//    return false;
//  }
//
//  public boolean isEnclosedBy(Klass klass) {
//    return klass.isEnclosing(this);
//  }

  private static boolean isMatchableDescriptor(MethodDescriptor methodDesc,
                                               MethodDescriptor selector) {
    return methodDesc.hasCovariantReturnTypeOf(selector) && methodDesc.hasSameParameter(selector);
  }

  public boolean isConcrete() {
    if (!isConcrete) {
      return false;
    }

    Klass parent = this.superClass.get();
    if (parent != null && !parent.isConcrete()) {
      return false;
    }

    List<Klass> itf = interfaces.get();

    if (itf.size() > 0) {
      return itf.stream().allMatch(Klass::isConcrete);
    } else {
      return true;
    }
  }

  @Nullable
  private Klass getKlassOrNull(@Nullable String className) {
    if (className == null) {
      return null;
    } else {
      return tm.getOrCreateKlass(className);
    }
  }

  public boolean isReachable() {
    if (!isReachable) {
      return false;
    }

    Klass parent = this.superClass.get();
    if (parent != null && !parent.isReachable()) {
      return false;
    }

    return interfaces.get().stream().allMatch(Klass::isReachable);
  }

//  private EnclosingAttrib resolveEnclosing(@NonNull AnalysisSession tm, @NonNull ClassSource source) {
//    String outerClassName = source.getOuterKlass();
//    if (outerClassName != null) {
//      String methodDesc = source.getOuterMethodDesc();
//      String methodName = source.getOuterMethodName();
//      Preconditions.checkState(methodDesc != null && methodName != null);
//
//      Klass klass = tm.getOrCreateKlass(Type.getObjectType(outerClassName));
//      MethodDescriptor desc = tm.getOrCreateMethodDescriptor(Type.getMethodType(methodDesc));
//      KlassMethod enclosingMethod = klass.tryClosestInvocation(methodName, desc);
//
//      Optional<InnerClassAttr> attrib = source.getMemberAccess().findFirst();
//      Preconditions.checkState(attrib.isPresent());
//      InnerClassAttr attr = attrib.getPointer();
//      Preconditions.checkState(attr.getInnerClass() == null && attr.getOuterClass() == null);
//
//      return EnclosingAttrib.byMethod(enclosingMethod, attrib.getPointer().getAccess());
//    } else {
//      Optional<InnerClassAttr> attrib = source.getMemberAccess()
//          .filter(x -> tm.getOrCreateKlass(x.getInnerClass()).equals(this)).findFirst();
//      if (attrib.isPresent()) {
//        InnerClassAttr attr = attrib.getPointer();
//        Klass outerKlass = tm.getOrCreateKlass(attr.getOuterClass());
//        return EnclosingAttrib.byClass(outerKlass, attr.getAccess());
//      }
//    }
//    return null;
//  }

  @UnderInitialization
  private ImmutableList<Klass> resolveInterfaces(Stream<String> interfaces) {
    return interfaces.map(Type::getObjectType)
            .map(tm::getOrCreateKlass)
            .collect(ImmutableList.toImmutableList());
  }

  private ImmutableListMultimap<String, KlassMethod> resolveMethods(
          Stream<MethodSource> src) {
    ImmutableListMultimap.Builder<String, KlassMethod> builder = ImmutableListMultimap.builder();
    src.map(this::createMethod).forEach(x -> builder.put(x.getMethodName(), x));
    return builder.build();
  }

  private KlassMethod createMethod(MethodSource methodSource) {
    MethodSource simulated = WALANativeModel.simulateSource(this, methodSource);
    return new KlassMethod(this, simulated != null ? simulated : methodSource, tm);
  }

  public @NonNull ImmutableList<Klass> getInterfaces() {
    return interfaces.get();
  }

  @NonNull
  public ImmutableList<KlassField> getFields() {
    return fields.get();
  }

  @NonNull
  public String getPackageName() {
    return packageName;
  }

  @Nullable
  public Klass getSuperClass() {
    return superClass.get();
  }

  @NonNull
  public String getInternalName() {
    return internalName;
  }

  public boolean inherits(Klass klass) {
    if (klass.equals(this)) {
      return true;
    }

    Klass next = superClass.get();
    if (next != null && next.inherits(klass)) {
      return true;
    }
    for (Klass itf : interfaces.get()) {
      if (itf.inherits(klass)
      ) {
        return true;
      }
    }

    return false;
  }

  public @NonNull Stream<Klass> streamInterfaces() {
    return interfaces.get().stream();
  }

//  @Override
//  public int getAccess() {
//    if (!innerAccessResolved) {
//      innerAccessResolved = true;
//      Klass outerKlass = getOuterKlass();
//      if (outerKlass == null) {
//        return access;
//      }
//      int meta = outerKlass.innerKlasses.getPointer().getPointer(this);
//      access |= meta;
//    }
//
//    return access;
//  }

  public @NonNull Stream<KlassMethod> streamDeclaringMethods() {
    return methods.get().values().stream();
  }

  @Override
  public String toString() {
    return getTypeName();
  }

  @Override
  @SideEffectFree
  public @NonNull String getTypeName() {
    return typeName;
  }

  @Override
  public @NonNull String getTypeDescriptor() {
    if (desc == null) {
      desc = "L" + internalName + ";";
    }

    return desc;
  }

  @Override
  public boolean isCovariantTypeOf(@NonNull TypeLike type) {
    if (type instanceof Klass) {
      return inherits((Klass) type);
    }

    return false;
  }

  @Override
  public @NonNull AnalysisSession getSession() {
    return tm;
  }

  @Nullable
  public KlassMethod getClassInitializer() {
    List<KlassMethod> list = methods.get().get("<clinit>");
    int size = list.size();
    if (size == 1) {
      return list.get(0);
    } else if (size == 0) {
      return null;
    } else {
      throw new IllegalStateException();
    }
  }

  public boolean inherited(Klass klass) {
    return klass.inherits(this);
  }

  @Nullable
  public KlassMethod tryExactInvocation(String name, MethodDescriptor desc) {
    List<KlassMethod> list = methods.get().get(name);
    return list.stream()
            .filter(x -> x.getDescriptor().equals(desc)).findFirst().orElse(null);
  }

  @Nullable
  public KlassMethod tryClosestInvocation(String name, MethodDescriptor desc) {
    List<KlassMethod> list = methods.get().get(name);
    Iterator<KlassMethod> iterator = list.stream()
            .filter(x -> x.isPolymorphicSignature() || x.getDescriptor().isCovariantOf(desc))
            .iterator();

    if (!iterator.hasNext()) {
      return null;
    }

    KlassMethod m = iterator.next();
    if (m.isPolymorphicSignature()) {
      return m;
    }

    MethodDescriptor md = m.getDescriptor();

    while (iterator.hasNext()) {
      KlassMethod c = iterator.next();
      MethodDescriptor d = c.getDescriptor();
      if (c.isPolymorphicSignature()) {
        return c;
      }

      if (d.isCovariantOf(md)) {
        m = c;
        md = d;
      }
    }

    return m;
  }

  @Override
  public int getAccess() {
    return access;
  }

  public boolean isAccessibleTo(Klass candidate) {
    if (candidate == null) {
      return false;
    }

    if (candidate instanceof FakeCallerKlass) {
      return true;
    }

    if (isPublic()) {
      return true;
    }

    return packageName.equals(candidate.packageName);
  }

//  public boolean isAccessibleTo(KlassMethod exactMethod) {
//    return isAccessibleTo(exactMethod.getDeclaringClass(), exactMethod);
//  }
}
