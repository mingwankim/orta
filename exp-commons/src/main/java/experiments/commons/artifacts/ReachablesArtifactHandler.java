package experiments.commons.artifacts;

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


import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

class ReachablesArtifactHandler extends ArtifactHandler<Table<String, String, Set<String>>> {
  private static final Gson gson = new Gson();

  ReachablesArtifactHandler(String filename) {
    super(filename);
  }

  @Override
  public boolean verify(Path filePath) throws IOException {
    return true;
  }

  @Override
  protected void dumpArtifact(Table<String, String, Set<String>> obj, Path artifactPath) throws IOException {

    try (TarArchiveOutputStream tar = new TarArchiveOutputStream(new GzipCompressorOutputStream(
            Files.newOutputStream(artifactPath)))) {
      tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
      for (String className : obj.rowKeySet()) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        Map<String, Set<String>> methods = obj.row(className);
        try (JsonWriter w = new JsonWriter(new OutputStreamWriter(bos))) {
          gson.toJson(methods, Map.class, w);
        }

        TarArchiveEntry tarEntry = new TarArchiveEntry(className);
        tarEntry.setSize(bos.size());
        tar.putArchiveEntry(tarEntry);
        tar.write(bos.toByteArray());
        tar.closeArchiveEntry();
      }
    }
  }

  @Override
  protected Table<String, String, Set<String>> readArtifact(Path artifactsPath) throws IOException {
    Table<String, String, Set<String>> reachables = HashBasedTable.create();
    try (TarArchiveInputStream is = new TarArchiveInputStream(
            new GzipCompressorInputStream(
                    Files.newInputStream(artifactsPath)
            ))) {

      ArchiveEntry entry;
      while ((entry = is.getNextEntry()) != null) {
        String testName = entry.getName();
        Map<String, Set<String>> classes = reachables.row(testName);
        JsonReader r = new JsonReader(new InputStreamReader(is));
        r.beginObject();
        while (r.peek() != JsonToken.END_OBJECT) {
          String className = r.nextName();
          if (r.peek() != JsonToken.NULL) {
            Set<String> methods = gson.fromJson(r, Set.class);
            classes.put(className, methods);
          } else {
            r.nextNull();
          }
        }
        r.endObject();
      }
    }

    return reachables;
  }
}
