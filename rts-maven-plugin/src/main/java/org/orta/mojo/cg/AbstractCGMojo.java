package org.orta.mojo.cg;

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

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Sets;
import com.google.common.collect.Table;
import experiments.commons.artifacts.Artifact;
import experiments.commons.artifacts.ArtifactHandler;
import org.orta.AnalysisSetting;
import org.orta.core.type.AnalysisSession;
import org.orta.core.type.klass.Klass;
import org.orta.core.type.klass.KlassMethod;
import org.orta.diff.ClassHash;
import org.orta.mojo.BaseMojo;
import org.orta.mojo.Utils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Parameter;
import org.checkerframework.checker.nullness.qual.NonNull;

import java.io.File;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

abstract class AbstractCGMojo extends BaseMojo {
  @Parameter(property = "oldArtifactsRoot")
  protected File oldArtifactRoot;

  protected @NonNull Klass loadKlass(AnalysisSession sess, String name) {
    return sess.getOrCreateKlass(name.replace(".", "/"));
  }

  protected abstract ArtifactHandler<Set<String>> getAffectedArtifact();

  protected abstract ArtifactHandler<Table<String, String, Set<String>>> getReachablesArtifact();

  public void execute() throws MojoExecutionException {
    try (AnalysisSession sess = new AnalysisSetting()
            .addClassPath(getTestClassesDirectory().toPath())
            .addClassPath(getClassesDirectory().toPath())
            .excludeMemoryLoader()
            .build()) {
      Set<String> affected = getAffectedArtifact().loadArtifact(artifactRoot);
      Set<String> allTestClasses = Artifact.TestClasses.loadArtifact(artifactRoot);   //Should not use any methods from SurefirePlugin due to includesFile and excludesFile.
      if (allTestClasses == null) {
        throw new MojoExecutionException("Could not found all test classes.");
      }

      if (affected == null) {
        Map<String, ClassHash> hashes = createAndDumpHashes();
        affected = Utils.computeAffectedTests(getLog(), hashes, oldArtifactRoot, getReachablesArtifact(), allTestClasses);
        getAffectedArtifact().acceptArtifact(affected, artifactRoot);
      }

      Table<String, String, Set<String>> reachables;
      if (oldArtifactRoot != null) {
        reachables = getReachablesArtifact().loadArtifact(oldArtifactRoot);
      } else {
        reachables = HashBasedTable.create();
      }

      for (String name : Sets.union(Sets.difference(reachables.rowKeySet(), allTestClasses), affected).immutableCopy()) {
        reachables.rowMap().remove(name);
      }

      for (String testName : affected) {
        Iterator<KlassMethod> iter = findReachables(sess, testName, affected);
        while (iter.hasNext()) {
          KlassMethod reachable = iter.next();
          if (reachable.getDeclaringClass().getPackageName().startsWith("rtscg.") || reachable.equals(sess.getFakeCaller())) {
            continue;
          }

          String className = reachable.getDeclaringClass().toString();
          Set<String> selectors = reachables.get(testName, className);
          if (selectors == null) {
            selectors = new HashSet<>();
            reachables.put(testName, className, selectors);
          }

          selectors.add(reachable.getSelector());
        }
      }

      getReachablesArtifact().acceptArtifact(reachables, artifactRoot);
      getLog().info("Construction Time: " + getTotalTime() / 1000.0);
    } catch (Exception e) {
      getReachablesArtifact().deleteIfExists(artifactRoot);
      throw mojoException(e);
    }
  }

  protected abstract long getTotalTime();

  abstract Iterator<KlassMethod> findReachables(AnalysisSession sess, String className, Set<String> affected);
}
