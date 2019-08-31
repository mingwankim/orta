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



import com.google.common.collect.ImmutableSet;
import org.orta.core.cg.impacts.ImpactFactory.ImpactBuilder;
import org.orta.core.cg.impacts.ImpactUnit;
import org.orta.core.type.AnalysisSession;
import org.orta.core.type.TypeHelper;
import org.orta.core.type.klass.Klass;
import org.orta.core.type.klass.MethodDescriptor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.MethodNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Function;

import static org.objectweb.asm.Opcodes.ASM6;

public class AsmMethodSource implements MethodSource {

  private static final Logger logger = LoggerFactory.getLogger(
          AsmMethodSource.class);
  private final MethodNode node;

  public AsmMethodSource(MethodNode node) {
    this.node = node;
  }

  @Override
  public String getDescriptor() {
    return node.desc;
  }

  @Override
  public Function<AnalysisSession, ImmutableSet<ImpactUnit>> getImpacts() {
    return this::generateImpacts;
  }

  private ImmutableSet<ImpactUnit> generateImpacts(AnalysisSession tm) {
    InstVisitor visitor = new InstVisitor(tm);
    node.instructions.accept(visitor);
    return ImmutableSet.copyOf(visitor.impacts.build());
  }


  @Override
  public String getMethodName() {
    return node.name;
  }

  @Override
  public boolean isPolymorphicSignature() {
    if (node.visibleAnnotations == null) {
      return false;
    }

    for (AnnotationNode node : node.visibleAnnotations) {
      if (node.desc.equals("Ljava/lang/invoke/MethodHandle$PolymorphicSignature;")) {
        return true;
      }
    }

    return false;
  }

  @Override
  public boolean isReachable() {
    return true;
  }

  @Override
  public int getAccess() {
    return node.access;
  }

  private static class InstVisitor extends MethodVisitor {

    private final ImpactBuilder impacts;
    private final AnalysisSession sess;

    InstVisitor(AnalysisSession analysisSession) {
      super(ASM6);
      this.impacts = analysisSession.createImpactBuilder();
      this.sess = analysisSession;
    }

    @Override
    public void visitTypeInsn(int code, String typeDesc) {
      Type referencedType = TypeHelper.getReferencedType(Type.getObjectType(typeDesc));
      if (referencedType == null) {
        return;
      }

      // ignore other instructions related to non-class types.
      Klass kls = sess.getOrCreateKlass(referencedType);
      if (code == Opcodes.NEW) {
        impacts.createObject(kls);
      }
    }

    @Override
    public void visitLdcInsn(Object value) {
      if (value instanceof String) {
        Klass kls = sess.getOrCreateKlass(String.class);
        impacts.createObject(kls);
      }
    }

    @Override
    public void visitFieldInsn(int code, String owner, String name, String descriptor) {
      switch (code) {
        case Opcodes.PUTSTATIC:
        case Opcodes.GETSTATIC:
          Type ownerType = Type.getObjectType(owner);
          Klass kls = sess.getOrCreateKlass(ownerType);
          impacts.referenceType(kls);
          break;
        case Opcodes.GETFIELD:
        case Opcodes.PUTFIELD:
          // Do not handle instance field accesses, because the field and instance types are initialized during its instantiation.
          break;
      }
    }

    @Override
    public void visitInvokeDynamicInsn(String implementedMethodName, String descriptor,
                                       Handle bootstrapMethodHandle,
                                       Object... bootstrapMethodArguments) {
      Klass bootstrapper = sess.getOrCreateKlass(bootstrapMethodHandle.getOwner());
      impacts.referenceType(bootstrapper);
//      impacts.invokeStatic(bootstrapper, bootstrapMethodHandle.getName(),
//          sess.getOrCreateMethodDescriptor(bootstrapMethodHandle.getDesc()));

      Type generatorDesc = Type.getMethodType(descriptor);
      // RTA does not need parameters
      String parentKlass = generatorDesc.getReturnType().getInternalName();

      for (Object o : bootstrapMethodArguments) {
        if (o instanceof Handle) {
          Handle invokeInfo = (Handle) o;
          Type ownerType = Type.getObjectType(invokeInfo.getOwner());
          if (ownerType.getSort() == Type.ARRAY) {
            return;
          }

          Klass kls = sess.getOrCreateKlass(ownerType);
          MethodDescriptor desc = sess.getOrCreateMethodDescriptor(invokeInfo.getDesc());
          String methodName = invokeInfo.getName();
          ImpactBuilder inner = sess.createImpactBuilder();
          switch (invokeInfo.getTag()) {
            case Opcodes.H_INVOKEINTERFACE:
              inner.invokeInterface(kls, methodName, desc);
              break;
            case Opcodes.H_INVOKESPECIAL:
              inner.invokeSpecial(kls, methodName, desc);
              break;
            case Opcodes.H_INVOKESTATIC:
              inner.invokeStatic(kls, methodName, desc);
              break;
            case Opcodes.H_GETSTATIC:
            case Opcodes.H_PUTSTATIC:
              inner.referenceType(kls);
              break;
            case Opcodes.H_INVOKEVIRTUAL:
              inner.invokeVirtual(kls, methodName, desc);
              break;
            case Opcodes.H_NEWINVOKESPECIAL:
              inner.createObject(kls);
              inner.invokeSpecial(kls, methodName, desc);
              break;
            default:
              throw new AssertionError("Unknown methodhandle: " + invokeInfo);
          }

          Klass created = sess
                  .createLambdaKlass(bootstrapper.getTypeName(), parentKlass, implementedMethodName,
                          kls, methodName, desc, inner.build());
          if (created != null) {
            impacts.createObject(created);
          }
        }
      }
    }

    @Override
    public void visitMethodInsn(int code, String owner, String name, String descriptor,
                                boolean isInterface) {
      Type ownerType = Type.getObjectType(owner);
      MethodDescriptor desc = sess.getOrCreateMethodDescriptor(descriptor);
      if (ownerType.getSort() == Type.ARRAY) {
        ownerType = Type.getType(Object.class);
        Klass kls = sess.getOrCreateKlass(ownerType);
        impacts.invokeSpecial(kls, name, desc);
      } else {
        Klass kls = sess.getOrCreateKlass(ownerType);
        switch (code) {
          case Opcodes.INVOKEINTERFACE:
            impacts.invokeInterface(kls, name, desc);
            break;
          case Opcodes.INVOKESPECIAL:
            impacts.invokeSpecial(kls, name, desc);
            break;
          case Opcodes.INVOKESTATIC:
            impacts.invokeStatic(kls, name, desc);
            break;
          case Opcodes.INVOKEVIRTUAL:
            impacts.invokeVirtual(kls, name, desc);
            break;
          default:
            throw new AssertionError("Unknown method call code: " + code);
        }
      }
    }
  }
}
