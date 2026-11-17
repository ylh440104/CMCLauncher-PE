package org.lwjgl.util.mapped;

import static org.objectweb.asm.ClassWriter.*;
import static org.objectweb.asm.Opcodes.*;

import org.lwjgl.BufferUtils;
import org.lwjgl.LWJGLUtil;
import org.lwjgl.MemoryUtil;
import org.objectweb.asm.ClassAdapter;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.Frame;
import org.objectweb.asm.tree.analysis.SimpleVerifier;
import org.objectweb.asm.util.TraceClassVisitor;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

public class MappedObjectTransformer {

	static final boolean PRINT_ACTIVITY = LWJGLUtil.DEBUG && LWJGLUtil.getPrivilegedBoolean("org.lwjgl.util.mapped.PrintActivity");
	static final boolean PRINT_TIMING   = PRINT_ACTIVITY && LWJGLUtil.getPrivilegedBoolean("org.lwjgl.util.mapped.PrintTiming");
	static final boolean PRINT_BYTECODE = LWJGLUtil.DEBUG && LWJGLUtil.getPrivilegedBoolean("org.lwjgl.util.mapped.PrintBytecode");

	static final Map<String, MappedSubtypeInfo> className_to_subtype;

	static final String MAPPED_OBJECT_JVM = jvmClassName(MappedObject.class);
	static final String MAPPED_HELPER_JVM = jvmClassName(MappedHelper.class);

	static final String MAPPEDSET_PREFIX = jvmClassName(MappedSet.class);
	static final String MAPPED_SET2_JVM  = jvmClassName(MappedSet2.class);
	static final String MAPPED_SET3_JVM  = jvmClassName(MappedSet3.class);
	static final String MAPPED_SET4_JVM  = jvmClassName(MappedSet4.class);

	static final String CACHE_LINE_PAD_JVM = "L" + jvmClassName(CacheLinePad.class) + ";";

	static final String VIEWADDRESS_METHOD_NAME = "getViewAddress";
	static final String NEXT_METHOD_NAME        = "next";
	static final String ALIGN_METHOD_NAME       = "getAlign";
	static final String SIZEOF_METHOD_NAME      = "getSizeof";
	static final String CAPACITY_METHOD_NAME    = "capacity";

	static final String VIEW_CONSTRUCTOR_NAME = "constructView$LWJGL";

	static final Map<Integer, String> OPCODE_TO_NAME   = new HashMap<Integer, String>();
	static final Map<Integer, String> INSNTYPE_TO_NAME = new HashMap<Integer, String>();

	static boolean is_currently_computing_frames;

	static {
		getClassEnums(Opcodes.class, OPCODE_TO_NAME, "V1_", "ACC_", "T_", "F_", "MH_");
		getClassEnums(AbstractInsnNode.class, INSNTYPE_TO_NAME);

		className_to_subtype = new HashMap<String, MappedSubtypeInfo>();

		{

			className_to_subtype.put(MAPPED_OBJECT_JVM, new MappedSubtypeInfo(MAPPED_OBJECT_JVM, null, -1, -1, -1, false));
		}

		final String vmName = System.getProperty("java.vm.name");
		if ( vmName != null && !vmName.contains("Server") ) {
			System.err.println("Warning: " + MappedObject.class.getSimpleName() + "s have inferiour performance on Client VMs, please consider switching to a Server VM.");
		}
	}

	public static void register(Class<? extends MappedObject> type) {
		if ( MappedObjectClassLoader.FORKED )
			return;

		final MappedType mapped = type.getAnnotation(MappedType.class);

		if ( mapped != null && mapped.padding() < 0 )
			throw new ClassFormatError("Invalid mapped type padding: " + mapped.padding());

		if ( type.getEnclosingClass() != null && !Modifier.isStatic(type.getModifiers()) )
			throw new InternalError("only top-level or static inner classes are allowed");

		final String className = jvmClassName(type);
		final Map<String, FieldInfo> fields = new HashMap<String, FieldInfo>();

		long sizeof = 0;
		for ( Field field : type.getDeclaredFields() ) {
			FieldInfo fieldInfo = registerField(mapped == null || mapped.autoGenerateOffsets(), className, sizeof, field);
			if ( fieldInfo == null )
				continue;

			fields.put(field.getName(), fieldInfo);

			sizeof = Math.max(sizeof, fieldInfo.offset + fieldInfo.lengthPadded);
		}

		int align = 4;
		int padding = 0;
		boolean cacheLinePadded = false;

		if ( mapped != null ) {
			align = mapped.align();
			if ( mapped.cacheLinePadding() ) {
				if ( mapped.padding() != 0 )
					throw new ClassFormatError("Mapped type padding cannot be specified together with cacheLinePadding.");

				final int cacheLineMod = (int)(sizeof % CacheUtil.getCacheLineSize());
				if ( cacheLineMod != 0 )
					padding = CacheUtil.getCacheLineSize() - cacheLineMod;

				cacheLinePadded = true;
			} else
				padding = mapped.padding();
		}

		sizeof += padding;

		final MappedSubtypeInfo mappedType = new MappedSubtypeInfo(className, fields, (int)sizeof, align, padding, cacheLinePadded);
		if ( className_to_subtype.put(className, mappedType) != null )
			throw new InternalError("duplicate mapped type: " + mappedType.className);
	}

