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
import com.google.common.collect.ImmutableSet
import experiments.commons.artifacts.Artifact

def artifactRoot = basedir.toPath()

assert Artifact.Hashes.isReady(artifactRoot)
def hashes = Artifact.Hashes.loadArtifact(artifactRoot)
assert hashes.keySet() == ["rts.sample.A", "rts.sample.B", "rts.sample.C", "rts.sample.I", "rts.sample.TestA", "rts.sample.TestB", "rts.sample.TestC", "rts.sample.TestI"] as Set

assert Artifact.ClassPaths.isReady(artifactRoot)

assert Artifact.TestClasses.isReady(artifactRoot)
assert Artifact.TestClasses.loadArtifact(artifactRoot) == ["rts.sample.TestA", "rts.sample.TestB", "rts.sample.TestC", "rts.sample.TestI"] as Set

assert Artifact.SingleArtifact.AffectedTests.isReady(artifactRoot)
assert Artifact.SingleArtifact.AffectedTests.loadArtifact(artifactRoot) == ["rts.sample.TestA", "rts.sample.TestB", "rts.sample.TestC", "rts.sample.TestI"] as Set

assert Artifact.SingleArtifact.SelectedTests.isReady(artifactRoot)
assert Artifact.SingleArtifact.SelectedTests.loadArtifact(artifactRoot) == ["rts.sample.TestA", "rts.sample.TestB", "rts.sample.TestC", "rts.sample.TestI"] as Set

assert Artifact.SingleArtifact.ExcludedTests.isReady(artifactRoot)
assert Artifact.SingleArtifact.ExcludedTests.loadArtifact(artifactRoot).isEmpty()

assert Artifact.SingleArtifact.Reachables.isReady(artifactRoot)
assert Artifact.SingleArtifact.Reachables.loadArtifact(artifactRoot).rowKeySet() == ["rts.sample.TestA", "rts.sample.TestB", "rts.sample.TestC", "rts.sample.TestI"] as Set
