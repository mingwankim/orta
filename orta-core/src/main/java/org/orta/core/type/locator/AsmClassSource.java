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
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InnerClassNode;

import java.io.IOException;
import java.io.InputStream;
import java.util.stream.Stream;

class AsmClassSource implements ClassSource {

  private final ClassNode classNode;

  AsmClassSource(InputStream srcStream) throws IOException {
    classNode = new ClassNode();
    ClassReader reader = new ClassReader(srcStream);
    reader.accept(classNode, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
  }

  @Override
  public @NonNull String getInternalName() {
    return classNode.name;
  }

  @Override
  public @Nullable String getSuperClass() {
    return classNode.superName;
  }

  @Override
  public @NonNull Stream<String> streamInterfaces() {
    return classNode.interfaces.stream();
  }

  @Override
  public int computeAccess() {
    for (InnerClassNode node : classNode.innerClasses) {
      if (node.name.equals(classNode.name)) {
        return node.access | classNode.access;
      }
    }

    return classNode.access;
  }

  @Override
  public @NonNull Stream<MethodSource> streamMethods() {
    return classNode.methods.stream().map(AsmMethodSource::new);
  }

  @Override
  public @NonNull Stream<FieldSource> streamFields() {
    // TODO: need to?
    return Stream.empty();
  }

  @Override
  public boolean isReachable() {
    return true;
  }

  @Override
  public boolean isConcrete() {
    return true;
  }
}