	private static FieldInfo registerField(final boolean autoGenerateOffsets, final String className, long advancingOffset, final Field field) {
		if ( Modifier.isStatic(field.getModifiers()) )
			return null;

		if ( !field.getType().isPrimitive() && field.getType() != ByteBuffer.class )
			throw new ClassFormatError("field '" + className + "." + field.getName() + "' not supported: " + field.getType());

		MappedField meta = field.getAnnotation(MappedField.class);
		if ( meta == null && !autoGenerateOffsets )
			throw new ClassFormatError("field '" + className + "." + field.getName() + "' missing annotation " + MappedField.class.getName() + ": " + className);

		Pointer pointer = field.getAnnotation(Pointer.class);
		if ( pointer != null && field.getType() != long.class )
			throw new ClassFormatError("The @Pointer annotation can only be used on long fields. @Pointer field found: " + className + "." + field.getName() + ": " + field.getType());

		if ( Modifier.isVolatile(field.getModifiers()) && (pointer != null || field.getType() == ByteBuffer.class) )
			throw new ClassFormatError("The volatile keyword is not supported for @Pointer or ByteBuffer fields. Volatile field found: " + className + "." + field.getName() + ": " + field.getType());

		long byteLength;
		if ( field.getType() == long.class || field.getType() == double.class ) {
			if ( pointer == null )
				byteLength = 8;
			else
				byteLength = MappedObjectUnsafe.INSTANCE.addressSize();
		} else if ( field.getType() == double.class )
			byteLength = 8;
		else if ( field.getType() == int.class || field.getType() == float.class )
			byteLength = 4;
		else if ( field.getType() == char.class || field.getType() == short.class )
			byteLength = 2;
		else if ( field.getType() == byte.class )
			byteLength = 1;
		else if ( field.getType() == ByteBuffer.class ) {
			byteLength = meta.byteLength();
			if ( byteLength < 0 )
				throw new IllegalStateException("invalid byte length for mapped ByteBuffer field: " + className + "." + field.getName() + " [length=" + byteLength + "]");
		} else
			throw new ClassFormatError(field.getType().getName());

		if ( field.getType() != ByteBuffer.class && (advancingOffset % byteLength) != 0 )
			throw new IllegalStateException("misaligned mapped type: " + className + "." + field.getName());

		CacheLinePad pad = field.getAnnotation(CacheLinePad.class);

		long byteOffset = advancingOffset;
		if ( meta != null && meta.byteOffset() != -1 ) {
			if ( meta.byteOffset() < 0 )
				throw new ClassFormatError("Invalid field byte offset: " + className + "." + field.getName() + " [byteOffset=" + meta.byteOffset() + "]");
			if ( pad != null )
				throw new ClassFormatError("A field byte offset cannot be specified together with cache-line padding: " + className + "." + field.getName());

			byteOffset = meta.byteOffset();
		}

		long byteLengthPadded = byteLength;
		if ( pad != null ) {

			if ( pad.before() && byteOffset % CacheUtil.getCacheLineSize() != 0 )
				byteOffset += CacheUtil.getCacheLineSize() - (byteOffset & (CacheUtil.getCacheLineSize() - 1));

			if ( pad.after() && (byteOffset + byteLength) % CacheUtil.getCacheLineSize() != 0 )
				byteLengthPadded += CacheUtil.getCacheLineSize() - (byteOffset + byteLength) % CacheUtil.getCacheLineSize();

			assert !pad.before() || (byteOffset % CacheUtil.getCacheLineSize() == 0);
			assert !pad.after() || ((byteOffset + byteLengthPadded) % CacheUtil.getCacheLineSize() == 0);
		}

		if ( PRINT_ACTIVITY )
			LWJGLUtil.log(MappedObjectTransformer.class.getSimpleName() + ": " + className + "." + field.getName() + " [type=" + field.getType().getSimpleName() + ", offset=" + byteOffset + "]");

		return new FieldInfo(byteOffset, byteLength, byteLengthPadded, Type.getType(field.getType()), Modifier.isVolatile(field.getModifiers()), pointer != null);
	}

	static byte[] transformMappedObject(byte[] bytecode) {
		final ClassWriter cw = new ClassWriter(0);

		ClassVisitor cv = new ClassAdapter(cw) {

			private final String[] DEFINALIZE_LIST = {
				VIEWADDRESS_METHOD_NAME,
				NEXT_METHOD_NAME,
				ALIGN_METHOD_NAME,
				SIZEOF_METHOD_NAME,
				CAPACITY_METHOD_NAME,
			};

			public MethodVisitor visitMethod(int access, final String name, final String desc, final String signature, final String[] exceptions) {
				for ( String method : DEFINALIZE_LIST ) {
					if ( name.equals(method) ) {
						access &= ~ACC_FINAL;
						break;
					}
				}
				return super.visitMethod(access, name, desc, signature, exceptions);
			}
		};

		new ClassReader(bytecode).accept(cv, 0);
		return cw.toByteArray();
	}

	static byte[] transformMappedAPI(final String className, byte[] bytecode) {
		final ClassWriter cw = new ClassWriter(COMPUTE_FRAMES) {

			@Override
			protected String getCommonSuperClass(String a, String b) {

				if ( is_currently_computing_frames && !a.startsWith("java/") || !b.startsWith("java/") )
					return "java/lang/Object";

				return super.getCommonSuperClass(a, b);
			}

		};

		final TransformationAdapter ta = new TransformationAdapter(cw, className);

		ClassVisitor cv = ta;
		if ( className_to_subtype.containsKey(className) )
			cv = getMethodGenAdapter(className, cv);

		new ClassReader(bytecode).accept(cv, ClassReader.SKIP_FRAMES);

		if ( !ta.transformed )
			return bytecode;

		bytecode = cw.toByteArray();
		if ( PRINT_BYTECODE )
			printBytecode(bytecode);

		return bytecode;
	}

