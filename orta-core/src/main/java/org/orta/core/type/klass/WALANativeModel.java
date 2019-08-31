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



import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableTable;
import org.orta.core.cg.impacts.ImpactFactory.ImpactBuilder;
import org.orta.core.cg.impacts.ImpactUnit;
import org.orta.core.type.AnalysisSession;
import org.orta.core.type.TypeHelper;
import org.orta.core.type.locator.MethodSource;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.EndElement;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;
import java.io.IOException;
import java.io.InputStream;
import java.util.function.BiConsumer;
import java.util.function.Function;

public class WALANativeModel {

  static final QName ATTR_DEF = new QName("def");
  static final QName ATTR_FACTORY = new QName("factory");
  static final QName ATTR_STATIC = new QName("static");
  static final QName ATTR_VALUE = new QName("value");
  private static final QName ATTR_CLASS = new QName("class");
  private static final QName ATTR_DESC = new QName("descriptor");
  private static final QName ATTR_NAME = new QName("name");
  private static final QName ATTR_TYPE = new QName("type");
  private static final Logger logger = LoggerFactory.getLogger(
          WALANativeModel.class);
  @MonotonicNonNull
  private static ImmutableMap<String, ImmutableTable<String, String, BiConsumer<AnalysisSession, ImpactBuilder>>> methods;

  private WALANativeModel() {
  }

  private static @NonNull String getAttrib(@NonNull StartElement elem, @NonNull QName tag) {
    String value = tryAttrib(elem, tag);
    assert value != null : "Could not find an expected attribute: " + tag + " in " + elem;
    return elem.getAttributeByName(tag).getValue();
  }

  private static @Nullable String tryAttrib(@NonNull StartElement elem, @NonNull QName tag) {
    Attribute attr = elem.getAttributeByName(tag);
    if (attr == null) {
      return null;
    }
    return attr.getValue();
  }

  private static @NonNull String getName(@NonNull EndElement elem) {
    return elem.getName().getLocalPart();
  }

  private static @NonNull String getName(@NonNull StartElement elem) {
    return elem.getName().getLocalPart();
  }

  private static ImmutableMap<String, ImmutableTable<String, String, BiConsumer<AnalysisSession, ImpactBuilder>>> initialize()
          throws IOException, XMLStreamException {
    NativeBuilderContext ctx = new NativeBuilderContext();
    XMLInputFactory xml = XMLInputFactory.newInstance();
    ClassLoader loader = Preconditions.checkNotNull(
            WALANativeModel.class.getClassLoader());
    try (@NonNull InputStream input =
                 Preconditions.checkNotNull(loader.getResourceAsStream("natives.xml"))) {
      XMLEventReader reader = xml.createXMLEventReader(input);
      while (reader.hasNext()) {
        XMLEvent event = reader.nextEvent();
        if (event.isStartElement()) {
          if (!ctx.onStartElement(event.asStartElement())) {
            throw new IOException("Unknown event: " + event);
          }
        } else if (event.isEndElement()) {
          EndElement elem = event.asEndElement();
          if (!ctx.onEndElement(elem)) {
            throw new IOException("Unknown event: " + event);
          }
        }
      }
    }

    return ctx.build();
  }

  public static @Nullable MethodSource simulateSource(Klass declaringClass, MethodSource source) {
    if (methods == null) {
      try {
        methods = initialize();
      } catch (IOException | XMLStreamException e) {
        throw new RuntimeException(e);
      }
    }

    String key = declaringClass.getTypeName();
    ImmutableTable<String, String, BiConsumer<AnalysisSession, ImpactBuilder>> list = methods
            .get(key);
    if (list == null) {
      return null;
    }

    BiConsumer<AnalysisSession, ImpactBuilder> builder = list
            .get(source.getMethodName(), source.getDescriptor());
    if (builder == null) {
      return null;
    }

    logger.debug("{}.{}{}", declaringClass.getTypeName(), source.getMethodName(),
            source.getDescriptor());

    return new WALAMethodSource(source, builder);
  }

  private static class NativeBuilderContext {

    private final ImmutableMap.Builder<String, ImmutableTable<String, String, BiConsumer<AnalysisSession, ImpactBuilder>>> class2Table = ImmutableMap
            .builder();
    @Nullable
    private String qualifiedName = null;
    @Nullable
    private MethodBuilder methodBuilder = null;
    private ImmutableTable.Builder<String, String, BiConsumer<AnalysisSession, ImpactBuilder>> builderTable = ImmutableTable
            .builder();
    @Nullable
    private StringBuilder classNameBuilder = null;
    private int classNameIndex = 0;

    @SuppressWarnings("SameReturnValue")
    private boolean updatePackageInfo(@NonNull StartElement elem) {
      String packageName = getAttrib(elem, ATTR_NAME);
      classNameBuilder = new StringBuilder(packageName.replace("/", ".")).append(".");
      classNameIndex = classNameBuilder.length();
      methodBuilder = null;
      return true;
    }

    private boolean updateClassInfo(@NonNull StartElement elem) {
      StringBuilder nameBuilder = Preconditions.checkNotNull(classNameBuilder);
      nameBuilder.delete(classNameIndex, nameBuilder.length())
              .append(getAttrib(elem, ATTR_NAME));
      qualifiedName = nameBuilder.toString();
      methodBuilder = null;
      return true;
    }

    private boolean initializeMethodBuilder(@NonNull StartElement elem) {
      methodBuilder = new MethodBuilder(elem);
      return true;
    }

