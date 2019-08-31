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



import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.orta.core.cg.impacts.ImpactFactory;
import org.orta.core.cg.impacts.ImpactUnit;
import org.orta.core.type.klass.FakeCallerKlass;
import org.orta.core.type.klass.Klass;
import org.orta.core.type.klass.KlassMethod;
import org.orta.core.type.klass.MethodDescriptor;
import org.orta.core.type.locator.ClassSource;
import org.orta.core.type.locator.ClassSourceLocator;
import org.orta.core.type.locator.EmptyClassSource;
import org.orta.core.type.locator.FieldSource;
import org.orta.core.type.locator.MethodSource;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.StringJoiner;
import java.util.function.Function;
import java.util.stream.Stream;

public class AnalysisSession implements AutoCloseable {

  private static final int[] primitives = new int[]{Type.VOID, Type.BOOLEAN, Type.CHAR, Type.BYTE,
          Type.SHORT, Type.INT, Type.FLOAT, Type.LONG, Type.DOUBLE};
  private static final Logger logger = LoggerFactory.getLogger(
          AnalysisSession.class);
  @VisibleForTesting
  @SuppressWarnings("initialization") // the this keyword does not need to be initialized.
  final Map<String, PrimitiveType> primTypes = Stream
          .of("I", "V", "Z", "B", "C", "S", "D", "F", "J")
          .collect(ImmutableMap.toImmutableMap(
                  Function.identity(),
                  x -> new PrimitiveType(this, x)
          ));
  @NonNull
  private final ClassSourceLocator locator;
  @NonNull
  private final Map<String, Klass> name2Ref = new HashMap<>();
  private final Map<Type, MethodDescriptor> desc2method = new HashMap<>();
  private final Map<Type, ArrayType> desc2arr = new HashMap<>();
  private final ImpactFactory factory = new ImpactFactory();
  private final Klass fakeKlass = new FakeCallerKlass(this);
  private Klass[] essentialKlasses;

  public AnalysisSession(@NonNull ClassSourceLocator classSourceLocator) {
    this.locator = classSourceLocator;
  }

  private static void expectPrimitives(@NonNull Type desc) {
    int typeSort = desc.getSort();
    for (int sort : primitives) {
      if (sort == typeSort) {
        return;
      }
    }

    throw new IllegalArgumentException(
            "Unexpected descriptor type: " + desc + " is given");
  }

  public void close() {
    locator.close();
  }

  public KlassMethod getFakeCaller() {
    return getFakeKlass().streamDeclaringMethods().findFirst().get();
  }

  public Klass getFakeKlass() {
    return fakeKlass;
  }

  public Klass[] getEssentialKlasses() {
    if (this.essentialKlasses == null) {
      this.essentialKlasses = Stream.of(
              Object.class,
              ExceptionInInitializerError.class,
              ArithmeticException.class,
              ClassCastException.class,
              ClassNotFoundException.class,
              IndexOutOfBoundsException.class,
              NegativeArraySizeException.class
      )
              .map(this::getOrCreateKlass)
              .toArray(Klass[]::new);
    }

    return essentialKlasses;
  }

  public @NonNull Klass getOrCreateKlass(
          @NonNull String rawType) {
    if (rawType.equals("FakeKlass")) {
      return getFakeKlass();
    }

    return getOrCreateKlass(Type.getObjectType(rawType));
  }

  public @NonNull Klass getOrCreateKlass(
          @NonNull Type rawType) {
    Preconditions.checkArgument(rawType.getSort() == Type.OBJECT, rawType);
    String className = rawType.getInternalName();
    if (className.equals("FakeKlass")) {
      return getFakeKlass();
    }

    return name2Ref.computeIfAbsent(className, this::createKlass);
  }

  public Klass getOrCreateKlass(Class<?> cls) {
    return getOrCreateKlass(Type.getType(cls));
  }

  public @NonNull ArrayType getOrCreateArrayType(@NonNull Type desc) {
    Preconditions.checkArgument(desc.getSort() == Type.ARRAY);

    return desc2arr.computeIfAbsent(desc, this::createArrayType);
  }

  private @NonNull ArrayType createArrayType(@NonNull Type desc) {
    Type elemType = desc.getElementType();
    TypeLike type;
    if (elemType.getSort() == Type.OBJECT) {
      type = getOrCreateKlass(elemType);
    } else {
      type = getPrimitiveType(elemType);
    }

    return new ArrayType(type, desc.getDimensions());
  }

  private Klass createKlass(@NonNull String typeName) {
    ClassSource source;
    try {
      source = locator.lookupSource(typeName);
    } catch (IOException e) {
      source = EmptyClassSource.get(typeName, e);
    }

    if (source == null) {
      source = EmptyClassSource.get(typeName, null);
    }

    return new Klass(this, source);
  }

  public PrimitiveType getPrimitiveType(@NonNull Type desc) {
    expectPrimitives(desc);
    PrimitiveType type = primTypes.get(desc.getInternalName());
    assert type != null : "@AssumeAssertion(nullness)";
    return type;
  }

  public @NonNull MethodDescriptor getOrCreateMethodDescriptor(@NonNull Type desc) {
    Preconditions.checkArgument(desc.getSort() == Type.METHOD);

    return desc2method.computeIfAbsent(desc, this::createMethodDescriptor);
  }

