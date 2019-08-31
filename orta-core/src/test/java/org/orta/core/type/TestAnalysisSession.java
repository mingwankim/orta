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



import org.orta.AnalysisSetting;
import org.orta.core.type.klass.Klass;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Type;

import java.io.IOException;
import java.net.URISyntaxException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestAnalysisSession {

  private AnalysisSession session;

  @BeforeEach
  void initialize() throws URISyntaxException, IOException {
    session = new AnalysisSetting().build();
  }

  @Test
  void testPrimitives() {
    for (Type t : new Type[]{Type.CHAR_TYPE, Type.BOOLEAN_TYPE, Type.BYTE_TYPE, Type.DOUBLE_TYPE,
            Type.FLOAT_TYPE, Type.INT_TYPE, Type.LONG_TYPE, Type.SHORT_TYPE, Type.VOID_TYPE}) {
      assertEquals(session.getPrimitiveType(t), session.primTypes.get(t.getInternalName()),
              t.toString());
    }
  }

  @Test
  void testKlasses() {
    Klass kls = session.getOrCreateKlass(Object.class);
    assertEquals("java.lang.Object", kls.getTypeName());
    assertEquals("java/lang/Object", kls.getInternalName());
    assertEquals("Ljava/lang/Object;", kls.getTypeDescriptor());
    assertEquals("java.lang", kls.getPackageName());
  }

  @Test
  void testDerivedTypes() {
    Class<?> cls = Object[][].class;
    assertEquals("java.lang.Object[][]",
            new ArrayType(session.getOrCreateKlass(Object.class), 2).toString());
    assertEquals("java.lang.Object[][]",
            session.getOrCreateArrayType(Type.getType(cls)).toString());

    cls = long[][].class;
    assertEquals("Long[][]", session.getOrCreateArrayType(Type.getType(cls)).toString());
  }
}
