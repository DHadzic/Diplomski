package source;

import java.util.ArrayList;

import util.Change;

public class patchApi {

	public static void main(String[] args) {
		
		XmlRWUtil xmlrw = new XmlRWUtil("data/xml/ZakonOIzmeniIDopuni94-2017.xml");
		
		ArrayList<Change> changes = xmlrw.getChanges();
		
		for (Change change : changes) {
			System.out.println(change);
		}
		
		xmlrw = new XmlRWUtil("data/xml/ZakonOInformacionojBezbednosti1.xml");
		
		xmlrw.applyChanges(changes);
		
		xmlrw = new XmlRWUtil("data/xml/ZakonOInformacionojBezbednosti1.xml");
		xmlrw.compareToOtherDoc("data/xml/ZakonOInformacionojBezbednosti1-v1.xml");

}

}
