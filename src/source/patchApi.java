package source;

import java.io.File;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class patchApi {

	public static void main(String[] args) {
		
//		args =  new String[]{"patch","data/xml/ZakonOInformacionojBezbednosti1-v1.xml"
//				,"data/xml/ZakonOIzmeniIDopuni77-2019.xml"};
//		args =  new String[]{"compare","data/xml/ZakonOInformacionojBezbednosti1-v1.xml"
//				,"data/xml/ZakonOInformacionojBezbednosti1-v2.xml"};
//		args =  new String[]{"version","Zakon"
//				,"02-02-2018"};

		if(args.length == 0){
			System.out.println();
			System.out.println("You need to pass arguments.");
			System.out.println("Format : OPTION ARGS");
			System.out.println("Possible options: patch, compare, version");
			System.out.println();
			System.out.println("Patch pattern:");
			System.out.println("java -jar patchApi patch FILE1 FILE2");
			System.out.println("Patch example:");
			System.out.println("java -jar patchApi patch data/xml/ZakonOInformacionojBezbednosti.xml data/xml/ZakonOIzmeniIDopuni77-2019.xml");
			System.out.println();
			System.out.println("Compare patter:");
			System.out.println("java -jar patchApi compare FILE1 FILE2");
			System.out.println("Compare example:");
			System.out.println("java -jar patchApi compare data/xml/ZakonOInformacionojBezbednosti.xml data/xml/ZakonOInformacionojBezbednosti.xml");
			System.out.println();
			System.out.println("Version pattern:");
			System.out.println("[NOTE] Version checks all files in directory [data/xml] that include SEARCH_FILE_NAME");
			System.out.println("java -jar patchApi version SEARCH_FILE_NAME date");
			System.out.println("Compare example:");
			System.out.println("java -jar patchApi version ZakonOInformacionojBezbednosti 02-02-2018");
			System.out.println();
			return;
		}
		String requestType = "";
		String sourceFile = "", modificationFile = "";
		String destinationFile= "", dateStr = "";
		Date givenDate = null;
		File f;
		boolean argumentsValid = true;
		
		if(args.length == 3) {
			requestType = args[0];
			if(requestType.equals("patch")) {
				sourceFile = args[1];
				f= new File(sourceFile);
				if(!(f.exists() && f.isFile())) {
					System.out.println("[WARN] Wrong first argument (file does not exist)");
					argumentsValid = false;
				}

				modificationFile = args[2];
				f= new File(modificationFile);
				if(!(f.exists() && f.isFile())) {
					System.out.println("[WARN] Wrong second argument (file does not exist)");
					argumentsValid = false;
				}
				
			}else if(requestType.equals("compare")) {
				sourceFile = args[1];
				f= new File(sourceFile);
				if(!(f.exists() && f.isFile())) {
					System.out.println("[WARN] Wrong first argument (file does not exist)");
					argumentsValid = false;
				}

				destinationFile = args[2];
				f= new File(destinationFile);
				if(!(f.exists() && f.isFile())) {
					System.out.println("[WARN] Wrong second argument (file does not exist)");
					argumentsValid = false;
				}
			}else if(requestType.equals("version")) {
				sourceFile = args[1];
				f= new File("data/xml");
				
				String[] files = f.list();
				boolean fileExists = false;
				
				for(int i = 0; i < files.length; i++) {
					if(files[i].contains(sourceFile)) {
						fileExists = true;
					}
				}
				
				if(!fileExists) {
					System.out.println("[WARN] There is no file in directory [data/xml] that contains given file name");
					argumentsValid = false;
				}

				dateStr = args[2];
				
				SimpleDateFormat formatter = new SimpleDateFormat("dd-MM-yyyy");
				try {
					givenDate = formatter.parse(dateStr);
				} catch (ParseException e) {
					System.out.println("[WARN] Invalid date format. Must be dd-mm-yyyy");
					argumentsValid = false;
				}
			}else {
				System.out.println("Invalid option argument");
				argumentsValid = false;
			}
		}else {
			System.out.println("Invalid input, 3 arguments required.");
			argumentsValid = false;
		}

		if(argumentsValid) {
			XmlRWUtil xmlrw = new XmlRWUtil();

			if(requestType.equals("patch")) {
				System.out.println(sourceFile);
				System.out.println(modificationFile);
				xmlrw.applyPatch(sourceFile, modificationFile);			
			} else if(requestType.equals("compare")) {
				xmlrw.compareDocuments(sourceFile,destinationFile);
			} else if(requestType.equals("version")) {
				xmlrw.showActiveDocument(sourceFile,givenDate);
			}
		}
	
	}

}
