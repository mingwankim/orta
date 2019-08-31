package org.orta.diff;

/*-
 * #%L
 * orta-diff
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



import com.google.common.primitives.Ints;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.MethodNode;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

class InsnVisitor extends MethodVisitor {

  private final MessageDigest md = MessageDigest.getInstance("MD5");

  private InsnVisitor() throws NoSuchAlgorithmException {
    super(Opcodes.ASM6);
  }

  public static MessageDigest compute(MethodNode node) throws NoSuchAlgorithmException {
    InsnVisitor visitor = new InsnVisitor();
    node.accept(visitor);
    return visitor.md;
  }

  @Override
  public void visitInsn(int opcode) {
    md.update(Ints.toByteArray(opcode));
  }

  @Override
  public void visitIntInsn(int opcode, int operand) {
    md.update(Ints.toByteArray(opcode));
    md.update(Ints.toByteArray(operand));
  }

  @Override
  public void visitVarInsn(int opcode, int var) {
    md.update(Ints.toByteArray(opcode));
    md.update(Ints.toByteArray(var));
  }

  @Override
  public void visitTypeInsn(int opcode, String type) {
    md.update(Ints.toByteArray(opcode));
    md.update(type.getBytes());
  }

  @Override
  public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
    md.update(Ints.toByteArray(opcode));
    md.update(owner.getBytes());
    md.update(name.getBytes());
    md.update(descriptor.getBytes());
  }

  @Override
  public void visitMethodInsn(int opcode, String owner, String name, String descriptor,
                              boolean isInterface) {
    md.update(Ints.toByteArray(opcode));
    md.update(owner.getBytes());
    md.update(name.getBytes());
    md.update(descriptor.getBytes());
    md.update(isInterface ? (byte) 0 : (byte) 1);
  }

  private void updateMethodHandle(Handle handler) {
    md.update(handler.getDesc().getBytes());
    md.update(handler.getName().getBytes());
    md.update(handler.getOwner().getBytes());
    md.update(Ints.toByteArray(handler.getTag()));
  }

  @Override
  public void visitInvokeDynamicInsn(String name, String descriptor, Handle bootstrapMethodHandle,
                                     Object... bootstrapMethodArguments) {
    md.update(name.getBytes());
    md.update(descriptor.getBytes());
    updateMethodHandle(bootstrapMethodHandle);
    for (Object o : bootstrapMethodArguments) {
      if (o instanceof Handle) {
        updateMethodHandle((Handle) o);
      } else {
        md.update(o.toString().getBytes());
      }
    }
  }

  @Override
  public void visitJumpInsn(int opcode, Label label) {
    md.update(Ints.toByteArray(opcode));
  }

  @Override
  public void visitLdcInsn(Object value) {
    md.update(value.toString().getBytes());
  }

  @Override
  public void visitIincInsn(int var, int increment) {
    md.update(Ints.toByteArray(var));
    md.update(Ints.toByteArray(increment));
  }

  @Override
  public void visitTableSwitchInsn(int min, int max, Label dflt, Label... labels) {
    md.update(Ints.toByteArray(min));
    md.update(Ints.toByteArray(max));
  }

  @Override
  public void visitLookupSwitchInsn(Label dflt, int[] keys, Label[] labels) {
    for (int key : keys) {
      md.update(Ints.toByteArray(key));
    }
  }

  @Override
  public void visitMultiANewArrayInsn(String descriptor, int numDimensions) {
    md.update(descriptor.getBytes());
    md.update(Ints.toByteArray(numDimensions));
  }

  @Override
  public void visitTryCatchBlock(Label start, Label end, Label handler, String type) {
    if (type == null) {
      md.update("finally".getBytes());
    } else {
      md.update(type.getBytes());
    }
  }
}
