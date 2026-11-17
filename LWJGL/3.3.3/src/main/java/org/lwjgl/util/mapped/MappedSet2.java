package org.lwjgl.util.mapped;

public class MappedSet2 {

	private final MappedObject a, b;

	MappedSet2(MappedObject a, MappedObject b) {
		this.a = a;
		this.b = b;
	}

	public int view;

	void view(int view) {
		a.setViewAddress(a.getViewAddress(view));
		b.setViewAddress(b.getViewAddress(view));
	}

	public void next() {
		this.a.next();
		this.b.next();
	}

}
