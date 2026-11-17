package org.lwjgl.util.mapped;

import org.lwjgl.LWJGLUtil;
import org.lwjgl.MemoryUtil;

import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;

public abstract class MappedObject {

	static final boolean CHECKS = LWJGLUtil.getPrivilegedBoolean("org.lwjgl.util.mapped.Checks");

	protected MappedObject() {

	}

	public long baseAddress;

	public long viewAddress;

	ByteBuffer preventGC;

	public static int SIZEOF = -1;

	public int view;

	protected final long getViewAddress(final int view) {

		throw new InternalError("type not registered");
	}

	public final void setViewAddress(final long address) {
		if ( CHECKS )
			checkAddress(address);
		this.viewAddress = address;
	}

	final void checkAddress(final long address) {
		final long base = MemoryUtil.getAddress0(preventGC);
		final int offset = (int)(address - base);
		if ( address < base || preventGC.capacity() < (offset + getSizeof()) )
			throw new IndexOutOfBoundsException(Integer.toString(offset / getSizeof()));
	}

	final void checkRange(final int bytes) {
		if ( bytes < 0 )
			throw new IllegalArgumentException();

		if ( preventGC.capacity() < (viewAddress - MemoryUtil.getAddress0(preventGC) + bytes) )
			throw new BufferOverflowException();
	}

	public final int getAlign() {

		throw new InternalError("type not registered");
	}

	public final int getSizeof() {

		throw new InternalError("type not registered");
	}

	public final int capacity() {

		throw new InternalError("type not registered");
	}

	@SuppressWarnings("unused")
	public static <T extends MappedObject> T map(ByteBuffer bb) {

		throw new InternalError("type not registered");
	}

	@SuppressWarnings("unused")
	public static <T extends MappedObject> T map(long address, int capacity) {

		throw new InternalError("type not registered");
	}

	@SuppressWarnings("unused")
	public static <T extends MappedObject> T malloc(int elementCount) {

		throw new InternalError("type not registered");
	}

	public final <T extends MappedObject> T dup() {

		throw new InternalError("type not registered");
	}

	public final <T extends MappedObject> T slice() {

		throw new InternalError("type not registered");
	}

	public final void runViewConstructor() {

		throw new InternalError("type not registered");
	}

	public final void next() {

		throw new InternalError("type not registered");
	}

	@SuppressWarnings("unused")
	public final <T extends MappedObject> void copyTo(T target) {

		throw new InternalError("type not registered");
	}

	@SuppressWarnings("unused")
	public final <T extends MappedObject> void copyRange(T target, int instances) {

		throw new InternalError("type not registered");
	}

	public static <T extends MappedObject> Iterable<T> foreach(T mapped) {
		return foreach(mapped, mapped.capacity());
	}

	public static <T extends MappedObject> Iterable<T> foreach(T mapped, int elementCount) {
		return new MappedForeach<T>(mapped, elementCount);
	}

	@SuppressWarnings("unused")
	public final <T extends MappedObject> T[] asArray() {

		throw new InternalError("type not registered");
	}

	public final ByteBuffer backingByteBuffer() {
		return this.preventGC;
	}

}
