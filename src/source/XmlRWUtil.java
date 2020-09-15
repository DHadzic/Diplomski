package source;

import static org.apache.xerces.jaxp.JAXPConstants.JAXP_SCHEMA_LANGUAGE;
import static org.apache.xerces.jaxp.JAXPConstants.W3C_XML_SCHEMA;

import java.io.File;
import java.util.ArrayList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import util.Change;
import util.ChangeType;
import util.ComparisonType;

public class XmlRWUtil implements ErrorHandler{

	private ArrayList<Element> allRegisteredElements;
	private Document document;
	private String filePath;

	private static DocumentBuilderFactory factory;
	private static TransformerFactory transformerFactory;
	static {
		factory = DocumentBuilderFactory.newInstance();
		factory.setValidating(false);
		factory.setNamespaceAware(true);
		factory.setIgnoringComments(true);
		factory.setIgnoringElementContentWhitespace(true);
		factory.setAttribute(JAXP_SCHEMA_LANGUAGE, W3C_XML_SCHEMA);
	}
	static {
		factory = DocumentBuilderFactory.newInstance();
		transformerFactory = TransformerFactory.newInstance();
	}

	public XmlRWUtil(String filePath) {
		this.filePath = filePath;
		this.buildDocument(filePath);
		this.loadAllElements();
	}
	
