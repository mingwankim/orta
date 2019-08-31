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



import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.AnalysisCacheImpl;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.CallGraphBuilderCancelException;
import com.ibm.wala.ipa.callgraph.Entrypoint;
import com.ibm.wala.ipa.callgraph.IAnalysisCacheView;
import com.ibm.wala.ipa.callgraph.impl.DefaultEntrypoint;
import com.ibm.wala.ipa.callgraph.impl.Util;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.ipa.cha.ClassHierarchyFactory;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.config.AnalysisScopeReader;
import com.ibm.wala.util.config.FileOfClasses;
import org.orta.core.cg.rta.RTA;
import org.orta.core.type.AnalysisSession;
import org.orta.core.type.TypeHelper;
import org.orta.core.type.klass.Klass;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.orta.core.cg.CallGraph;
import sample.Test1;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

class TestTime {

  //  private static final String EXCLUSIONS = "";
  private static final String EXCLUSIONS =
          "java\\/awt\\/.*\n" + "javax\\/swing\\/.*\n" + "sun\\/awt\\/.*\n"
                  + "sun\\/swing\\/.*\n" + "com\\/sun\\/.*\n" + "sun\\/.*\n" + "org\\/netbeans\\/.*\n"
                  + "org\\/openide\\/.*\n" + "com\\/ibm\\/crypto\\/.*\n" + "com\\/ibm\\/security\\/.*\n"
                  + "org\\/apache\\/xerces\\/.*\n" + "java\\/security\\/.*\n" + "";
  private final AnalysisSession session = make();

  TestTime() throws IOException, URISyntaxException {
  }

  private static AnalysisSession make() throws URISyntaxException, IOException {
    URLClassLoader loader = (URLClassLoader) ClassLoader.getSystemClassLoader();
    AnalysisSetting setting = new AnalysisSetting()
            .addClassPath(ClassLoader.getSystemResource("."))
            .excludePackages(EXCLUSIONS.split("\n"));

    for (URL url : loader.getURLs()) {
      Path p = Paths.get(url.toURI()).toAbsolutePath();
      setting.addClassPath(p);
    }

    return setting.build();
  }

  private CallGraph makeOurGraph(Class<?> cls) {
    Klass kls = session.getOrCreateKlass(cls);
    return RTA.get().createCallGraph(session, Collections.singleton(kls),
            TypeHelper.resolveInvocableMethods(kls));
  }

  private com.ibm.wala.ipa.callgraph.CallGraph makeWALAGraph(Class<?> cls)
          throws ClassHierarchyException, IOException,
          IllegalArgumentException, CallGraphBuilderCancelException, URISyntaxException {
    URLClassLoader loader = (URLClassLoader) ClassLoader.getSystemClassLoader();
    AnalysisScope scope = AnalysisScopeReader.readJavaScope("wala.scope", null, loader);
    scope.setExclusions(new FileOfClasses(new ByteArrayInputStream(EXCLUSIONS.getBytes(
            StandardCharsets.UTF_8))));

    ClassLoaderReference classLoaderRef = scope.getLoader(AnalysisScope.APPLICATION);
    AnalysisScopeReader
            .addClassPathToScope(Paths.get(ClassLoader.getSystemResource(".").toURI()).toAbsolutePath().toString(), scope, classLoaderRef);

    for (URL url : loader.getURLs()) {
      Path p = Paths.get(url.toURI()).toAbsolutePath();
      AnalysisScopeReader.addClassPathToScope(p.toString(), scope, classLoaderRef);
    }

    ClassHierarchy cha = ClassHierarchyFactory.make(scope);
    TypeReference kls = TypeReference
            .findOrCreateClass(classLoaderRef, cls.getPackage().getName(), cls.getSimpleName());
    IClass walaCls = cha.lookupClass(kls);
    Set<Entrypoint> entries = new HashSet<>();
    for (IMethod method : walaCls.getAllMethods()) {
      entries.add(new DefaultEntrypoint(method, cha));
    }

    AnalysisOptions options = new AnalysisOptions(scope, entries);
    options.setReflectionOptions(AnalysisOptions.ReflectionOptions.NONE);

    IAnalysisCacheView cache = new AnalysisCacheImpl();
    Util.makeRTABuilder(options, cache, cha, scope).makeCallGraph(options, null);
    return Util.makeRTABuilder(options, new AnalysisCacheImpl(), cha, scope)
            .makeCallGraph(options, null);
  }

  @Test
  void testTimeLimit() throws IOException, ClassHierarchyException,
          IllegalArgumentException, CallGraphBuilderCancelException, URISyntaxException {
    Logger.getLogger("").setLevel(Level.OFF);
    makeOurGraph(Test1.class);
    long t = System.currentTimeMillis();
    makeOurGraph(Test1.class);
    long ours = System.currentTimeMillis() - t;
    makeWALAGraph(Test1.class);
    t = System.currentTimeMillis();
    makeWALAGraph(Test1.class);
    Assertions.assertTrue(ours <= System.currentTimeMillis() - t);
  }
}
