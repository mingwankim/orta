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



import org.orta.core.type.AnalysisSession;
import org.orta.core.type.locator.ClassSourceLocator;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class AnalysisSetting {

  private final Set<Path> classPathStream = new HashSet<>();
  private final Set<String> excludedPackages = new HashSet<>();
  private boolean excludeJVM = false;

  public AnalysisSetting addClassPath(URL url) throws URISyntaxException {
    Path path = Paths.get(url.toURI()).normalize();
    classPathStream.add(path);

    return this;
  }

  private AnalysisSetting addClassPath(URL... urls) throws URISyntaxException {
    for (URL classPath : urls) {
      Path path = Paths.get(classPath.toURI()).normalize();
      classPathStream.add(path);
    }

    return this;
  }

  private AnalysisSetting addClassPath(String... classPaths) {
    for (String classPath : classPaths) {
      Path path = Paths.get(classPath).normalize();
      this.classPathStream.add(path);
    }

    return this;
  }

  public AnalysisSetting addClassPath(String classPaths) {
    return addClassPath(classPaths.split(File.pathSeparator));
  }

  public AnalysisSession build() throws IOException {
    ClassSourceLocator locator = new ClassSourceLocator(classPathStream, excludedPackages,
            excludeJVM);
    return new AnalysisSession(locator);
  }

  public AnalysisSetting excludePackages(String[] packageNames) {
    excludedPackages.addAll(Arrays.asList(packageNames));
    return this;
  }

  public AnalysisSetting addClassPath(Path p) {
    classPathStream.add(p);
    return this;
  }

  public AnalysisSetting addClassPath(Iterable<Path> classPaths) {
    classPaths.forEach(classPathStream::add);
    return this;
  }

  public AnalysisSetting addPaths(Set<String> classPaths) {
    classPaths.stream().map(Paths::get).forEach(classPathStream::add);
    return this;
  }

  public AnalysisSetting excludeMemoryLoader() {
    excludeJVM = true;
    return this;
  }
}
