package util;

import org.w3c.dom.Element;

public class ElementPair {
	private Element first;
	private Element second;
	
	public ElementPair() {}
	
	public ElementPair(Element first,Element second) {
		this.first = first;
		this.second = second;
	}
	
	public Element getFirst() {
		return first;
	}
	public void setFirst(Element first) {
		this.first = first;
	}
	public Element getSecond() {
		return second;
	}
	public void setSecond(Element second) {
		this.second = second;
	}
	
	
}
