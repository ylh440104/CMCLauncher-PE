package org.lwjgl.util.mapped;

import java.util.Iterator;

final class MappedForeach<T extends MappedObject> implements Iterable<T> {

	final T   mapped;
	final int elementCount;

	MappedForeach(T mapped, int elementCount) {
		this.mapped = mapped;
		this.elementCount = elementCount;
	}

	public Iterator<T> iterator() {
		return new Iterator<T>() {

			private int index;

			public boolean hasNext() {
				return this.index < (MappedForeach.this.elementCount);
			}

			public T next() {
				mapped.setViewAddress(mapped.getViewAddress(this.index++));
				return mapped;
			}

			public void remove() {
				throw new UnsupportedOperationException();
			}
		};
	}

}
