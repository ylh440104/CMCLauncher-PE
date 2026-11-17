package org.lwjgl.util.mapped;

public class MappedSet3 {

	private final MappedObject a, b, c;

	MappedSet3(MappedObject a, MappedObject b, MappedObject c) {
		this.a = a;
		this.b = b;
		this.c = c;
	}

	public int view;

	void view(int view) {
		a.setViewAddress(a.getViewAddress(view));
		b.setViewAddress(b.getViewAddress(view));
		c.setViewAddress(c.getViewAddress(view));
	}

	public void next() {
		this.a.next();
		this.b.next();
		this.c.next();
	}

}
