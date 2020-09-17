package source;

import java.util.ArrayList;

import util.Change;

public class patchApi {

	public static void main(String[] args) {
		
		System.out.println("[INFO] Loading doc: data/xml/ZakonOIzmeniIDopuni94-2017.xml ..");
		
		XmlRWUtil xmlrw = new XmlRWUtil("data/xml/ZakonOIzmeniIDopuni94-2017.xml");
		
		ArrayList<Change> changes = xmlrw.getChanges();
		
		System.out.println();
		System.out.println("[INFO] Changes read from file:");
		
		for (Change change : changes) {
			System.out.println(change);
		}
		System.out.println();
		
		System.out.println("[INFO] Loading doc: data/xml/ZakonOInformacionojBezbednosti1.xml");

		xmlrw = new XmlRWUtil("data/xml/ZakonOInformacionojBezbednosti1.xml");
		
		xmlrw.applyChanges(changes);
		
		System.out.println();
		
		System.out.println("[INFO] Loading doc: data/xml/ZakonOInformacionojBezbednosti1.xml");

		xmlrw = new XmlRWUtil("data/xml/ZakonOInformacionojBezbednosti1.xml");
		System.out.println("[INFO] Loading doc: data/xml/ZakonOInformacionojBezbednosti1-v1.xml");

		xmlrw.compareToOtherDoc("data/xml/ZakonOInformacionojBezbednosti1-v1.xml");

}

}