    @SuppressWarnings("UnusedReturnValue")
    boolean onStartElement(@NonNull StartElement elem) {
      if (methodBuilder != null && methodBuilder.acceptStart(elem)) {
        return true;
      }

      String tagName = getName(elem);
      switch (tagName) {
        case "package":
          return updatePackageInfo(elem);
        case "class":
          return updateClassInfo(elem);
        case "method":
          return initializeMethodBuilder(elem);
        case "summary-spec":
        case "classloader":
          return true;
      }

      return false;
    }

    @SuppressWarnings("UnusedReturnValue")
    boolean onEndElement(EndElement elem) {
      String tagName = getName(elem);
      switch (tagName) {
        case "method":
          return registerMethodBuilder();
        case "class":

          return registerClassBuilders();
        case "summary-spec":
        case "classloader":
          return true;
      }

      return true;
    }

    private boolean registerMethodBuilder() {
      MethodBuilder builder = Preconditions.checkNotNull(methodBuilder);
      builderTable.put(builder.getMethodName(), builder.getDescriptor(), builder.impacts);
      return true;
    }

    private boolean registerClassBuilders() {
      String className = Preconditions.checkNotNull(qualifiedName);
      class2Table.put(className, builderTable.build());
      builderTable = ImmutableTable.builder();
      return true;
    }

    ImmutableMap<String, ImmutableTable<String, String, BiConsumer<AnalysisSession, ImpactBuilder>>> build() {
      return class2Table.build();
    }
  }

  private static class MethodBuilder {

    final String descriptor;
    final String methodName;
    BiConsumer<AnalysisSession, ImpactBuilder> impacts = (s, b) -> {
    };

    MethodBuilder(StartElement elem) {
      this.descriptor = getAttrib(elem, ATTR_DESC);
      this.methodName = getAttrib(elem, ATTR_NAME);

      Preconditions.checkState(Type.getType(descriptor).getSort() == Type.METHOD);
    }


    boolean acceptStart(StartElement elem) {
      String tagName = getName(elem);
      switch (tagName) {
        case "new":
          return instantiated(elem);
        case "constant":
          return setConstant(elem);
        case "putstatic":
        case "getstatic":
          return classref(elem);
        case "return":
        case "aastore":
        case "throw":
        case "putfield":
        case "getfield":
        case "poison":
          // Because only RTA is implemented, these information is not necessary.
          return true;
        case "call":
          return registerInvoke(elem);
      }

      return false;
    }

    private boolean classref(StartElement elem) {
      Type rawType = getObjectTypeAttr(elem);
      Type type = TypeHelper.getReferencedType(rawType);
      if (type == null) {
        return true;
      }

      impacts = impacts.andThen((s, b) -> b.referenceType(s.getOrCreateKlass(type)));
      return true;
    }

    String getDescriptor() {
      return descriptor;
    }

    private Type getObjectTypeAttr(StartElement elem) {
      String value = getAttrib(elem, ATTR_CLASS);
      return Type.getType(value + ";");
    }

    private boolean instantiated(StartElement elem) {
      Type rawType = getObjectTypeAttr(elem);
      Type type = TypeHelper.getReferencedType(rawType);
      if (type == null) {
        return true;
      }

      impacts = impacts.andThen((s, b) -> b.createObject(s.getOrCreateKlass(type)));
      return true;
    }

    private boolean registerInvoke(StartElement elem) {
      Type typeName = getObjectTypeAttr(elem);

      String invokeType = getAttrib(elem, ATTR_TYPE);
      String name = getAttrib(elem, ATTR_NAME);
      Type desc = Type.getType(getAttrib(elem, ATTR_DESC));

      switch (invokeType) {
        case "virtual":
          impacts = impacts.andThen((s, b) -> b.invokeVirtual(s.getOrCreateKlass(typeName), name,
                  s.getOrCreateMethodDescriptor(desc)));
          break;
        case "static":
          impacts = impacts.andThen((s, b) -> b.invokeStatic(s.getOrCreateKlass(typeName), name,
                  s.getOrCreateMethodDescriptor(desc)));
          break;
        case "special":
          impacts = impacts.andThen((s, b) -> b.invokeSpecial(s.getOrCreateKlass(typeName), name,
                  s.getOrCreateMethodDescriptor(desc)));
          break;
        case "interface":
          impacts = impacts.andThen((s, b) -> b.invokeInterface(s.getOrCreateKlass(typeName), name,
                  s.getOrCreateMethodDescriptor(desc)));
          break;
        default:
          throw new IllegalArgumentException(invokeType);
      }

      return true;
    }

    private boolean setConstant(StartElement elem) {
      return true;
    }

    String getMethodName() {
      return methodName;
    }
  }

  private static class WALAMethodSource implements MethodSource {

    private final BiConsumer<AnalysisSession, ImpactBuilder> builder;
    private final MethodSource origin;

    WALAMethodSource(MethodSource origin, BiConsumer<AnalysisSession, ImpactBuilder> builder) {
      this.builder = builder;
      this.origin = origin;
    }

    @Override
    public int getAccess() {
      return origin.getAccess() & ~Opcodes.ACC_NATIVE;
    }

    @Override
    public String getDescriptor() {
      return origin.getDescriptor();
    }

    @Override
    public Function<AnalysisSession, ImmutableSet<ImpactUnit>> getImpacts() {
      return (tm) -> {
        ImpactBuilder b = tm.createImpactBuilder();
        builder.accept(tm, b);
        return b.build();
      };
    }

    @Override
    public String getMethodName() {
      return origin.getMethodName();
    }

    @Override
    public boolean isPolymorphicSignature() {
      return false;
    }

    @Override
    public boolean isReachable() {
      return true;
    }
  }

}
