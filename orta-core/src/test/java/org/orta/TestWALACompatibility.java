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



import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.SetMultimap;
import com.google.common.graph.EndpointPair;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.AnalysisCacheImpl;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraphBuilderCancelException;
import com.ibm.wala.ipa.callgraph.Entrypoint;
import com.ibm.wala.ipa.callgraph.IAnalysisCacheView;
import com.ibm.wala.ipa.callgraph.impl.DefaultEntrypoint;
import com.ibm.wala.ipa.callgraph.impl.Util;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.ipa.cha.ClassHierarchyFactory;
import com.ibm.wala.ipa.summaries.SummarizedMethod;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.config.AnalysisScopeReader;
import com.ibm.wala.util.config.FileOfClasses;
import com.ibm.wala.util.graph.traverse.DFS;
import org.orta.core.cg.rta.RTA;
import org.orta.core.type.AnalysisSession;
import org.orta.core.type.TypeHelper;
import org.orta.core.type.klass.Klass;
import org.orta.core.type.klass.KlassMethod;
import org.junit.jupiter.api.Test;
import org.orta.core.cg.CallGraph;
import sample.Test1;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.fail;

class TestWALACompatibility {

  private static final String EXCLUSIONS = "";
  //          private static final String EXCLUSIONS =
//      "java\\/awt\\/.*\n" + "javax\\/swing\\/.*\n" + "sun\\/awt\\/.*\n"
//          + "sun\\/swing\\/.*\n" + "com\\/sun\\/.*\n" + "sun\\/.*\n" + "org\\/netbeans\\/.*\n"
//          + "org\\/openide\\/.*\n" + "com\\/ibm\\/crypto\\/.*\n" + "com\\/ibm\\/security\\/.*\n"
//          + "org\\/apache\\/xerces\\/.*\n" + "java\\/security\\/.*\n" + "";
  private final AnalysisSession session = new AnalysisSetting()
          .addClassPath(ClassLoader.getSystemResource("."))
          .excludePackages(EXCLUSIONS.split("\n"))
          .build();

  public TestWALACompatibility() throws IOException, URISyntaxException {
  }

  private static boolean isCommonNode(CGNode node) {
    if (node.toString().contains("FakeRoot")) {
      return false;
    }
    IMethod m = node.getMethod();
    String sig = node.getMethod().getSignature();
    if (m instanceof SummarizedMethod) {
      if (sig.contains("LambdaMetafactory")) {
        return false;
      }
    }

    return !sig.startsWith("wala.lambda$");
  }

  private static boolean isCommonNode(KlassMethod x) {
    String s = x.getSignature();
    return !s.equals("FakeKlass.FakeCaller(LFakeKlass;)V") && !s.startsWith("rtscg.");
  }

  private com.ibm.wala.ipa.callgraph.CallGraph makeWALAGraph(Class<?> cls)
          throws ClassHierarchyException, IOException,
          IllegalArgumentException, CallGraphBuilderCancelException {
    ClassLoader loader = ClassLoader.getSystemClassLoader();
    AnalysisScope scope = AnalysisScopeReader.readJavaScope("wala.scope", null, loader);
    scope.setExclusions(new FileOfClasses(new ByteArrayInputStream(EXCLUSIONS.getBytes(
            StandardCharsets.UTF_8))));

    ClassLoaderReference classLoaderRef = scope.getLoader(AnalysisScope.APPLICATION);
    AnalysisScopeReader
            .addClassPathToScope(ClassLoader.getSystemResource(".").getPath(), scope, classLoaderRef);

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

  private CallGraph makeGraph(Class<?> cls) {
    Klass kls = session.getOrCreateKlass(cls);
    return RTA.get().createCallGraph(session, Collections.singleton(kls),
            TypeHelper.resolveInvocableMethods(kls));
  }

  @Test
  void testWALACompatibility() throws IOException, ClassHierarchyException,
          IllegalArgumentException, CallGraphBuilderCancelException {
    CallGraph ourGraph = makeGraph(Test1.class);
    com.ibm.wala.ipa.callgraph.CallGraph walaGraph = makeWALAGraph(Test1.class);

    Set<CGNode> walaNodes = DFS.getReachableNodes(walaGraph);
    SetMultimap<String, String> edges = MultimapBuilder.hashKeys().hashSetValues().build();
    for (EndpointPair<KlassMethod> edge : ourGraph.edges()) {
      if (isCommonNode(edge.nodeU()) && isCommonNode(edge.nodeV())) {
        edges.put(edge.source().getSignature(), edge.target().getSignature());
      }
    }

    SetMultimap<String, String> walaEdges = MultimapBuilder.hashKeys().hashSetValues().build();
    SetMultimap<String, String> visited = MultimapBuilder.hashKeys().hashSetValues().build();
    for (CGNode node : walaNodes) {
      if (!isCommonNode(node)) {
        continue;
      }

      String sig = node.getMethod().getSignature();
      Iterator<CGNode> callers = walaGraph.getPredNodes(node);
      while (callers.hasNext()) {
        CGNode caller = callers.next();
        if (!isCommonNode(caller)) {
          continue;
        }

        String callerSig = caller.getMethod().getSignature();
        if (visited.put(callerSig, sig) && !edges.remove(callerSig, sig)) {
          walaEdges.put(callerSig, sig);
        }
      }

      Iterator<CGNode> callees = walaGraph.getSuccNodes(node);
      while (callees.hasNext()) {
        CGNode callee = callees.next();
        if (!isCommonNode(callee)) {
          continue;
        }

        String calleeSig = callee.getMethod().getSignature();
        if (visited.put(sig, calleeSig) && !edges.remove(sig, calleeSig)) {
          walaEdges.put(sig, calleeSig);
        }
      }
    }

    // We prefer the original code of this method to its xml specification.
    edges.removeAll("sun.misc.Unsafe.getUnsafe()Lsun/misc/Unsafe;");
    walaEdges.removeAll("sun.misc.Unsafe.getUnsafe()Lsun/misc/Unsafe;");
    if (!walaEdges.isEmpty() || !edges.isEmpty()) {
      fail();
    }
  }
}