	private static ClassAdapter getMethodGenAdapter(final String className, final ClassVisitor cv) {
		return new ClassAdapter(cv) {

			@Override
			public void visitEnd() {
				final MappedSubtypeInfo mappedSubtype = className_to_subtype.get(className);

				generateViewAddressGetter();
				generateCapacity();
				generateAlignGetter(mappedSubtype);
				generateSizeofGetter();
				generateNext();

				for ( String fieldName : mappedSubtype.fields.keySet() ) {
					final FieldInfo field = mappedSubtype.fields.get(fieldName);

					if ( field.type.getDescriptor().length() > 1 ) {
						generateByteBufferGetter(fieldName, field);
					} else {
						generateFieldGetter(fieldName, field);
						generateFieldSetter(fieldName, field);
					}
				}

				super.visitEnd();
			}

			private void generateViewAddressGetter() {
				MethodVisitor mv = super.visitMethod(ACC_PUBLIC, VIEWADDRESS_METHOD_NAME, "(I)J", null, null);
				mv.visitCode();
				mv.visitVarInsn(ALOAD, 0);
				mv.visitFieldInsn(GETFIELD, MAPPED_OBJECT_JVM, "baseAddress", "J");
				mv.visitVarInsn(ILOAD, 1);
				mv.visitFieldInsn(GETSTATIC, className, "SIZEOF", "I");
				mv.visitInsn(IMUL);
				mv.visitInsn(I2L);
				mv.visitInsn(LADD);
				if ( MappedObject.CHECKS ) {
					mv.visitInsn(DUP2);
					mv.visitVarInsn(ALOAD, 0);
					mv.visitMethodInsn(INVOKESTATIC, MAPPED_HELPER_JVM, "checkAddress", "(JL" + MAPPED_OBJECT_JVM + ";)V");
				}
				mv.visitInsn(LRETURN);
				mv.visitMaxs(3, 2);
				mv.visitEnd();
			}

			private void generateCapacity() {

				MethodVisitor mv = super.visitMethod(ACC_PUBLIC, CAPACITY_METHOD_NAME, "()I", null, null);
				mv.visitCode();
				mv.visitVarInsn(ALOAD, 0);
				mv.visitMethodInsn(INVOKEVIRTUAL, MAPPED_OBJECT_JVM, "backingByteBuffer", "()L" + jvmClassName(ByteBuffer.class) + ";");
				mv.visitInsn(DUP);
				mv.visitMethodInsn(INVOKEVIRTUAL, jvmClassName(ByteBuffer.class), "capacity", "()I");
				mv.visitInsn(SWAP);
				mv.visitMethodInsn(INVOKESTATIC, jvmClassName(MemoryUtil.class), "getAddress0", "(L" + jvmClassName(Buffer.class) + ";)J");
				mv.visitVarInsn(ALOAD, 0);
				mv.visitFieldInsn(GETFIELD, MAPPED_OBJECT_JVM, "baseAddress", "J");
				mv.visitInsn(LSUB);
				mv.visitInsn(L2I);
				mv.visitInsn(IADD);
				mv.visitFieldInsn(GETSTATIC, className, "SIZEOF", "I");
				mv.visitInsn(IDIV);
				mv.visitInsn(IRETURN);
				mv.visitMaxs(3, 1);
				mv.visitEnd();
			}

			private void generateAlignGetter(final MappedSubtypeInfo mappedSubtype) {
				MethodVisitor mv = super.visitMethod(ACC_PUBLIC, ALIGN_METHOD_NAME, "()I", null, null);
				mv.visitCode();
				visitIntNode(mv, mappedSubtype.sizeof);
				mv.visitInsn(IRETURN);
				mv.visitMaxs(1, 1);
				mv.visitEnd();
			}

			private void generateSizeofGetter() {
				MethodVisitor mv = super.visitMethod(ACC_PUBLIC, SIZEOF_METHOD_NAME, "()I", null, null);
				mv.visitCode();
				mv.visitFieldInsn(GETSTATIC, className, "SIZEOF", "I");
				mv.visitInsn(IRETURN);
				mv.visitMaxs(1, 1);
				mv.visitEnd();
			}

			private void generateNext() {
				MethodVisitor mv = super.visitMethod(ACC_PUBLIC, NEXT_METHOD_NAME, "()V", null, null);
				mv.visitCode();
				mv.visitVarInsn(ALOAD, 0);
				mv.visitInsn(DUP);
				mv.visitFieldInsn(GETFIELD, MAPPED_OBJECT_JVM, "viewAddress", "J");
				mv.visitFieldInsn(GETSTATIC, className, "SIZEOF", "I");
				mv.visitInsn(I2L);
				mv.visitInsn(LADD);
				mv.visitMethodInsn(INVOKEVIRTUAL, className, "setViewAddress", "(J)V");
				mv.visitInsn(RETURN);
				mv.visitMaxs(3, 1);
				mv.visitEnd();
			}

			private void generateByteBufferGetter(final String fieldName, final FieldInfo field) {
				MethodVisitor mv = super.visitMethod(ACC_PUBLIC | ACC_STATIC, getterName(fieldName), "(L" + className + ";I)" + field.type.getDescriptor(), null, null);
				mv.visitCode();
				mv.visitVarInsn(ALOAD, 0);
				mv.visitVarInsn(ILOAD, 1);
				mv.visitMethodInsn(INVOKEVIRTUAL, className, VIEWADDRESS_METHOD_NAME, "(I)J");
				visitIntNode(mv, (int)field.offset);
				mv.visitInsn(I2L);
				mv.visitInsn(LADD);
				visitIntNode(mv, (int)field.length);
				mv.visitMethodInsn(INVOKESTATIC, MAPPED_HELPER_JVM, "newBuffer", "(JI)L" + jvmClassName(ByteBuffer.class) + ";");
				mv.visitInsn(ARETURN);
				mv.visitMaxs(3, 2);
				mv.visitEnd();
			}

			private void generateFieldGetter(final String fieldName, final FieldInfo field) {
				MethodVisitor mv = super.visitMethod(ACC_PUBLIC | ACC_STATIC, getterName(fieldName), "(L" + className + ";I)" + field.type.getDescriptor(), null, null);
				mv.visitCode();
				mv.visitVarInsn(ALOAD, 0);
				mv.visitVarInsn(ILOAD, 1);
				mv.visitMethodInsn(INVOKEVIRTUAL, className, VIEWADDRESS_METHOD_NAME, "(I)J");
				visitIntNode(mv, (int)field.offset);
				mv.visitInsn(I2L);
				mv.visitInsn(LADD);
				mv.visitMethodInsn(INVOKESTATIC, MAPPED_HELPER_JVM, field.getAccessType() + "get", "(J)" + field.type.getDescriptor());
				mv.visitInsn(field.type.getOpcode(IRETURN));
				mv.visitMaxs(3, 2);
				mv.visitEnd();
			}

			private void generateFieldSetter(final String fieldName, final FieldInfo field) {
				MethodVisitor mv = super.visitMethod(ACC_PUBLIC | ACC_STATIC, setterName(fieldName), "(L" + className + ";I" + field.type.getDescriptor() + ")V", null, null);
				mv.visitCode();
				int load = 0;
				switch ( field.type.getSort() ) {
					case Type.BOOLEAN:
					case Type.CHAR:
					case Type.BYTE:
					case Type.SHORT:
					case Type.INT:
						load = ILOAD;
						break;
					case Type.FLOAT:
						load = FLOAD;
						break;
					case Type.LONG:
						load = LLOAD;
						break;
					case Type.DOUBLE:
						load = DLOAD;
						break;
				}
				mv.visitVarInsn(load, 2);
				mv.visitVarInsn(ALOAD, 0);
				mv.visitVarInsn(ILOAD, 1);
				mv.visitMethodInsn(INVOKEVIRTUAL, className, VIEWADDRESS_METHOD_NAME, "(I)J");
				visitIntNode(mv, (int)field.offset);
				mv.visitInsn(I2L);
				mv.visitInsn(LADD);
				mv.visitMethodInsn(INVOKESTATIC, MAPPED_HELPER_JVM, field.getAccessType() + "put", "(" + field.type.getDescriptor() + "J)V");
				mv.visitInsn(RETURN);
				mv.visitMaxs(4, 4);
				mv.visitEnd();
			}

		};
	}

