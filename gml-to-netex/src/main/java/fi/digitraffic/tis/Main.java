package fi.digitraffic.tis;

import jakarta.xml.bind.JAXBException;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;

public class Main {
    public static void main(String[] args) throws IOException, ParserConfigurationException, SAXException, JAXBException {
        if (args.length != 2) {
            System.out.println("Usage: java -jar gml-to-netex.jar <input file> <output file>");
            System.exit(1);
        }
        System.out.println("Input file: " + args[0]);
        System.out.println("Output file: " + args[1]);
        String inFile = args[0];
        String outFile = args[1];
        Gml2NetexConverter.convertGml2TopographicPlacesNetex(inFile, outFile);
        System.out.println("Finished converting GML to NeTEx.");
    }
}