  private @NonNull MethodDescriptor createMethodDescriptor(@NonNull Type desc) {
    assert desc.getSort() == Type.METHOD;

    Type returnType = desc.getReturnType();
    Type[] parameters = desc.getArgumentTypes();
    TypeLike returnTypeLike = getOrCreateTypeLike(returnType);
    TypeLike[] paramTypeLike = new TypeLike[parameters.length];
    for (int i = 0; i < parameters.length; ++i) {
      paramTypeLike[i] = getOrCreateTypeLike(parameters[i]);
    }

    return new MethodDescriptor(returnTypeLike, paramTypeLike);
  }

  private @NonNull TypeLike getOrCreateTypeLike(@NonNull Type desc) {
    Preconditions.checkArgument(desc.getSort() != Type.METHOD);

    switch (desc.getSort()) {
      case Type.ARRAY:
        return getOrCreateArrayType(desc);
      case Type.OBJECT:
        return getOrCreateKlass(desc);
      case Type.BOOLEAN:
      case Type.BYTE:
      case Type.CHAR:
      case Type.DOUBLE:
      case Type.FLOAT:
      case Type.INT:
      case Type.LONG:
      case Type.SHORT:
      case Type.VOID:
        return getPrimitiveType(desc);
      default:
        throw new IllegalArgumentException();
    }
  }

  public @NonNull MethodDescriptor getOrCreateMethodDescriptor(@NonNull String desc) {
    return getOrCreateMethodDescriptor(Type.getMethodType(desc));
  }

  public ImpactFactory.ImpactBuilder createImpactBuilder() {
    return factory.builder();
  }

  public Klass createLambdaKlass(
          String bootstrapper, String superClassName,
          String implementedMethodName, Klass invokedKlass, String invokedName,
          MethodDescriptor invokedDesc, ImmutableSet<ImpactUnit> impacts) {
    if (impacts.isEmpty()) {
      // This is a lambda klass that do nothing.
      return null;
    }

    StringJoiner joiner = new StringJoiner("#");
    joiner.add(bootstrapper);
    joiner.add(superClassName);
    joiner.add(implementedMethodName);
    joiner.add(invokedKlass.getInternalName());
    joiner.add(invokedName);
    joiner.add(invokedDesc.toString());

    Klass itf = getOrCreateKlass(superClassName);

    final String desc;
    final boolean isConcrete = itf.isConcrete();
    if (invokedName.equals("<init>")) {
      if (isConcrete) {
        KlassMethod method = itf.streamDeclaringMethods().findFirst()
                .orElseThrow(AssertionError::new);
        desc = method.getDescriptor().toString();
      } else {
        // This lambda is meaningless because it aims to create "class::new" lambda but "class" is not loaded.
        return null;
      }
    } else {
      desc = invokedDesc.toString();
    }

    return name2Ref.compute("rtscg.lambda." + joiner.toString(),
            (klassName, old) -> old != null ? old : new Klass(this, new ClassSource() {
              @Override
              public @NonNull String getInternalName() {
                return klassName;
              }

              @Override
              public @NonNull String getSuperClass() {
                return "java/lang/Object";
              }

              @Override
              public @NonNull Stream<String> streamInterfaces() {
                return Stream.of(superClassName);
              }

              @Override
              public int computeAccess() {
                return Opcodes.ACC_PUBLIC;
              }

              @Override
              public @NonNull Stream<MethodSource> streamMethods() {
                return Stream.of(new MethodSource() {
                  @Override
                  public String getDescriptor() {
                    return desc;
                  }

                  @Override
                  public Function<AnalysisSession, ImmutableSet<ImpactUnit>> getImpacts() {
                    return (x) -> impacts;
                  }

                  @Override
                  public String getMethodName() {
                    return implementedMethodName;
                  }

                  @Override
                  public boolean isPolymorphicSignature() {
                    return false;
                  }

                  @Override
                  public boolean isReachable() {
                    return true;
                  }

                  @Override
                  public int getAccess() {
                    return Opcodes.ACC_PUBLIC;
                  }
                });
              }

              @Override
              public @NonNull Stream<FieldSource> streamFields() {
                return Stream.empty();
              }

              @Override
              public boolean isReachable() {
                return isConcrete;
              }

              @Override
              public boolean isConcrete() {
                return isConcrete;
              }
            }));
  }

  public ImpactFactory getImpactFactory() {
    return factory;
  }

  private static class PrimitiveType implements TypeLike {


    private final @NonNull String internalName;
    private final @NonNull String typeName;
    private final @NonNull AnalysisSession tm;

    PrimitiveType(@NonNull AnalysisSession tm, @NonNull String internalName) {
      this.internalName = internalName;
      this.tm = tm;
      switch (internalName) {
        case "I":
          typeName = "Int";
          break;
        case "V":
          typeName = "Void";
          break;
        case "Z":
          typeName = "Boolean";
          break;
        case "B":
          typeName = "Byte";
          break;
        case "C":
          typeName = "Char";
          break;
        case "S":
          typeName = "Short";
          break;
        case "D":
          typeName = "Double";
          break;
        case "F":
          typeName = "Float";
          break;
        case "J":
          typeName = "Long";
          break;
        default:
          throw new IllegalArgumentException();
      }
    }

    @Override
    public @NonNull String getTypeName() {
      return typeName;
    }

    @Override
    public @NonNull String getTypeDescriptor() {
      return internalName;
    }

    @Override
    public boolean isCovariantTypeOf(@NonNull TypeLike type) {
      return type.equals(this);
    }

    @Override
    public @NonNull AnalysisSession getSession() {
      return tm;
    }

    @Override
    public String toString() {
      return typeName;
    }
  }
}