	private static class TransformationAdapter extends ClassAdapter {

		final String className;

		boolean transformed;

		TransformationAdapter(final ClassVisitor cv, final String className) {
			super(cv);
			this.className = className;
		}

		@Override
		public FieldVisitor visitField(final int access, final String name, final String desc, final String signature, final Object value) {

			final MappedSubtypeInfo mappedSubtype = className_to_subtype.get(className);
			if ( mappedSubtype != null && mappedSubtype.fields.containsKey(name) ) {
				if ( PRINT_ACTIVITY )
					LWJGLUtil.log(MappedObjectTransformer.class.getSimpleName() + ": discarding field: " + className + "." + name + ":" + desc);
				return null;
			}

			if ( (access & ACC_STATIC) == 0 ) {
				return new FieldNode(access, name, desc, signature, value) {
					public void visitEnd() {
						if ( visibleAnnotations == null ) {
							accept(cv);
							return;
						}

						boolean before = false;
						boolean after = false;
						int byteLength = 0;
						for ( AnnotationNode pad : visibleAnnotations ) {
							if ( CACHE_LINE_PAD_JVM.equals(pad.desc) ) {
								if ( "J".equals(desc) || "D".equals(desc) )
									byteLength = 8;
								else if ( "I".equals(desc) || "F".equals(desc) )
									byteLength = 4;
								else if ( "S".equals(desc) || "C".equals(desc) )
									byteLength = 2;
								else if ( "B".equals(desc) || "Z".equals(desc) )
									byteLength = 1;
								else
									throw new ClassFormatError("The @CacheLinePad annotation cannot be used on non-primitive fields: " + className + "." + name);

								transformed = true;

								after = true;
								if ( pad.values != null ) {
									for ( int i = 0; i < pad.values.size(); i += 2 ) {
										final boolean value = pad.values.get(i + 1).equals(Boolean.TRUE);
										if ( "before".equals(pad.values.get(i)) )
											before = value;
										else
											after = value;
									}
								}
								break;
							}
						}

						if ( before ) {
							final int count = CacheUtil.getCacheLineSize() / byteLength - 1;
							for ( int i = count; i >= 1; i-- )
								cv.visitField(access | ACC_PUBLIC | ACC_SYNTHETIC, name + "$PAD_" + i, desc, signature, null);
						}

						accept(cv);

						if ( after ) {
							final int count = CacheUtil.getCacheLineSize() / byteLength - 1;
							for ( int i = 1; i <= count; i++ )
								cv.visitField(access | ACC_PUBLIC | ACC_SYNTHETIC, name + "$PAD" + i, desc, signature, null);
						}
					}
				};
			} else
				return super.visitField(access, name, desc, signature, value);
		}

		@Override
		public MethodVisitor visitMethod(final int access, String name, final String desc, final String signature, final String[] exceptions) {

			if ( "<init>".equals(name) ) {
				final MappedSubtypeInfo mappedSubtype = className_to_subtype.get(className);
				if ( mappedSubtype != null ) {
					if ( !"()V".equals(desc) )
						throw new ClassFormatError(className + " can only have a default constructor, found: " + desc);

					final MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
					mv.visitVarInsn(ALOAD, 0);
					mv.visitMethodInsn(INVOKESPECIAL, MAPPED_OBJECT_JVM, "<init>", "()V");
					mv.visitInsn(RETURN);
					mv.visitMaxs(0, 0);

					name = VIEW_CONSTRUCTOR_NAME;
				}
			}

			final MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
			return new MethodNode(access, name, desc, signature, exceptions) {

				boolean needsTransformation;

				@Override
				public void visitMaxs(int a, int b) {
					try {
						is_currently_computing_frames = true;
						super.visitMaxs(a, b);
					} finally {
						is_currently_computing_frames = false;
					}
				}

				@Override
				public void visitFieldInsn(final int opcode, final String owner, final String name, final String desc) {
					if ( className_to_subtype.containsKey(owner) || owner.startsWith(MAPPEDSET_PREFIX) )
						needsTransformation = true;

					super.visitFieldInsn(opcode, owner, name, desc);
				}

				@Override
				public void visitMethodInsn(final int opcode, final String owner, final String name, final String desc) {
					if ( className_to_subtype.containsKey(owner) )
						needsTransformation = true;

					super.visitMethodInsn(opcode, owner, name, desc);
				}

				@Override
				public void visitEnd() {
					if ( needsTransformation ) {

						transformed = true;
						try {
							transformMethod(analyse());
						} catch (Exception e) {
							throw new RuntimeException(e);
						}
					}

					accept(mv);
				}

				private Frame<BasicValue>[] analyse() throws AnalyzerException {
					final Analyzer<BasicValue> a = new Analyzer<BasicValue>(new SimpleVerifier());
					a.analyze(className, this);
					return a.getFrames();
				}

				private void transformMethod(final Frame<BasicValue>[] frames) {
					final InsnList instructions = this.instructions;

					final Map<Integer, MappedSubtypeInfo> arrayVars = new HashMap<Integer, MappedSubtypeInfo>();

					final Map<AbstractInsnNode, Frame<BasicValue>> frameMap = new HashMap<AbstractInsnNode, Frame<BasicValue>>();
					for ( int i = 0; i < frames.length; i++ )
						frameMap.put(instructions.get(i), frames[i]);

					for ( int i = 0; i < instructions.size(); i++ ) {
						final AbstractInsnNode instruction = instructions.get(i);

						switch ( instruction.getType() ) {
							case AbstractInsnNode.VAR_INSN:
								if ( instruction.getOpcode() == ALOAD ) {
									VarInsnNode varInsn = (VarInsnNode)instruction;
									final MappedSubtypeInfo mappedSubtype = arrayVars.get(varInsn.var);
									if ( mappedSubtype != null )
										i = transformArrayAccess(instructions, i, frameMap, varInsn, mappedSubtype, varInsn.var);
								}
								break;
							case AbstractInsnNode.FIELD_INSN:
								FieldInsnNode fieldInsn = (FieldInsnNode)instruction;

								final InsnList list = transformFieldAccess(fieldInsn);
								if ( list != null )
									i = replace(instructions, i, instruction, list);

								break;
							case AbstractInsnNode.METHOD_INSN:
								MethodInsnNode methodInsn = (MethodInsnNode)instruction;
								final MappedSubtypeInfo mappedType = className_to_subtype.get(methodInsn.owner);
								if ( mappedType != null )
									i = transformMethodCall(instructions, i, frameMap, methodInsn, mappedType, arrayVars);
								break;
						}
					}
				}
			};
		}
	}

