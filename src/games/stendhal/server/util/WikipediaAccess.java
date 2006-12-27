package games.stendhal.server.util;

import games.stendhal.client.update.HttpClient;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Gets the first text paragraph from the specified Wikipedia article using
 * the MediaWiki bot api.
 *
 * @author hendrik
 */
public class WikipediaAccess extends DefaultHandler {
	private StringBuilder text = new StringBuilder();
	/** used by the parser to detect the right tag */
	private boolean isContent = false;
	/** was the parsing completed */
	private boolean finished = false;
	private String error = null;

	@Override
	public void startElement(String namespaceURI, String lName, String qName, Attributes attrs) {
		isContent = qName.equals("content");
	}

	@Override
	public void characters(char[] ch, int start, int length) throws SAXException {
		if (isContent) {
			text.append(ch, start, length);
		}
	}

	/**
	 * returns the unparsed text
	 *
	 * @return content
	 */
	public String getText() {
		return text.toString();
	}

	/**
	 * Gets the last error message
	 *
	 * @return error message or <code>null</code> in case no error occured
	 */
	public String getError() {
		return error;
	}

	/**
	 * Returns the first paragraph of the specified article without wiki code
	 *
	 * @return content
	 */
	public String getProcessedText() {
		String content = getText();
		if (content != null) {
			// remove image links
			content = content.replaceAll("\\[\\[[iI]mage:[^\\]]*\\]\\]", "");
			// remove comments (note reg exp is incorret)
			content = content.replaceAll("<!--[^>]*-->", "");
			// remove templates (note reg exp is incorret)
			content = content.replaceAll("\\{\\{[^\\}]*\\}\\}", "");
			// remove complex links
			content = content.replaceAll("\\[\\[[^\\]]*\\|", "");
			// remove simple links
			content = content.replaceAll("\\[\\[", "");
			content = content.replaceAll("\\]\\]", "");
			
			// ignore leading empty lines and spaces
			content = content.trim();
	
			// extract the first paragraph (ignoring very short once but oposing a max len)
			int size = content.length();
			int endOfFirstParagraph = content.indexOf("\n", 50);
			if (endOfFirstParagraph < 0) {
				endOfFirstParagraph = size;
			}
			content = content.substring(0, Math.min(endOfFirstParagraph, 1024));
		}
		return content;
	}

	/**
	 * starts the parsing of the specified article
	 *
	 * @param title
	 * @throws Exception in case of an unexpected error
	 */
	public void parse(String title) throws Exception {
		try {
			// look it up using the Wikipedia API
			HttpClient httpClient = new HttpClient("http://en.wikipedia.org/w/query.php?format=xml&titles=" + title + "&what=content");
			SAXParserFactory factory = SAXParserFactory.newInstance();
	
			// Parse the input
			SAXParser saxParser = factory.newSAXParser();
			saxParser.parse(httpClient.getInputStream(), this);
	
			// finished
			finished = true;
		} catch (Exception e) {
			finished = true;
			error = e.toString();
			throw e;
		}
	}

	/**
	 * Returns true when the xml response was completly parsed
	 *
	 * @return true if the parsing was completed, false otherwise
	 */
	public boolean isFinished() {
		return finished;
	}
}
