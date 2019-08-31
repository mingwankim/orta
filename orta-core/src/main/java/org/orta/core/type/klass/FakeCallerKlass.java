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



import com.google.common.collect.ImmutableSet;
import org.orta.core.cg.impacts.ImpactFactory;
import org.orta.core.cg.impacts.ImpactUnit;
import org.orta.core.type.AnalysisSession;
import org.orta.core.type.locator.ClassSource;
import org.orta.core.type.locator.FieldSource;
import org.orta.core.type.locator.MethodSource;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.objectweb.asm.Opcodes;

import java.util.function.Function;
import java.util.stream.Stream;

public class FakeCallerKlass extends Klass {

  public static final String KLASSNAME = "FakeKlass";
  public static final String METHODNAME = "FakeCaller";
  public static final String DESC = "(LFakeKlass;)V";
  public static final String SIGNATURE = KLASSNAME + "." + METHODNAME + DESC;

  public FakeCallerKlass(
          @NonNull AnalysisSession tm) {
    super(tm, new ClassSource() {
      @Override
      public @NonNull String getInternalName() {
        return KLASSNAME;
      }

      @Override
      public @Nullable String getSuperClass() {
        return null;
      }

      @Override
      public @NonNull Stream<String> streamInterfaces() {
        return Stream.empty();
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
            return DESC;
          }

          @Override
          public Function<AnalysisSession, ImmutableSet<ImpactUnit>> getImpacts() {
            return (sess) -> {
              ImpactFactory.ImpactBuilder builder = sess.createImpactBuilder();
              Klass obj = sess.getOrCreateKlass(Object.class);
              builder
                      .invokeVirtual(obj, "toString",
                              sess.getOrCreateMethodDescriptor("()Ljava/lang/String;"))
                      .invokeVirtual(obj, "hashCode", sess.getOrCreateMethodDescriptor("()I"))
                      .invokeVirtual(obj, "equals",
                              sess.getOrCreateMethodDescriptor("(Ljava/lang/Object;)Z"))
                      .invokeVirtual(obj, "finalize", sess.getOrCreateMethodDescriptor("()V"))
                      .invokeVirtual(obj, "clone",
                              sess.getOrCreateMethodDescriptor("()Ljava/lang/Object;"));

              for (Klass essentialKlass : sess.getEssentialKlasses()) {
                builder.invokeDefaultConstructor(essentialKlass);
              }

              return builder.build();
            };
          }

          @Override
          public String getMethodName() {
            return METHODNAME;
          }

          @Override
          public boolean isPolymorphicSignature() {
            return false;
          }

          @Override
          public boolean isReachable() {
            return false;
          }

          @Override
          public int getAccess() {
            return Opcodes.ACC_STATIC | Opcodes.ACC_PUBLIC;
          }
        });
      }

      @Override
      public @NonNull Stream<FieldSource> streamFields() {
        return Stream.empty();
      }

      @Override
      public boolean isReachable() {
        return false;
      }

      @Override
      public boolean isConcrete() {
        return false;
      }
    });
  }
}
