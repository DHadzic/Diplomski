package source;

import static org.apache.xerces.jaxp.JAXPConstants.JAXP_SCHEMA_LANGUAGE;
import static org.apache.xerces.jaxp.JAXPConstants.W3C_XML_SCHEMA;

import java.io.File;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import util.Change;
import util.ChangeType;
import util.ComparisonType;
import util.ElementPair;

public class XmlRWUtil implements ErrorHandler{

	private ArrayList<Element> allRegisteredElements;
	private Document document;
	private String filePath;

	private static DocumentBuilderFactory factory;
	private static TransformerFactory transformerFactory;
	static {
		factory = DocumentBuilderFactory.newInstance();
		factory.setValidating(true);
		factory.setNamespaceAware(true);
		factory.setIgnoringComments(true);
		factory.setIgnoringElementContentWhitespace(true);
		factory.setAttribute(JAXP_SCHEMA_LANGUAGE, W3C_XML_SCHEMA);
	}
	static {
		factory = DocumentBuilderFactory.newInstance();
		transformerFactory = TransformerFactory.newInstance();
	}

	public XmlRWUtil() {
	}
	
	private Document buildDocument(String filePath) {

		try {
			DocumentBuilder builder = factory.newDocumentBuilder();
			builder.setErrorHandler(this);
			
			Document tempDoc = builder.parse(new File(filePath)); 

			if (tempDoc != null)
				System.out.println("[INFO] File parsed with no errors.");
			else
				System.out.println("[WARN] Document is null.");
			
			return tempDoc;
			
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
		return null;
	}
	
	public void applyPatch(String sourceFile,String modificationFile) {
		System.out.println("[INFO] Loading doc: " + modificationFile);
		document = this.buildDocument(modificationFile);
		
		
		
		if(document == null) return;
		
		this.filePath = sourceFile;
		this.loadAllElements();

		// Get date element from first document to insert into lifecycle of second document
		Element dateElem = this.getElementWithEid("doc-date");		
		ArrayList<Change> changes = this.getChanges();
		
		System.out.println();
		System.out.println("[INFO] Changes read from file:");
		
		for (Change change : changes) {
			System.out.println(change);
		}
		System.out.println();

		
		System.out.println("[INFO] Loading doc: " + sourceFile);
		document = this.buildDocument(sourceFile);

		if(document == null) return;
		
		
		// Apply all changes
		this.loadAllElements();
		this.applyChanges(changes);
		
		this.updateLifecycle(dateElem);
		
		// Write to file
		Transformer transformer = null;
		try {
			transformer = transformerFactory.newTransformer();
		} catch (TransformerConfigurationException e) {
			System.out.println("[WARN] Something went wrong with transformer");
		}

		transformer.setOutputProperty("{http://xml.apache.org/xalan}indent-amount", "2");
		transformer.setOutputProperty(OutputKeys.INDENT, "yes");
		DOMSource source = new DOMSource(document);
		
		// Increment version of file if it exists
		String newFileName = "";
		if(filePath.contains("-v")) {
			int start_index = filePath.indexOf("-v");
			int end_index = filePath.indexOf(".xml");
			
			String old_verison = filePath.substring(start_index, end_index);
			int new_index = Integer.parseInt(old_verison.substring(2));
			new_index = new_index+1;
			
			String new_version = "-v" + new_index;
			
			newFileName = filePath.replace(old_verison, new_version);
		}else {
			newFileName = filePath.substring(0, filePath.length() - 4);
			newFileName = newFileName + "-v1.xml";
		}
		
		StreamResult result = new StreamResult(new File(newFileName));

		try {
			transformer.transform(source, result);
		} catch (TransformerException e) {
			System.out.println("[WARN] Something went wrong with transformation");
		}
		
	}
	
	private ArrayList<Change> getChanges() {
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
		
		if(new_text.equals("")) {
			return retVal;
		}
		
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
	
	private void applyChanges(ArrayList<Change> changes) {
		for (Change change : changes) {
			if(change.getType() == ChangeType.INSERTION) {
				insertIntoXML(change);
			} else if(change.getType() == ChangeType.SUBSTITUTION) {
				updateIntoXML(change);
			} else if(change.getType() == ChangeType.REPEAL) {
				removeFromXML(change);
			}
			this.loadAllElements();
		}
		
		System.out.println("[INFO] All changes applied");
		
	}
	
	private void updateLifecycle(Element dateElement) {
		Element lifecycleElem = (Element) document.getElementsByTagName("lifecycle").item(0);
		
		Element firstEventRef = (Element) lifecycleElem.getElementsByTagName("eventRef").item(0);
		
		Element insertionElem = (Element) firstEventRef.cloneNode(true);
		
		Attr dateAttr = insertionElem.getAttributeNode("date");
		Attr typeAttr = insertionElem.getAttributeNode("type");
		
		dateAttr.setNodeValue(dateElement.getAttribute("date"));
		typeAttr.setNodeValue("amendment");
		
		lifecycleElem.appendChild(insertionElem);
		
		Element docDate = this.getElementWithEid("doc-date");
		Text newText = document.createTextNode("," + dateElement.getAttribute("title") +" oд "+ dateElement.getTextContent());
		
		docDate.getParentNode().appendChild(newText);
		
	}
	
	private void updateIntoXML(Change change) {
		Element destinationElement = null;
		NodeList helpList = null;
		Node parent = null, nextSibling = null;
		boolean eIdFound = false;
		
		for (Element element : allRegisteredElements) {
			if(element.getAttribute("eId").equals(change.getDestination())) {
				destinationElement = element;
				eIdFound = true;
				break;
			}
		}
		
		if(!eIdFound) {
			System.out.println("[WARN] Element with eId < " + change.getDestination() + " > declared in Change not found.");
			return;
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
			System.out.println("[WARN] Element with given eId not found");
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
	
	private void removeFromXML(Change change) {
		Element destinationElement = null;
		
		for (Element element : allRegisteredElements) {
			if(element.getAttribute("eId").equals(change.getDestination())) {
				destinationElement = element;
				break;
			}
		}
		
		if(destinationElement == null) {
			System.out.println("[WARN] Element with given eId not found <" + change.getDestination() + ">");
			return;
		}
		
		if(change.getOldValue().equals("")) {
			destinationElement.getParentNode().removeChild(destinationElement);
		}else {
			String text = destinationElement.getTextContent();
			
			text = text.replace(change.getOldValue().toString(), "".toString());
			destinationElement.setTextContent(text);
		}
	}
	
	public void compareDocuments(String sourceFile,String destinationFile) {

		this.document = buildDocument(sourceFile);
		NodeList firstDocument= document.getElementsByTagName("article");
		this.document = buildDocument(destinationFile);
		NodeList secondDocument = document.getElementsByTagName("article");
		String art_id ="";
		
		ArrayList<Element> firstDocumentArray = castNodeListToElemList(firstDocument);
		ArrayList<Element> secondDocumentArray = castNodeListToElemList(secondDocument);
		
		ArrayList<ElementPair> commonArticles = this.getElementsSameEID(firstDocumentArray, secondDocumentArray);
		ArrayList<Element> removedArticles = this.getElementsDiffEID(firstDocumentArray, secondDocumentArray);
		ArrayList<Element> newArticles = this.getElementsDiffEID(secondDocumentArray, firstDocumentArray);

		ComparisonType answer;
		
		for (ElementPair pair : commonArticles) {
			System.out.println();
			System.out.println("[INFO] Comparing next article:");
			
			answer = compareArticle(pair.getFirst(),pair.getSecond());
			art_id = pair.getFirst().getAttribute("eId");
			System.out.println("[INFO] Article  with eId <" + art_id + "> : " + answer);
		}
		
		if(removedArticles.size()>0) {
			System.out.println("");
			System.out.println("[INFO] Removed articles:");
		}

		for(int i =0; i < removedArticles.size();i++) {
			System.out.println("[INFO] Article  with eId <" + removedArticles.get(i).getAttribute("eId") + "> has been removed");			
		}

		if(newArticles.size()>0) {
			System.out.println("");
			System.out.println("[INFO] New articles:");
		}

		for(int i =0; i < newArticles.size();i++) {
			System.out.println("[INFO] Article  with eId <" + newArticles.get(i).getAttribute("eId") + "> has been added");			
		}
	}
	
	private ArrayList<ElementPair> getElementsSameEID(ArrayList<Element> list1, ArrayList<Element> list2){
		ArrayList<ElementPair> retVal = new ArrayList<ElementPair>();
		String eId1 = "", eId2 = "";
		
		for (Element element1 : list1) {
			eId1 = element1.getAttribute("eId");
			for (Element element2 : list2) {
				eId2 = element2.getAttribute("eId");
				
				if(eId1.equals(eId2)) {
					retVal.add(new ElementPair(element1,element2));
					break;
				}
			}
		}
		
		return retVal;
	}
	
	// Returns elements that are non-existent in list2 by eId comparison
	private ArrayList<Element> getElementsDiffEID(ArrayList<Element> list1,ArrayList<Element> list2){
		ArrayList<Element> retVal = new ArrayList<Element>();
		String eId1 = "", eId2 = "";
		boolean found = false;
		
		for (Element element1 : list1) {
			eId1 = element1.getAttribute("eId");
			for (Element element2 : list2) {
				eId2 = element2.getAttribute("eId");
				if(eId1.equals(eId2)) {
					found = true;
					break;
				}				
			}
			if(!found) {
				retVal.add(element1);
			}
			found = false;
		}
		
		return retVal;
	}
	
	private ArrayList<Element> castNodeListToElemList(NodeList list){
		ArrayList<Element> retVal = new ArrayList<>();
		for(int i=0;i< list.getLength(); i++) {
			retVal.add((Element)list.item(i));
		}
		return retVal;
	}
	
	private ComparisonType compareArticle(Element art1,Element art2) {
		ComparisonType retVal = ComparisonType.SAME_ARTICLE;
		Element num1 = getChildNodesByTagname(art1, "num").get(0);
		Element num2 = getChildNodesByTagname(art2, "num").get(0);
		
		if(num1 == null || num2 == null) {
			System.out.println("[INFO] Wrong format of input data. Missing numeration at article.");
			return ComparisonType.INVALID_FORMAT;
		}
		
		Element heading1 = getChildNodesByTagname(art1, "heading").get(0);
		Element heading2 = getChildNodesByTagname(art2, "heading").get(0);
		
		if(heading1 != null && heading2 != null) {
			if(!(heading1.getTextContent()).equals(heading2.getTextContent())) {
				System.out.println("[INFO] Heading changed");
				retVal = ComparisonType.CHANGED_ARTICLE;
			}
		}else if(heading2 != null && heading1 == null) {
			System.out.println("[INFO] Heading added");
			retVal = ComparisonType.CHANGED_ARTICLE;
		}else if(heading2 == null && heading1 != null) {
			System.out.println("[INFO] Heading removed");
			retVal = ComparisonType.CHANGED_ARTICLE;
		}

		ArrayList<Element> paragraphs1 = getChildNodesByTagname(art1, "paragraph");
		ArrayList<Element> paragraphs2 = getChildNodesByTagname(art2, "paragraph");

		ArrayList<ElementPair> commonPragraphs = this.getElementsSameEID(paragraphs1, paragraphs2);
		ArrayList<Element> removedParagraphs = this.getElementsDiffEID(paragraphs1, paragraphs2);
		ArrayList<Element> newParagraphs = this.getElementsDiffEID(paragraphs2, paragraphs1);

		for (ElementPair pair : commonPragraphs) {
			if(!compareParagraph(pair.getFirst(),pair.getSecond())) {
				retVal = ComparisonType.CHANGED_ARTICLE;
			}
		}

		for(int i =0; i < removedParagraphs.size();i++) {
			System.out.println("[INFO] Point  with eId < " + removedParagraphs.get(i).getAttribute("eId") + " > has been removed");			
		}

		for(int i =0; i < newParagraphs.size();i++) {
			System.out.println("[INFO] Point  with eId < " + newParagraphs.get(i).getAttribute("eId") + " > has been added");			
		}
		
		return retVal;
	}
	
	private boolean compareParagraph(Element paragraph1, Element paragraph2) {
		Element content1 = getChildNodesByTagname(paragraph1, "content").get(0);
		Element content2 = getChildNodesByTagname(paragraph2, "content").get(0);
		boolean retVal = true;
		
		if(content1 == null && content2 == null) {
			Element intro1 = getChildNodesByTagname(paragraph1, "intro").get(0);
			Element intro2 = getChildNodesByTagname(paragraph2, "intro").get(0);
			Element p1 = getChildNodesByTagname(intro1, "p").get(0);
			Element p2 = getChildNodesByTagname(intro2, "p").get(0);
			
			if(!(p1.getTextContent().equals(p2.getTextContent()))) {
				System.out.println("[INFO] Intro with eId < " + p1.getAttribute("eId") + " > has been changed");
				retVal = false;
			}

			ArrayList<Element> points1 = getChildNodesByTagname(paragraph1, "point");
			ArrayList<Element> points2 = getChildNodesByTagname(paragraph2, "point");

			ArrayList<ElementPair> commonPoints = this.getElementsSameEID(points1, points2);
			ArrayList<Element> removedPoints = this.getElementsDiffEID(points1, points2);
			ArrayList<Element> newPoints = this.getElementsDiffEID(points2, points1);

			for (ElementPair pair : commonPoints) {
				if(!comparePoint(pair.getFirst(),pair.getSecond())) {
					retVal = false;
				}
			}

			for(int i =0; i < removedPoints.size();i++) {
				System.out.println("[INFO] Point  with eId < " + removedPoints.get(i).getAttribute("eId") + " > has been removed");			
			}

			for(int i =0; i < newPoints.size();i++) {
				System.out.println("[INFO] Point  with eId < " + newPoints.get(i).getAttribute("eId") + " > has been added");			
			}
			
		}else if(content1 == null || content2 == null){
			
			System.out.println("[INFO] Structures changed");
			retVal = false;
		}else {
			Element p1 = getChildNodesByTagname(content1, "p").get(0);
			Element p2 = getChildNodesByTagname(content2, "p").get(0);
			
			if(!(p1.getTextContent().equals(p2.getTextContent()))) {
				System.out.println("[INFO] Paragraph content with id < " + p1.getAttribute("eId") + " > has been changed.");
				retVal = false;
			}
		}
		
		return retVal;
	}
	
	private boolean comparePoint(Element point1,Element point2) {
		boolean retVal = true;
		
		Element content1 = getChildNodesByTagname(point1, "content").get(0);
		Element content2 = getChildNodesByTagname(point2, "content").get(0);
		
		if(content1 == null && content2 == null) {

			Element intro1 = getChildNodesByTagname(point1, "intro").get(0);
			Element intro2 = getChildNodesByTagname(point2, "intro").get(0);
			Element p1 = getChildNodesByTagname(intro1, "p").get(0);
			Element p2 = getChildNodesByTagname(intro2, "p").get(0);
			
			if(!(p1.getTextContent().equals(p2.getTextContent()))) {
				System.out.println("[INFO] Intro with eId < " + p1.getAttribute("eId") + " > has been changed");
				retVal = false;
			}

			ArrayList<Element> subpoints1 = getChildNodesByTagname(point1, "hcontainer");
			ArrayList<Element> subpoints2 = getChildNodesByTagname(point2, "hcontainer");

			ArrayList<ElementPair> commonSubpoints = this.getElementsSameEID(subpoints1, subpoints2);
			ArrayList<Element> removedSubpoints = this.getElementsDiffEID(subpoints1, subpoints2);
			ArrayList<Element> newSubpoints = this.getElementsDiffEID(subpoints2, subpoints1);

			for (ElementPair pair : commonSubpoints) {
				if(!compareSubpoint(pair.getFirst(),pair.getSecond())) {
					System.out.println("[INFO] Subpoint with eId < " + pair.getFirst().getAttribute("eId") + " > has been changed");
					retVal = false;
				}
			}

			for(int i =0; i < removedSubpoints.size();i++) {
				System.out.println("[INFO] Subpoint with eId < " + removedSubpoints.get(i).getAttribute("eId") + " > has been removed");			
			}

			for(int i =0; i < newSubpoints.size();i++) {
				System.out.println("[INFO] Subpoint with eId < " + newSubpoints.get(i).getAttribute("eId") + " > has been added");			
			}
			
		}else if(content1 == null || content2 == null){
			
			System.out.println("[INFO] Structures changed");
			retVal = false;
		}else {
			
			Element p1 = getChildNodesByTagname(content1, "p").get(0);
			Element p2 = getChildNodesByTagname(content2, "p").get(0);

			if(!(p1.getTextContent().equals(p2.getTextContent()))) {
				System.out.println("[INFO] Paragraph content with id < " + p1.getAttribute("eId") + " > has been changed.");
				retVal = false;
			}
		}
			
		return retVal;
	}
	
	private boolean compareSubpoint(Element subpoint1,Element subpoint2) {
		boolean retVal = true;
		Element subp1,subp2;
		Element subcontent1,subcontent2;

		subcontent1 = getChildNodesByTagname(subpoint1, "content").get(0);
		subcontent2 = getChildNodesByTagname(subpoint2, "content").get(0);
		subp1 = getChildNodesByTagname(subcontent1, "p").get(0);
		subp2 = getChildNodesByTagname(subcontent2, "p").get(0);


		if(subcontent1 == null) {
			ArrayList<Element> childSubpoints1 = getChildNodesByTagname(subpoint1, "hcontainer");
			ArrayList<Element> childSubpoints2 = getChildNodesByTagname(subpoint2, "hcontainer");

			ArrayList<ElementPair> commonSubpoints = this.getElementsSameEID(childSubpoints1, childSubpoints2);
			ArrayList<Element> removedSubpoints = this.getElementsDiffEID(childSubpoints1, childSubpoints2);
			ArrayList<Element> newSubpoints = this.getElementsDiffEID(childSubpoints2, childSubpoints1);

			for (ElementPair pair : commonSubpoints) {
				if(!compareSubpoint(pair.getFirst(),pair.getSecond())) {
					System.out.println("[INFO] Subpoint with eId < " + subp1.getAttribute("eId") + " > has been changed");
					retVal = false;
				}
			}

			for(int i =0; i < removedSubpoints.size();i++) {
				System.out.println("[INFO] Subpoint with eId < " + removedSubpoints.get(i).getAttribute("eId") + " > has been removed");			
			}

			for(int i =0; i < newSubpoints.size();i++) {
				System.out.println("[INFO] Subpoint with eId < " + newSubpoints.get(i).getAttribute("eId") + " > has been added");			
			}
			
		}else {
			if(!(subp1.getTextContent().equals(subp2.getTextContent()))) {
				retVal = false;
			}
		}
		
		
		return retVal;
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
	
	public void showActiveDocument(String fileName, Date givenDate) {
		
		File directory = new File("data/xml");
		
		String[] files = directory.list();
		
		ArrayList<File> fittingFiles = new ArrayList<File>();
		
		for(int i = 0; i<files.length; i++) {
			if((new File("data/xml/" + files[i])).isFile()) {
				if(files[i].contains(fileName)) {
					fittingFiles.add(new File("data/xml/" + files[i]));
				}
			}
		}			
		
		if(fittingFiles.size() == 0) {
			System.out.println("[WARN] Didn't find any files containing < " + fileName + " >");
			return;
		}
		
		Document document;
		NodeList eventNodes;
		Date docDate;
		int eventRefCount;
		ArrayList<Date> fittingFilesDate = new ArrayList<Date>();
		SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
		
		ArrayList<File> removeFiles = new ArrayList<File>();

		for (File file : fittingFiles) {
			document = buildDocument(file.getAbsolutePath());
			
			eventNodes = document.getElementsByTagName("eventRef");
			eventRefCount = eventNodes.getLength();
			
			if(eventRefCount == 0) {
				removeFiles.add(file);
				continue;
			}
			
			try {
				docDate = formatter.parse(((Element)eventNodes.item(eventRefCount-1)).getAttribute("date"));
				fittingFilesDate.add(docDate);
			} catch (ParseException e) {
				System.out.println("[WARN] Counldn't parse date from <eventRef/>");
			}
		}
		
		for (File file : removeFiles) {
			fittingFiles.remove(file);
		}
		
		String fittingFile = "none";
		Date fittingDate = new Date(1);
		for(int i = 0; i < fittingFiles.size(); i++) {
			if(fittingDate.before(fittingFilesDate.get(i)) && givenDate.after(fittingFilesDate.get(i))) {
				fittingFile = fittingFiles.get(i).getAbsolutePath();
				fittingDate = fittingFilesDate.get(i);
			}
		}
		
		if(fittingFile.contains("none")) {
			System.out.println("[INFO] There is no valid document for given date");
			return;
		}
		
		displayDocument(fittingFile);
	}
	
	private void displayDocument(String fileName) {
		document = buildDocument(fileName);
		String text = document.getDocumentElement().getTextContent();
		
		text = text.replaceAll("\\s{2,}\\n", "\n");
		
		PrintStream consoleOut;
		try {
			consoleOut = new PrintStream(System.out, true, "UTF-8");
			consoleOut.println(text);
		} catch (UnsupportedEncodingException e) {
			System.out.println("Error");
			e.printStackTrace();
		}
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