	static int transformMethodCall(final InsnList instructions, int i, final Map<AbstractInsnNode, Frame<BasicValue>> frameMap, final MethodInsnNode methodInsn, final MappedSubtypeInfo mappedType, final Map<Integer, MappedSubtypeInfo> arrayVars) {
		switch ( methodInsn.getOpcode() ) {
			case INVOKEVIRTUAL:
				if ( "asArray".equals(methodInsn.name) && methodInsn.desc.equals("()[L" + MAPPED_OBJECT_JVM + ";") ) {

					AbstractInsnNode nextInstruction;
					checkInsnAfterIsArray(nextInstruction = methodInsn.getNext(), CHECKCAST);
					checkInsnAfterIsArray(nextInstruction = nextInstruction.getNext(), ASTORE);

					final Frame<BasicValue> frame = frameMap.get(nextInstruction);
					final String targetType = frame.getStack(frame.getStackSize() - 1).getType().getElementType().getInternalName();
					if ( !methodInsn.owner.equals(targetType) ) {

						throw new ClassCastException("Source: " + methodInsn.owner + " - Target: " + targetType);
					}

					final VarInsnNode varInstruction = (VarInsnNode)nextInstruction;

					arrayVars.put(varInstruction.var, mappedType);

					instructions.remove(methodInsn.getNext());
					instructions.remove(methodInsn);
				}

				if ( "dup".equals(methodInsn.name) && methodInsn.desc.equals("()L" + MAPPED_OBJECT_JVM + ";") ) {
					i = replace(instructions, i, methodInsn, generateDupInstructions(methodInsn));
					break;
				}

				if ( "slice".equals(methodInsn.name) && methodInsn.desc.equals("()L" + MAPPED_OBJECT_JVM + ";") ) {
					i = replace(instructions, i, methodInsn, generateSliceInstructions(methodInsn));
					break;
				}

				if ( "runViewConstructor".equals(methodInsn.name) && "()V".equals(methodInsn.desc) ) {
					i = replace(instructions, i, methodInsn, generateRunViewConstructorInstructions(methodInsn));
					break;
				}

				if ( "copyTo".equals(methodInsn.name) && methodInsn.desc.equals("(L" + MAPPED_OBJECT_JVM + ";)V") ) {
					i = replace(instructions, i, methodInsn, generateCopyToInstructions(mappedType));
					break;
				}

				if ( "copyRange".equals(methodInsn.name) && methodInsn.desc.equals("(L" + MAPPED_OBJECT_JVM + ";I)V") ) {
					i = replace(instructions, i, methodInsn, generateCopyRangeInstructions(mappedType));
					break;
				}

				break;
			case INVOKESPECIAL:

				if ( methodInsn.owner.equals(MAPPED_OBJECT_JVM) && "<init>".equals(methodInsn.name) && "()V".equals(methodInsn.desc) ) {
					instructions.remove(methodInsn.getPrevious());
					instructions.remove(methodInsn);

					i -= 2;
				}
				break;
			case INVOKESTATIC:
				boolean isMapDirectMethod = "map".equals(methodInsn.name) && methodInsn.desc.equals("(JI)L" + MAPPED_OBJECT_JVM + ";");
				boolean isMapBufferMethod = "map".equals(methodInsn.name) && methodInsn.desc.equals("(Ljava/nio/ByteBuffer;)L" + MAPPED_OBJECT_JVM + ";");
				boolean isMallocMethod = "malloc".equals(methodInsn.name) && methodInsn.desc.equals("(I)L" + MAPPED_OBJECT_JVM + ";");

				if ( (isMapDirectMethod || isMapBufferMethod) || isMallocMethod )
					i = replace(instructions, i, methodInsn, generateMapInstructions(mappedType, methodInsn.owner, isMapDirectMethod, isMallocMethod));
				break;
		}

		return i;
	}

	private static InsnList generateCopyRangeInstructions(final MappedSubtypeInfo mappedType) {
		final InsnList list = new InsnList();

		list.add(getIntNode(mappedType.sizeof));

		list.add(new InsnNode(IMUL));

		list.add(new MethodInsnNode(INVOKESTATIC, MAPPED_HELPER_JVM, "copy", "(L" + MAPPED_OBJECT_JVM + ";L" + MAPPED_OBJECT_JVM + ";I)V"));

		return list;
	}

	private static InsnList generateCopyToInstructions(final MappedSubtypeInfo mappedType) {
		final InsnList list = new InsnList();

		list.add(getIntNode(mappedType.sizeof - mappedType.padding));

		list.add(new MethodInsnNode(INVOKESTATIC, MAPPED_HELPER_JVM, "copy", "(L" + MAPPED_OBJECT_JVM + ";L" + MAPPED_OBJECT_JVM + ";I)V"));

		return list;
	}

