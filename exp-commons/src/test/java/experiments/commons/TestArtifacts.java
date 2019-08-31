package experiments.commons;

/*-
 * #%L
 * exp-commons
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


import com.google.common.collect.ImmutableSet;
import experiments.commons.artifacts.Artifact;
import experiments.commons.artifacts.ArtifactHandler;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;

public class TestArtifacts {

  private <T> void test(String name, ArtifactHandler<T> artifact, T content) throws URISyntaxException, IOException {
    Path resourceRoot = Paths.get(TestArtifacts.class.getResource("/").toURI());
    Path notExists = Paths.get(TestArtifacts.class.getResource("/notexists").toURI());
    Assertions.assertFalse(artifact.isReady(notExists), name + ": isReady()");
    Assertions.assertNull(artifact.loadArtifact(notExists), name + ": isReady()");
    Assertions.assertTrue(artifact.isReady(resourceRoot), name + ": isReady()");
    Assertions.assertEquals(content, artifact.loadArtifact(resourceRoot.toFile()), name + ": loadArtifact(File)");
    Assertions.assertEquals(content, artifact.loadArtifact(resourceRoot), name + ": loadArtifact(Path)");
  }

  @Test
  public void testArtifacts() throws IOException, URISyntaxException {
    test("classpaths", Artifact.ClassPaths, ImmutableSet.of("src", "pom.xml"));
    test("hyRTS", Artifact.HyRTS, ImmutableSet.of("hyRTS.A", "hyRTS.B"));
    test("testClasses", Artifact.TestClasses, ImmutableSet.of("Test.A", "Test.B"));
    test("singleAffectedTests", Artifact.SingleArtifact.AffectedTests, ImmutableSet.of("single.Affected1", "single.Affected2"));
    test("singleExcludedTests", Artifact.SingleArtifact.ExcludedTests, ImmutableSet.of("single.Excluded1", "single.Excluded2"));
    test("singleSelectedTests", Artifact.SingleArtifact.SelectedTests, ImmutableSet.of("single.Selected1", "single.Selected2"));
    test("separateAffectedTests", Artifact.SeparateArtifact.AffectedTests, ImmutableSet.of("sep.Affected1", "sep.Affected2"));
    test("separateExcludedTests", Artifact.SeparateArtifact.ExcludedTests, ImmutableSet.of("sep.Excluded1", "sep.Excluded2"));
    test("separateSelectedTests", Artifact.SeparateArtifact.SelectedTests, ImmutableSet.of("sep.Selected1", "sep.Selected2"));
  }
}
