package org.lwjgl.util.mapped;

public class MappedSet4 {

	private final MappedObject a, b, c, d;

	MappedSet4(MappedObject a, MappedObject b, MappedObject c, MappedObject d) {
		this.a = a;
		this.b = b;
		this.c = c;
		this.d = d;
	}

	public int view;

	void view(int view) {
		a.setViewAddress(a.getViewAddress(view));
		b.setViewAddress(b.getViewAddress(view));
		c.setViewAddress(c.getViewAddress(view));
		d.setViewAddress(d.getViewAddress(view));
	}

	public void next() {
		this.a.next();
		this.b.next();
		this.c.next();
		this.d.next();
	}
}