	private static InsnList generateRunViewConstructorInstructions(final MethodInsnNode methodInsn) {
		final InsnList list = new InsnList();

		list.add(new InsnNode(DUP));

		list.add(new MethodInsnNode(INVOKEVIRTUAL, methodInsn.owner, VIEW_CONSTRUCTOR_NAME, "()V"));

		return list;
	}

	private static InsnList generateSliceInstructions(final MethodInsnNode methodInsn) {
		final InsnList list = new InsnList();

		list.add(new TypeInsnNode(NEW, methodInsn.owner));

		list.add(new InsnNode(DUP));

		list.add(new MethodInsnNode(INVOKESPECIAL, methodInsn.owner, "<init>", "()V"));

		list.add(new MethodInsnNode(INVOKESTATIC, MAPPED_HELPER_JVM, "slice", "(L" + MAPPED_OBJECT_JVM + ";L" + MAPPED_OBJECT_JVM + ";)L" + MAPPED_OBJECT_JVM + ";"));

		return list;
	}

	private static InsnList generateDupInstructions(final MethodInsnNode methodInsn) {
		final InsnList list = new InsnList();

		list.add(new TypeInsnNode(NEW, methodInsn.owner));

		list.add(new InsnNode(DUP));

		list.add(new MethodInsnNode(INVOKESPECIAL, methodInsn.owner, "<init>", "()V"));

		list.add(new MethodInsnNode(INVOKESTATIC, MAPPED_HELPER_JVM, "dup", "(L" + MAPPED_OBJECT_JVM + ";L" + MAPPED_OBJECT_JVM + ";)L" + MAPPED_OBJECT_JVM + ";"));

		return list;
	}

	private static InsnList generateMapInstructions(final MappedSubtypeInfo mappedType, final String className, final boolean mapDirectMethod, final boolean mallocMethod) {
		final InsnList trg = new InsnList();

		if ( mallocMethod ) {

			trg.add(getIntNode(mappedType.sizeof));

			trg.add(new InsnNode(IMUL));

			trg.add(new MethodInsnNode(INVOKESTATIC, mappedType.cacheLinePadded ? jvmClassName(CacheUtil.class) : jvmClassName(BufferUtils.class), "createByteBuffer", "(I)L" + jvmClassName(ByteBuffer.class) + ";"));

		} else if ( mapDirectMethod ) {

			trg.add(new MethodInsnNode(INVOKESTATIC, MAPPED_HELPER_JVM, "newBuffer", "(JI)L" + jvmClassName(ByteBuffer.class) + ";"));

		}

		trg.add(new TypeInsnNode(NEW, className));

		trg.add(new InsnNode(DUP));

		trg.add(new MethodInsnNode(INVOKESPECIAL, className, "<init>", "()V"));

		trg.add(new InsnNode(DUP_X1));

		trg.add(new InsnNode(SWAP));

		trg.add(getIntNode(mappedType.align));

		trg.add(getIntNode(mappedType.sizeof));

		trg.add(new MethodInsnNode(INVOKESTATIC, MAPPED_HELPER_JVM, "setup", "(L" + MAPPED_OBJECT_JVM + ";Ljava/nio/ByteBuffer;II)V"));

		return trg;
	}

	static InsnList transformFieldAccess(final FieldInsnNode fieldInsn) {
		final MappedSubtypeInfo mappedSubtype;
		mappedSubtype = className_to_subtype.get(fieldInsn.owner);
		if ( mappedSubtype == null ) {

			outer:
			if ( "view".equals(fieldInsn.name) && fieldInsn.owner.startsWith(MAPPEDSET_PREFIX) )
				return generateSetViewInstructions(fieldInsn);

			return null;
		}

		if ( "SIZEOF".equals(fieldInsn.name) )
			return generateSIZEOFInstructions(fieldInsn, mappedSubtype);

		if ( "view".equals(fieldInsn.name) )
			return generateViewInstructions(fieldInsn, mappedSubtype);

		if ( "baseAddress".equals(fieldInsn.name) || "viewAddress".equals(fieldInsn.name) ) {
			return generateAddressInstructions(fieldInsn);
		}

		final FieldInfo field = mappedSubtype.fields.get(fieldInsn.name);
		if ( field == null )
			return null;

		if ( fieldInsn.desc.equals("L" + jvmClassName(ByteBuffer.class) + ";") )
			return generateByteBufferInstructions(fieldInsn, mappedSubtype, field.offset);

		return generateFieldInstructions(fieldInsn, field);
	}

	private static InsnList generateSetViewInstructions(final FieldInsnNode fieldInsn) {
		if ( fieldInsn.getOpcode() == GETFIELD )
			throwAccessErrorOnReadOnlyField(fieldInsn.owner, fieldInsn.name);
		if ( fieldInsn.getOpcode() != PUTFIELD )
			throw new InternalError();

		final InsnList list = new InsnList();

		if ( MAPPED_SET2_JVM.equals(fieldInsn.owner) )
			list.add(new MethodInsnNode(INVOKESTATIC, MAPPED_HELPER_JVM, "put_views", "(L" + MAPPED_SET2_JVM + ";I)V"));
		else if ( MAPPED_SET3_JVM.equals(fieldInsn.owner) )
			list.add(new MethodInsnNode(INVOKESTATIC, MAPPED_HELPER_JVM, "put_views", "(L" + MAPPED_SET3_JVM + ";I)V"));
		else if ( MAPPED_SET4_JVM.equals(fieldInsn.owner) )
			list.add(new MethodInsnNode(INVOKESTATIC, MAPPED_HELPER_JVM, "put_views", "(L" + MAPPED_SET4_JVM + ";I)V"));
		else
			throw new InternalError();

		return list;
	}

	private static InsnList generateSIZEOFInstructions(final FieldInsnNode fieldInsn, final MappedSubtypeInfo mappedSubtype) {
		if ( !"I".equals(fieldInsn.desc) )
			throw new InternalError();

		final InsnList list = new InsnList();

		if ( fieldInsn.getOpcode() == GETSTATIC ) {
			list.add(getIntNode(mappedSubtype.sizeof));
			return list;
		}

		if ( fieldInsn.getOpcode() == PUTSTATIC )
			throwAccessErrorOnReadOnlyField(fieldInsn.owner, fieldInsn.name);

		throw new InternalError();
	}

