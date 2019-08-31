package sample;

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



import com.ibm.wala.ipa.callgraph.AnalysisCacheImpl;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.CallGraphBuilderCancelException;
import com.ibm.wala.ipa.callgraph.Entrypoint;
import com.ibm.wala.ipa.callgraph.IAnalysisCacheView;
import com.ibm.wala.ipa.callgraph.impl.Util;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.ipa.cha.ClassHierarchyFactory;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.util.config.AnalysisScopeReader;
import com.ibm.wala.util.config.FileOfClasses;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

@SuppressWarnings("StringBufferReplaceableByString")
public class Test1 implements X {

  public Test1() {
    Supplier<Integer> z = () -> 5;
    z.get();

    consumer(this::instantceConsumed);
  }

  public static void main(String[] args)
          throws ClassHierarchyException, CallGraphBuilderCancelException, IOException {
    System.out.println("ASDF");
    Runnable y = () -> System.out.println("a");
    Supplier<Integer> z = () -> 5;
    z.get();
    y.run();
    System.out.println(z.get());
    consumer(Test1::consumed);

    Test1 x = new Test1();
    consumer(x::instantceConsumed);
    consumer(Test1::new);
    X g = x;
    g.get();

    ClassLoader loader = ClassLoader.getSystemClassLoader();
    AnalysisScope scope = AnalysisScopeReader.readJavaScope("wala.scope", null, loader);
    scope.setExclusions(new FileOfClasses(new ByteArrayInputStream(null)));

    ClassLoaderReference classLoaderRef = scope.getLoader(AnalysisScope.APPLICATION);
    AnalysisScopeReader
            .addClassPathToScope(ClassLoader.getSystemResource(".").getPath(), scope, classLoaderRef);

    ClassHierarchy cha = ClassHierarchyFactory.make(scope);
    Set<Entrypoint> entries = new HashSet<>();

    AnalysisOptions options = new AnalysisOptions(scope, entries);
    options.setReflectionOptions(AnalysisOptions.ReflectionOptions.NONE);

    IAnalysisCacheView cache = new AnalysisCacheImpl();
    Util.makeRTABuilder(options, cache, cha, scope).makeCallGraph(options, null);
    Util.makeRTABuilder(options, new AnalysisCacheImpl(), cha, scope).makeCallGraph(options, null);
  }

  private static void consumer(Supplier<Test1> z) {
    z.get();
  }

  private static void consumer(Runnable y) {
    y.run();
  }

  private static void consumed() {
    System.out.println();
  }

  private void instantceConsumed() {

  }

  @Override
  public String get() {
    return "ASDF";
  }

  public static class Test2 {

    static final Test1 t = new Test1();
  }
}
