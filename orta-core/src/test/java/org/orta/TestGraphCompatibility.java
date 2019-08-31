package org.orta;

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



import com.google.common.collect.Sets;
import com.google.common.graph.EndpointPair;
import org.orta.core.cg.CallGraph;
import org.orta.core.cg.ORTACallGraphBuilder;
import org.orta.core.cg.rta.RTA;
import org.orta.core.type.AnalysisSession;
import org.orta.core.type.TypeHelper;
import org.orta.core.type.klass.Klass;
import org.orta.core.type.klass.KlassMethod;
import org.junit.jupiter.api.Test;
import sample.Test1;
import sample.Test1.Test2;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.StringJoiner;

import static org.junit.jupiter.api.Assertions.fail;

class TestGraphCompatibility {

  private static final String EXCLUSIONS =
          "java\\/awt\\/.*\n" + "javax\\/swing\\/.*\n" + "sun\\/awt\\/.*\n"
                  + "sun\\/swing\\/.*\n" + "com\\/sun\\/.*\n" + "sun\\/.*\n" + "org\\/netbeans\\/.*\n"
                  + "org\\/openide\\/.*\n" + "com\\/ibm\\/crypto\\/.*\n" + "com\\/ibm\\/security\\/.*\n"
                  + "org\\/apache\\/xerces\\/.*\n" + "java\\/security\\/.*\n" + "";
  private final AnalysisSession session = new AnalysisSetting()
          .addClassPath(ClassLoader.getSystemResource("."))
          .excludePackages(EXCLUSIONS.split("\n"))
          .build();

  TestGraphCompatibility() throws IOException, URISyntaxException {
  }

  private void check(Set<EndpointPair<KlassMethod>> a, Set<EndpointPair<KlassMethod>> b,
                     String label) {
    Set<EndpointPair<KlassMethod>> diff = Sets.difference(a, b).immutableCopy();
    StringJoiner joiner = new StringJoiner("\n");
    if (!diff.isEmpty()) {
      for (EndpointPair<KlassMethod> edge : diff) {
        if (edge.target().equals(session.getFakeCaller()) || edge.source()
                .equals(session.getFakeCaller())) {
          continue;
        }
        joiner.add(edge.source() + "->" + edge.target());
      }

      if (joiner.length() != 0) {
        fail(label + "\n" + joiner.toString());
      }
    }
  }

  @Test
  void testGraphCompatibity() {
    RTA rta = RTA.get();

    Set<Klass> klasses = new HashSet<>();
    klasses.add(session.getOrCreateKlass(Test1.class));
    klasses.add(session.getOrCreateKlass(Test2.class));
    Map<String, CallGraph> cg = ORTACallGraphBuilder.build(session, klasses);
    for (Entry<String, CallGraph> entry : cg.entrySet()) {
      Klass kls = session.getOrCreateKlass(entry.getKey().replace(".", "/"));
      CallGraph rtaGraph = rta.createCallGraph(session, Collections.singleton(kls),
              TypeHelper.resolveInvocableMethods(kls));
      CallGraph ortaGraph = entry.getValue();
      check(ortaGraph.edges(), rtaGraph.edges(), entry.getKey() + ": ORTA");
      check(rtaGraph.edges(), ortaGraph.edges(), entry.getKey() + ": RTA");
    }
  }
}
