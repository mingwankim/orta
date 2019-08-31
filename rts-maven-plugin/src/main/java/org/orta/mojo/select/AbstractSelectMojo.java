package org.orta.mojo.select;

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

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.collect.Table;
import experiments.commons.artifacts.Artifact;
import experiments.commons.artifacts.ArtifactHandler;
import org.orta.diff.ClassHash;
import org.orta.mojo.BaseMojo;
import org.orta.mojo.Utils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugin.surefire.AbstractSurefireMojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.surefire.util.DefaultScanResult;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

import static experiments.commons.artifacts.Artifact.ClassPaths;

public abstract class AbstractSelectMojo extends BaseMojo {
  @Parameter(property = "oldArtifactsRoot")
  protected File oldArtifactRoot;

  private static Path checkEmptyFile(File x) throws MojoExecutionException {
    if (x == null) {
      throw new MojoExecutionException("excludes should be assigned.");
    }
    Path p = x.toPath();
    try {
      Files.deleteIfExists(p);
      Files.createFile(p);
    } catch (IOException e) {
      throw mojoException(e);
    }

    return p;
  }

  protected abstract ArtifactHandler<Set<String>> getAffectedArtifact();

  protected abstract ArtifactHandler<Table<String, String, Set<String>>> getReachablesArtifact();

  public void execute()
          throws MojoExecutionException {
    Path excludesPath = checkEmptyFile(getExcludesFile());

    Set<String> previousCP;
    if (this.oldArtifactRoot != null) {
      try {
        previousCP = ClassPaths.loadArtifact(oldArtifactRoot);
        if (previousCP == null) throw new MojoExecutionException("previousCP == null");
      } catch (IOException e) {
        throw mojoException(e);
      }
    } else {
      previousCP = ImmutableSet.of();
    }
    ImmutableSet<String> testClasses = getTestClasses();
    ImmutableSet<String> currentCP;
    try {
      currentCP = getAndDumpDependencies();
    } catch (IOException e) {
      throw mojoException(e);
    }
    Log log = getLog();
    Set<String> selected;
    if (!previousCP.equals(currentCP)) {
      log.info("classpaths changed.");
      selected = testClasses;
      try {
        Files.deleteIfExists(getAffectedArtifact().resolvePath(artifactRoot));
      } catch (java.io.IOException e) {
        throw mojoException(e);
      }
    } else {
      try {
        Map<String, ClassHash> curHashes = createAndDumpHashes();
        getLog().info("curHashes != null");
        selected = Utils.computeAffectedTests(getLog(), curHashes, oldArtifactRoot, getReachablesArtifact(), testClasses);
        getAffectedArtifact().acceptArtifact(selected, artifactRoot);
      } catch (IOException e) {
        throw mojoException(e);
      }
    }

    log.info(String.format("Selected test classes: %d/%d", selected.size(), testClasses.size()));
    try {
      getSelectedArtifact().acceptArtifact(selected, artifactRoot);
      Artifact.dumpSet(Sets.difference(testClasses, selected), excludesPath, Artifact::isNotClassName);
      Artifact.dumpSet(testClasses, Artifact.TestClasses.resolvePath(artifactRoot), Artifact::isNotClassName);
    } catch (IOException e) {
      throw mojoException(e);
    }
  }

  protected abstract ArtifactHandler<Set<String>> getSelectedArtifact();

  private ImmutableSet<String> getTestClasses() throws MojoExecutionException {
    getLog().info("getTestClasses()");

    try {
      Method scanMethod = AbstractSurefireMojo.class.getDeclaredMethod("scanForTestClasses");
      scanMethod.setAccessible(true);
      DefaultScanResult defaultScanResult = (DefaultScanResult) scanMethod.invoke(this);
      ImmutableSet<String> testClasses = ImmutableSet.copyOf(defaultScanResult.getClasses());
      ;
      Artifact.TestClasses.acceptArtifact(testClasses, artifactRoot);
      return testClasses;
    } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException | IOException e) {
      throw mojoException(e);
    }
  }
}
