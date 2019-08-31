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

import com.google.common.collect.ImmutableSet;
import experiments.commons.artifacts.Artifact;
import org.orta.diff.ClassHash;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.surefire.SurefirePlugin;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

public abstract class BaseMojo extends SurefirePlugin {
  @Parameter(property = "artifactsRoot")
  protected File artifactRoot;

  protected static MojoExecutionException mojoException(Throwable e) {
    StringWriter sw = new StringWriter();
    PrintWriter pw = new PrintWriter(sw);
    e.printStackTrace(pw);
    return new MojoExecutionException(sw.toString());
  }

  private static void appendClasses(File cp, Map<String, ClassHash> classFiles) throws IOException {
    try (Stream<Path> s = Files.walk(cp.toPath())) {
      Iterator<Path> iterator = s.iterator();
      while (iterator.hasNext()) {
        Path item = iterator.next();
        if (item.getFileName().toString().endsWith(".class")) {
          ClassHash hash = new ClassHash(item);
          if (hash.name.contains(".HikariProxy")) {
            continue;
          }

          if (hash.name.endsWith(".hikari.proxy.ProxyFactory")) {
            continue;
          }

          classFiles.put(hash.name, hash);
        }
      }
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }
  }

  protected ImmutableSet<String> getAndDumpDependencies() throws IOException {
    Set artifacts = getProject().getArtifacts();
    ImmutableSet.Builder<String> builder = ImmutableSet.builderWithExpectedSize(artifacts.size());
    for (Object o : artifacts) {
      org.apache.maven.artifact.Artifact a = (org.apache.maven.artifact.Artifact) o;
      if (a.getArtifactHandler().isAddedToClasspath()) {
//                ids.add(a.getId());
        builder.add(a.getFile().toString());
      }
    }

    ImmutableSet<String> result = builder.build();
    getLog().info(artifactRoot.toString());
    Artifact.ClassPaths.acceptArtifact(result, artifactRoot);
    return result;
  }

  protected Map<String, ClassHash> createAndDumpHashes() throws IOException {
    Map<String, ClassHash> hashes = new HashMap<>();
    appendClasses(getTestClassesDirectory(), hashes);
    appendClasses(getClassesDirectory(), hashes);
    Artifact.Hashes.acceptArtifact(hashes, artifactRoot);
    return hashes;
  }
}