	public void buildDocument(String filePath) {

		try {
			DocumentBuilder builder = factory.newDocumentBuilder();
			builder.setErrorHandler(this);
			
			document = builder.parse(new File(filePath)); 

			if (document != null)
				System.out.println("[INFO] File parsed with no errors.");
			else
				System.out.println("[WARN] Document is null.");
		} catch (SAXParseException e) {
			System.out.println("[ERROR] Parsing error, line: " + e.getLineNumber() + ", uri: " + e.getSystemId());
			System.out.println("[ERROR] " + e.getMessage() );
			System.out.print("[ERROR] Embedded exception: ");
			
			Exception embeddedException = e;
			if (e.getException() != null)
				embeddedException = e.getException();

			embeddedException.printStackTrace();
			System.exit(0);
		} catch (SAXException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public ArrayList<Change> getChanges() {
		NodeList nodes = document.getElementsByTagName("textualMod");
		ArrayList<Change> changes = new ArrayList<Change>();
		
		for(int i=0 ; i<nodes.getLength() ; i++) {
			changes.add(createChange(nodes.item(i)));
		}
		return changes;
	}
	
	private Change createChange(Node node) {
		Element elem = (Element) node;
		Element helpElem;
		String operation = "",destination = "",old_text = "",new_text = "", insertDirection = "";
		operation = elem.getAttribute("type");
		NodeList nodes = elem.getChildNodes();
		
		for(int i = 0; i < nodes.getLength(); i++) {
			 if(nodes.item(i) instanceof Element)
				 helpElem = (Element) nodes.item(i);
			 else 
				 continue;
			 
			 if(helpElem.getNodeName().equals("destination")) {
				destination = helpElem.getAttribute("href").split("~")[1];
				if(helpElem.hasAttribute("pos")) insertDirection = helpElem.getAttribute("pos");
			} else if(helpElem.getNodeName().equals("old")) {
				old_text = helpElem.getAttribute("href").substring(1);
			} else if(helpElem.getNodeName().equals("new")) {
				new_text = helpElem.getAttribute("href").substring(1);
			}
		}
		
		ChangeType changeType = ChangeType.INSERTION;
		if(operation.equals("substitution")) {
			changeType = ChangeType.SUBSTITUTION;
		} else if(operation.equals("repeal")) {
			changeType = ChangeType.REPEAL;
		} else if(operation.equals("insertion")) {
			changeType = ChangeType.INSERTION;
		} else {
			System.out.println("Wrong input data.");
		}
		
		if(!old_text.equals("")) {
			helpElem = this.getElementWithEid(old_text);
			if(helpElem == null) {
				old_text = "";
			}
			else {
				old_text = helpElem.getTextContent().trim();
			}
		}

		Change retVal = new Change(destination,insertDirection,changeType,old_text,"",null,false);
		
		helpElem = this.getElementWithEid(new_text);
		if(helpElem.getNodeName().equals("quotedText")) {
			retVal.setNewValue(helpElem.getTextContent().trim());
			retVal.setText(true);
		}else {
			retVal.setNewValue("");
			retVal.setText(false);
			retVal.setNewValueStruct((Element) helpElem.cloneNode(true));
		}
		
		
		return retVal;
	}
	
	private Element getElementWithEid(String eId) {
		for (Element element : allRegisteredElements) {
			if(element.getAttribute("eId").equals(eId)) {
				return element;
			}
		}
		return null;
	}
	
	private void loadAllElements() {
		this.clearAllElements();
		loadChildElements(document.getDocumentElement());
	}
	
	private void clearAllElements() {
		this.allRegisteredElements = new ArrayList<Element>();
	}
	
	private void loadChildElements(Node node) {
		NodeList children = node.getChildNodes();
		if(children.getLength() == 0) {
			return;
		} else {
			for(int i = 0; i < children.getLength(); i++) {
				if(!(children.item(i) instanceof Element)) continue;

				if( ((Element)children.item(i)).hasAttribute("eId")) {
					this.allRegisteredElements.add( (Element)children.item(i));
				}
			}
		}
		for(int i = 0; i < children.getLength(); i++) {
			if(!(children.item(i) instanceof Element)) continue;
			loadChildElements((Element)children.item(i));
		}
	}
	
	public void applyChanges(ArrayList<Change> changes) {
		for (Change change : changes) {
			if(change.getType() == ChangeType.INSERTION) {
				insertIntoXML(change);
			} else if(change.getType() == ChangeType.SUBSTITUTION) {
				updateIntoXML(change);
			}
		}
		
		System.out.println("All changes applied");
		
		Transformer transformer = null;
		try {
			transformer = transformerFactory.newTransformer();
		} catch (TransformerConfigurationException e) {
			System.out.println("Something went wrong with transformer");
			//e.printStackTrace();
		}

		transformer.setOutputProperty("{http://xml.apache.org/xalan}indent-amount", "2");
		transformer.setOutputProperty(OutputKeys.INDENT, "yes");
		DOMSource source = new DOMSource(document);

		String newFileName = filePath.substring(0, filePath.length() - 4);
		newFileName = newFileName + "-v1.xml";
		StreamResult result = new StreamResult(new File(newFileName));

		try {
			transformer.transform(source, result);
		} catch (TransformerException e) {
			System.out.println("Something went wrong with transformation");
		}
	}
	
	private void updateIntoXML(Change change) {
		Element destinationElement = null;
		ArrayList<Node> nodes = new ArrayList<Node>();
		NodeList helpList = null;
		Node parent = null, nextSibling = null;
		
		for (Element element : allRegisteredElements) {
			if(element.getAttribute("eId").equals(change.getDestination())) {
				destinationElement = element;
				break;
			}
		}
		
		if(change.getOldValue().equals("")) {
			if(change.getNewValue().equals("")) {
				parent= destinationElement.getParentNode();
				
				nextSibling = destinationElement.getNextSibling();
				while(true) {

					if(nextSibling == null) {
						break;
					}
					
					nextSibling = nextSibling.getNextSibling();
					if(!(nextSibling instanceof Element)) continue;
					
					break;
				}
				
				parent.removeChild(destinationElement);
				
				if(nextSibling == null) {
					helpList = change.getNewValueStruct().getChildNodes();
					for(int i=0; i< helpList.getLength(); i++) {
						if(!(helpList.item(i) instanceof Element)) continue;
						parent.appendChild(document.adoptNode(helpList.item(i)));
					}
				}else {
					helpList = change.getNewValueStruct().getChildNodes();
					for(int i=0; i< helpList.getLength(); i++) {
						if(!(helpList.item(i) instanceof Element)) continue;
						parent.insertBefore(document.adoptNode(helpList.item(i)),nextSibling);
					}
				}
				
			}else {
				destinationElement.setTextContent(change.getNewValue());
			}
		}else {
			String text = destinationElement.getTextContent();
			
			text = text.replace(change.getOldValue().toString(), change.getNewValue().toString());
			destinationElement.setTextContent(text);
		}
		
	}
	
	private void insertIntoXML(Change change) {
		Element destinationElement = null;
		
		for (Element element : allRegisteredElements) {
			if(element.getAttribute("eId").equals(change.getDestination())) {
				destinationElement = element;
				break;
			}
		}
		
		if(destinationElement == null) {
			System.out.println("Element with given eId not found");
			return;
		}
		
		NodeList childNodes = change.getNewValueStruct().getChildNodes();
		Node helpNode = null;
		for (int i =0 ; i < childNodes.getLength(); i++) {
			if(childNodes.item(i) instanceof Element)
				if(change.getInsertDirection().equals("before")) {
					destinationElement.getParentNode().insertBefore(document.adoptNode(childNodes.item(i)), destinationElement);
				}else {
					
					helpNode = destinationElement.getNextSibling();
					while(true) {

						if(helpNode == null) {
							destinationElement.getParentNode().appendChild(document.adoptNode(childNodes.item(i)));
							break;
						}
						
						helpNode = helpNode.getNextSibling();
						if(!(helpNode instanceof Element)) continue;
						
						destinationElement.getParentNode().insertBefore(document.adoptNode(childNodes.item(i)), helpNode);
						break;
					}
					
				}
		}
		
	}
	
	public void compareToOtherDoc(String filePath) {
		NodeList firstDocument= document.getElementsByTagName("article");
		this.buildDocument(filePath);
		NodeList secondDocument = document.getElementsByTagName("article");
		String art_id ="";

		ComparisonType answer;
		
		for(int i=0, j=0;i< firstDocument.getLength(); i++, j++) {
			answer = compareArticle((Element)firstDocument.item(i),(Element)secondDocument.item(j));
			art_id = ((Element)firstDocument.item(i)).getAttribute("eId");
			
			if(answer == ComparisonType.NEW_ARTICLE) {
				i--;
			}else {
				System.out.println("Article[" + art_id + "] -> " + answer);
			}
		}
	}
	
	
	private ComparisonType compareArticle(Element art1,Element art2) {
		Element num1 = getChildNodesByTagname(art1, "num").get(0);
		Element num2 = getChildNodesByTagname(art2, "num").get(0);
		
		if(num1 == null || num2 == null) {
			System.out.println("Wrong format of input data.");
			return ComparisonType.INVALID_FORMAT;
		}
		
		if(!num1.getTextContent().equals(num2.getTextContent())) {
			System.out.println("There is new article added --> " + num2.getTextContent());
			return ComparisonType.NEW_ARTICLE;
		}
		
		Element heading1 = getChildNodesByTagname(art1, "heading").get(0);
		Element heading2 = getChildNodesByTagname(art2, "heading").get(0);

		if(heading1 == null || heading2 == null) {
			System.out.println("Wrong format of input data.");
			return ComparisonType.INVALID_FORMAT;
		}

		Element content1 = getChildNodesByTagname(art1, "content").get(0);
		Element content2 = getChildNodesByTagname(art2, "content").get(0);

		if(content1 == null || content2 == null) {
			System.out.println("Wrong format of input data.");
			return ComparisonType.INVALID_FORMAT;
		}
		
		if(!compareContent(content1,content2)) {
			return ComparisonType.CHANGED_ARTICLE;
		}
		
		return ComparisonType.SAME_ARTICLE;
	}
	
	// Needs to register new pharagraphs
	private boolean compareContent(Element content1, Element content2) {
		ArrayList<Element> pharagraphs1 = getChildNodesByTagname(content1, "p");
		ArrayList<Element> pharagraphs2 = getChildNodesByTagname(content2, "p");
		boolean retVal = true;
		int k=0;																				//eId pairs found
		
		for(int i=0; i<pharagraphs1.size(); i++) {
			for(int j=0; j<pharagraphs2.size();j++) {
				
				// When you find maching eId <p>, compare it
				if(pharagraphs1.get(i).getAttribute("eId").equals(pharagraphs2.get(j).getAttribute("eId"))) {
					k++;
					
					if(!compareParagraph(pharagraphs1.get(i),pharagraphs2.get(j))) {
						retVal = false;
					}
				}
			}
		}
				
		ArrayList<Element> blockList1 = getChildNodesByTagname(content1, "blockList");
		ArrayList<Element> blockList2 = getChildNodesByTagname(content2, "blockList");

		if(blockList1.get(0) != null) {
			if(!compareBlockList(blockList1.get(0),blockList2.get(0))){
				retVal = false;
			}
		}
		
		return retVal;
	}
	
	private boolean compareParagraph(Element par1, Element par2) {
		
		if(par1.getTextContent().equals(par2.getTextContent()))
			return true;
		else {
			return false;
		}
	}
	
	private boolean compareBlockList(Element blockList1,Element blockList2) {
		ArrayList<Element> items1 = getChildNodesByTagname(blockList1, "item");
		ArrayList<Element> items2 = getChildNodesByTagname(blockList2, "item");
				
		boolean retVal = true;
		int k=0;					// eId pairds found

		for(int i=0; i<items1.size(); i++) {
			for(int j=0; j<items1.size();j++) {
				
				// When you find maching eId <p>, compare it
				if(items1.get(i).getAttribute("eId").equals(items1.get(i).getAttribute("eId"))) {
					k++;
					if(!compareItem(items1.get(i),items1.get(i))) {
						retVal = false;
					}
				}
			}
		}

		return true;
	}
	
	private boolean compareItem(Element item1,Element item2) {
		boolean retVal = true;
		
		Element blockList1 = getChildNodesByTagname(item1, "blockList").get(0);
		Element blockList2 = getChildNodesByTagname(item2, "blockList").get(0);
		
		if(blockList1 != null) {
			retVal = compareBlockList(blockList1, blockList2);
		}
		
		Element num1 = getChildNodesByTagname(item1, "num").get(0);
		Element num2 = getChildNodesByTagname(item2, "num").get(0);
		
		if(!num1.getTextContent().equals(num2.getTextContent())) {
			retVal = false;
			System.out.println("Numeration of item not same [" + item1.getAttribute("eId") + "]");
		}
		
		Element p1 = getChildNodesByTagname(item1, "p").get(0);
		Element p2 = getChildNodesByTagname(item2, "p").get(0);
		
		if(!p1.getTextContent().equals(p2.getTextContent())) {
			retVal = false;
			System.out.println("Context of item not same [" + item1.getAttribute("eId") + "]");
		}

		return true;
	}
	
	private ArrayList<Element> getChildNodesByTagname(Node elem, String tagName) {
		ArrayList<Element> retVal = new ArrayList<Element>();
		Node helpNode = null;
		for(int i=0; i<elem.getChildNodes().getLength(); i++) {
			helpNode = elem.getChildNodes().item(i);
			if(helpNode instanceof Element) {
				if(((Element)helpNode).getTagName().equals(tagName)) {
					retVal.add((Element) helpNode);
				}
			}
		}
		
		if(retVal.size() == 0) {
			retVal.add(null);
		}
		
		return retVal;
	}

	@Override
	public void error(SAXParseException err) throws SAXParseException {
		throw err;
	}

	@Override
	public void fatalError(SAXParseException err) throws SAXException {
		throw err;
	}
	
	@Override
    public void warning(SAXParseException err) throws SAXParseException {
    	System.out.println("[WARN] Warning, line: " + err.getLineNumber() + ", uri: " + err.getSystemId());
        System.out.println("[WARN] " + err.getMessage());
    }

}