	private static InsnList generateViewInstructions(final FieldInsnNode fieldInsn, final MappedSubtypeInfo mappedSubtype) {
		if ( !"I".equals(fieldInsn.desc) )
			throw new InternalError();

		final InsnList list = new InsnList();

		if ( fieldInsn.getOpcode() == GETFIELD ) {
			if ( mappedSubtype.sizeof_shift != 0 ) {

				list.add(getIntNode(mappedSubtype.sizeof_shift));

				list.add(new MethodInsnNode(INVOKESTATIC, MAPPED_HELPER_JVM, "get_view_shift", "(L" + MAPPED_OBJECT_JVM + ";I)I"));

			} else {

				list.add(getIntNode(mappedSubtype.sizeof));

				list.add(new MethodInsnNode(INVOKESTATIC, MAPPED_HELPER_JVM, "get_view", "(L" + MAPPED_OBJECT_JVM + ";I)I"));

			}
			return list;
		}

		if ( fieldInsn.getOpcode() == PUTFIELD ) {
			if ( mappedSubtype.sizeof_shift != 0 ) {

				list.add(getIntNode(mappedSubtype.sizeof_shift));

				list.add(new MethodInsnNode(INVOKESTATIC, MAPPED_HELPER_JVM, "put_view_shift", "(L" + MAPPED_OBJECT_JVM + ";II)V"));

			} else {

				list.add(getIntNode(mappedSubtype.sizeof));

				list.add(new MethodInsnNode(INVOKESTATIC, MAPPED_HELPER_JVM, "put_view", "(L" + MAPPED_OBJECT_JVM + ";II)V"));

			}
			return list;
		}

		throw new InternalError();
	}

	private static InsnList generateAddressInstructions(final FieldInsnNode fieldInsn) {
		if ( !"J".equals(fieldInsn.desc) )
			throw new IllegalStateException();

		if ( fieldInsn.getOpcode() == GETFIELD )
			return null;

		if ( fieldInsn.getOpcode() == PUTFIELD )
			throwAccessErrorOnReadOnlyField(fieldInsn.owner, fieldInsn.name);

		throw new InternalError();
	}

	private static InsnList generateByteBufferInstructions(final FieldInsnNode fieldInsn, final MappedSubtypeInfo mappedSubtype, final long fieldOffset) {
		if ( fieldInsn.getOpcode() == PUTFIELD )
			throwAccessErrorOnReadOnlyField(fieldInsn.owner, fieldInsn.name);

		if ( fieldInsn.getOpcode() == GETFIELD ) {
			final InsnList list = new InsnList();

			list.add(new FieldInsnNode(GETFIELD, mappedSubtype.className, "viewAddress", "J"));

			list.add(new LdcInsnNode(fieldOffset));

			list.add(new InsnNode(LADD));

			list.add(new LdcInsnNode(mappedSubtype.fields.get(fieldInsn.name).length));

			list.add(new InsnNode(L2I));

			list.add(new MethodInsnNode(INVOKESTATIC, MAPPED_HELPER_JVM, "newBuffer", "(JI)L" + jvmClassName(ByteBuffer.class) + ";"));

			return list;
		}

		throw new InternalError();
	}

	private static InsnList generateFieldInstructions(final FieldInsnNode fieldInsn, final FieldInfo field) {
		final InsnList list = new InsnList();

		if ( fieldInsn.getOpcode() == PUTFIELD ) {

			list.add(getIntNode((int)field.offset));

			list.add(new MethodInsnNode(INVOKESTATIC, MAPPED_HELPER_JVM, field.getAccessType() + "put", "(L" + MAPPED_OBJECT_JVM + ";" + fieldInsn.desc + "I)V"));

			return list;
		}

		if ( fieldInsn.getOpcode() == GETFIELD ) {

			list.add(getIntNode((int)field.offset));

			list.add(new MethodInsnNode(INVOKESTATIC, MAPPED_HELPER_JVM, field.getAccessType() + "get", "(L" + MAPPED_OBJECT_JVM + ";I)" + fieldInsn.desc));

			return list;
		}

		throw new InternalError();
	}

	static int transformArrayAccess(final InsnList instructions, int i, final Map<AbstractInsnNode, Frame<BasicValue>> frameMap, final VarInsnNode loadInsn, final MappedSubtypeInfo mappedSubtype, final int var) {

		final int loadStackSize = frameMap.get(loadInsn).getStackSize() + 1;

		AbstractInsnNode nextInsn = loadInsn;

		while ( true ) {
			nextInsn = nextInsn.getNext();
			if ( nextInsn == null )
				throw new InternalError();

			Frame<BasicValue> frame = frameMap.get(nextInsn);
			if ( frame == null )
				continue;

			int stackSize = frame.getStackSize();

			if ( stackSize == loadStackSize + 1 && nextInsn.getOpcode() == AALOAD ) {
				final AbstractInsnNode aaLoadInsn = nextInsn;

				while ( true ) {
					nextInsn = nextInsn.getNext();
					if ( nextInsn == null )
						break;

					frame = frameMap.get(nextInsn);
					if ( frame == null )
						continue;
					stackSize = frame.getStackSize();

					if ( stackSize == loadStackSize + 1 && nextInsn.getOpcode() == PUTFIELD ) {
						final FieldInsnNode fieldInsn = (FieldInsnNode)nextInsn;

						instructions.insert(nextInsn, new MethodInsnNode(INVOKESTATIC, mappedSubtype.className, setterName(fieldInsn.name), "(L" + mappedSubtype.className + ";I" + fieldInsn.desc + ")V"));

						instructions.remove(nextInsn);

						break;
					} else if ( stackSize == loadStackSize && nextInsn.getOpcode() == GETFIELD ) {
						final FieldInsnNode fieldInsn = (FieldInsnNode)nextInsn;

						instructions.insert(nextInsn, new MethodInsnNode(INVOKESTATIC, mappedSubtype.className, getterName(fieldInsn.name), "(L" + mappedSubtype.className + ";I)" + fieldInsn.desc));

						instructions.remove(nextInsn);

						break;
					} else if ( stackSize == loadStackSize && nextInsn.getOpcode() == DUP && nextInsn.getNext().getOpcode() == GETFIELD ) {

						final FieldInsnNode fieldInsn = (FieldInsnNode)nextInsn.getNext();

						final MethodInsnNode getter = new MethodInsnNode(INVOKESTATIC, mappedSubtype.className, getterName(fieldInsn.name), "(L" + mappedSubtype.className + ";I)" + fieldInsn.desc);

						instructions.insert(nextInsn, new InsnNode(DUP2));

						instructions.insert(nextInsn.getNext(), getter);

						instructions.remove(nextInsn);
						instructions.remove(fieldInsn);

						nextInsn = getter;
						continue;
					} else if ( stackSize < loadStackSize )
						throw new ClassFormatError("Invalid " + mappedSubtype.className + " view array usage detected: " + getOpcodeName(nextInsn));
				}

				instructions.remove(aaLoadInsn);

				return i;
			} else if ( stackSize == loadStackSize && nextInsn.getOpcode() == ARRAYLENGTH ) {
				if ( LWJGLUtil.DEBUG && loadInsn.getNext() != nextInsn )
					throw new InternalError();

				instructions.remove(nextInsn);
				loadInsn.var = var;
				instructions.insert(loadInsn, new MethodInsnNode(INVOKEVIRTUAL, mappedSubtype.className, CAPACITY_METHOD_NAME, "()I"));

				return i + 1;
			} else if ( stackSize < loadStackSize )
				throw new ClassFormatError("Invalid " + mappedSubtype.className + " view array usage detected: " + getOpcodeName(nextInsn));
		}
	}

