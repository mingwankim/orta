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
import org.orta.Utils;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class TestClassLocator {

  private final ClassSourceLocator locator = prepareLocator();

  TestClassLocator() throws IOException, URISyntaxException {
  }

  private ClassSourceLocator prepareLocator() throws IOException, URISyntaxException {
    URL resourcePath = getClass().getClassLoader().getResource(".");
    assertNotNull(resourcePath);

    Path path = Paths.get(resourcePath.toURI());
    return new ClassSourceLocator(ImmutableSet.of(path), ImmutableSet.of(), false);
  }

  private void testClassSource(String name, String superName, int access, String... interfaces)
          throws IOException {
    ClassSource src = locator.lookupSource(name);
    assertNotNull(src);
    assertEquals(name, src.getInternalName());
    assertEquals(superName, src.getSuperClass());
    Utils.assertStreamEquals(ImmutableSet.copyOf(interfaces), src.streamInterfaces());
    assertEquals(access, src.computeAccess(), "Unexpected access flag");
  }

  @Test
  void testDirectoryLocator() throws IOException {
    String outerClassName = Utils.getClassName(TestClassLocator.class);
    testClassSource(outerClassName, "java/lang/Object", Opcodes.ACC_SUPER);

    String innerClassName = Utils.getClassName(tester.class);
    testClassSource(innerClassName, "java/lang/Object",
            Opcodes.ACC_SUPER | Opcodes.ACC_STATIC | Opcodes.ACC_PRIVATE,
            Utils.getClassName(Runnable.class));

    @SuppressWarnings("Convert2Lambda")
    Runnable x = new Runnable() {
      @Override
      public void run() {

      }
    };

    String anonymousName = Utils.getClassName(x.getClass());
    testClassSource(anonymousName, "java/lang/Object", Opcodes.ACC_SUPER,
            Utils.getClassName(Runnable.class));
  }

  private static class tester implements Runnable {

    @Override
    public void run() {

    }
  }
}
