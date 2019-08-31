package org.orta.mojo;

/*-
 * #%L
 * rts-maven-plugin
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



/*
 * Copyright 2001-2005 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import com.google.common.graph.EndpointPair;
import com.google.common.graph.Graph;
import experiments.commons.MavenConstants;
import experiments.commons.artifacts.Artifact;
import org.orta.AnalysisSetting;
import org.orta.core.cg.CallGraph;
import org.orta.core.cg.ORTACallGraphBuilder;
import org.orta.core.cg.rta.RTA;
import org.orta.core.cg.rta.RTAEdgeCounter;
import org.orta.core.type.AnalysisSession;
import org.orta.core.type.TypeHelper;
import org.orta.core.type.klass.Klass;
import org.orta.core.type.klass.KlassMethod;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Mojo(defaultPhase = LifecyclePhase.TEST_COMPILE, name = MavenConstants.EDGE_COUNT, requiresDependencyResolution = ResolutionScope.TEST)
public class EdgeCounterMojo extends BaseMojo {
  private static Klass loadKlass(AnalysisSession sess, String className) {
    return sess.getOrCreateKlass(className.replace(".", "/"));
  }

  public void execute() throws MojoExecutionException {
    AnalysisSession sess;
    try {
      sess = new AnalysisSetting()
              .addClassPath(getTestClassesDirectory().toPath())
              .addClassPath(getClassesDirectory().toPath())
              .excludeMemoryLoader()
              .build();
    } catch (IOException e) {
      throw mojoException(e);
    }

    try {
      Set<String> affected = Artifact.SeparateArtifact.AffectedTests.loadArtifact(artifactRoot);
      if (affected == null) {
        throw new MojoExecutionException("Could not found affected tests");
      }

      Set<Klass> klasses = new HashSet<>();
      for (String name : affected) {
        klasses.add(loadKlass(sess, name));
      }

      Set<EndpointPair<KlassMethod>> visited = new HashSet<>();
      int rtaDups = 0;
      int rtaEdgeCount = 0;
      for (Klass klass : klasses) {
        CallGraph rta = RTA.get().createCallGraph(sess, Collections.singleton(klass), TypeHelper.resolveInvocableMethods(klass));
        for (EndpointPair<KlassMethod> edge : rta.edges()) {
          if (!TypeHelper.isToolSpecificEdge(edge, sess)) {
            rtaEdgeCount += 1;
            if (!visited.add(edge)) {
              rtaDups += 1;
            }
          }
        }
      }

      Map<String, CallGraph> orta = ORTACallGraphBuilder.build(sess, klasses);
      visited.clear();
      int ortaDups = 0;
      int ortaEdgeCount = 0;
      for (Graph<KlassMethod> cg : RTAEdgeCounter.iterate(orta.values())) {
        for (EndpointPair<KlassMethod> edge : cg.edges()) {
          if (!TypeHelper.isToolSpecificEdge(edge, sess)) {
            ortaEdgeCount += 1;
            if (!visited.add(edge)) {
              ortaDups += 1;
            }
          }
        }
      }
      Artifact.SeparateArtifact.Edges.acceptArtifact(new int[]{rtaEdgeCount, rtaDups, ortaEdgeCount, ortaDups}, artifactRoot);
    } catch (Exception e) {
      throw mojoException(e);
    } finally {
      sess.close();
    }
  }
}
