package util;

import org.w3c.dom.Element;;

public class Change {
	private String destination;
	private String insertDirection;
	private ChangeType type;
	private String oldValue;
	private String newValue;
	private Element newValueStruct;
	private boolean isText;
	
	Change(){}

	public Change(String destination,String insertDirection, ChangeType type, String oldValue, String newValue, Element newValueStruct,
			boolean isText) {
		super();
		this.destination = destination;
		this.type = type;
		this.oldValue = oldValue;
		this.newValue = newValue;
		this.newValueStruct = newValueStruct;
		this.isText = isText;
		this.insertDirection = insertDirection;
	}

	public Element getNewValueStruct() {
		return newValueStruct;
	}


	public void setNewValueStruct(Element newValueStruct) {
		this.newValueStruct = newValueStruct;
	}

	public boolean isText() {
		return isText;
	}
	
	public String getInsertDirection() {
		return insertDirection;
	}

	public void setInsertDirection(String insertDirection) {
		this.insertDirection = insertDirection;
	}

	public void setText(boolean isText) {
		this.isText = isText;
	}


	public String getDestination() {
		return destination;
	}
	public void setDestination(String destination) {
		this.destination = destination;
	}
	public ChangeType getType() {
		return type;
	}
	public void setType(ChangeType type) {
		this.type = type;
	}
	public String getOldValue() {
		return oldValue;
	}
	public void setOldValue(String oldValue) {
		this.oldValue = oldValue;
	}
	public String getNewValue() {
		return newValue;
	}
	public void setNewValue(String newValue) {
		this.newValue = newValue;
	}

	@Override
	public String toString() {
		return "Operation " + type + ", needs to be done at :" + destination + ", oldValue=" + oldValue + ", isNewValueText="
				+ this.isText;
	}
	
	
	
}