	private static class FieldInfo {

		final long    offset;
		final long    length;
		final long    lengthPadded;
		final Type    type;
		final boolean isVolatile;
		final boolean isPointer;

		FieldInfo(final long offset, final long length, final long lengthPadded, final Type type, final boolean isVolatile, final boolean isPointer) {
			this.offset = offset;
			this.length = length;
			this.lengthPadded = lengthPadded;
			this.type = type;
			this.isVolatile = isVolatile;
			this.isPointer = isPointer;
		}

		String getAccessType() {
			return isPointer ? "a" : type.getDescriptor().toLowerCase() + (isVolatile ? "v" : "");
		}

	}

	private static class MappedSubtypeInfo {

		final String className;

		final int     sizeof;
		final int     sizeof_shift;
		final int     align;
		final int     padding;
		final boolean cacheLinePadded;

		final Map<String, FieldInfo> fields;

		MappedSubtypeInfo(String className, Map<String, FieldInfo> fields, int sizeof, int align, int padding, final boolean cacheLinePadded) {
			this.className = className;

			this.sizeof = sizeof;
			if ( ((sizeof - 1) & sizeof) == 0 )
				this.sizeof_shift = getPoT(sizeof);
			else
				this.sizeof_shift = 0;
			this.align = align;
			this.padding = padding;
			this.cacheLinePadded = cacheLinePadded;

			this.fields = fields;
		}

		private static int getPoT(int value) {
			int pot = -1;
			while ( value > 0 ) {
				pot++;
				value >>= 1;
			}
			return pot;
		}

	}

	private static void getClassEnums(final Class clazz, final Map<Integer, String> map, final String... prefixFilters) {
		try {
			OUTER:
			for ( Field field : clazz.getFields() ) {
				if ( !Modifier.isStatic(field.getModifiers()) || field.getType() != int.class )
					continue;

				for ( String filter : prefixFilters ) {
					if ( field.getName().startsWith(filter) )
						continue OUTER;
				}

				if ( map.put((Integer)field.get(null), field.getName()) != null )
					throw new IllegalStateException();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	static String getOpcodeName(final AbstractInsnNode insn) {
		final String op = OPCODE_TO_NAME.get(insn.getOpcode());
		return INSNTYPE_TO_NAME.get(insn.getType()) + ": " + insn.getOpcode() + (op == null ? "" : " [" + OPCODE_TO_NAME.get(insn.getOpcode()) + "]");
	}

	static String jvmClassName(Class<?> type) {
		return type.getName().replace('.', '/');
	}

	static String getterName(final String fieldName) {
		return "get$" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1) + "$LWJGL";
	}

	static String setterName(final String fieldName) {
		return "set$" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1) + "$LWJGL";
	}

	private static void checkInsnAfterIsArray(final AbstractInsnNode instruction, final int opcode) {
		if ( instruction == null )
			throw new ClassFormatError("Unexpected end of instructions after .asArray() method.");

		if ( instruction.getOpcode() != opcode )
			throw new ClassFormatError("The result of .asArray() must be stored to a local variable. Found: " + getOpcodeName(instruction));
	}

	static AbstractInsnNode getIntNode(final int value) {
		if ( value <= 5 && -1 <= value )
			return new InsnNode(ICONST_M1 + value + 1);

		if ( value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE )
			return new IntInsnNode(BIPUSH, value);

		if ( value >= Short.MIN_VALUE && value <= Short.MAX_VALUE )
			return new IntInsnNode(SIPUSH, value);

		return new LdcInsnNode(value);
	}

	static void visitIntNode(final MethodVisitor mv, final int value) {
		if ( value <= 5 && -1 <= value )
			mv.visitInsn(ICONST_M1 + value + 1);
		else if ( value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE )
			mv.visitIntInsn(BIPUSH, value);
		else if ( value >= Short.MIN_VALUE && value <= Short.MAX_VALUE )
			mv.visitIntInsn(SIPUSH, value);
		else
			mv.visitLdcInsn(value);
	}

	static int replace(final InsnList instructions, final int i, final AbstractInsnNode location, final InsnList list) {
		final int size = list.size();

		instructions.insert(location, list);
		instructions.remove(location);

		return i + (size - 1);
	}

	private static void throwAccessErrorOnReadOnlyField(String className, String fieldName) {
		throw new IllegalAccessError("The " + className + "." + fieldName + " field is final.");
	}

	private static void printBytecode(byte[] bytecode) {
		StringWriter sw = new StringWriter();
		ClassVisitor tracer = new TraceClassVisitor(new ClassWriter(0), new PrintWriter(sw));
		new ClassReader(bytecode).accept(tracer, 0);
		String dump = sw.toString();

		LWJGLUtil.log(dump);
	}

}
