package perf;

import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;

import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.Node;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.SAXReader;
import org.dom4j.io.XMLWriter;

public class ConfigUtils {

	public static File config = new File("config.xml");
	public static File boardsInfo = new File("boardsInfo.xml");

	/** --config.xml配置-- * */
	public synchronized static String getCongByName(String confName) {

		String result = "";
		try {
			SAXReader saxReader = new SAXReader();
			Document document = saxReader.read(config);
			result = document.selectSingleNode("//root/" + confName).getText();

		} catch (Exception e) {
			System.out.println("解析" + confName + "异常" + e.getMessage());
		}
		return result;
	}

	public synchronized static void setConfigByName(String confName, String value) {

		try {
			SAXReader saxReader = new SAXReader();
			Document document = saxReader.read(config);
			document.selectSingleNode("//root/" + confName).setText(value);

			OutputFormat format = OutputFormat.createPrettyPrint();
			XMLWriter xmlWriter = new XMLWriter(new FileWriter(config), format);
			xmlWriter.write(document);
			xmlWriter.close();
		} catch (Exception e) {
			System.out.println("设置" + confName + "异常" + e.getMessage());
		}
	}

	/** --boardsInfo.xml配置-- **/
	public synchronized static ArrayList<Board> getBoards() {
		ArrayList<Board> boards = new ArrayList<Board>();
		try {
			SAXReader saxReader = new SAXReader();
			Document document = saxReader.read(boardsInfo);
			List<Node> nodes = document.selectNodes("//board");
			for (int i = 0; i < nodes.size(); i++) {
				Board board = new Board();
				Element element = (Element) nodes.get(i);

				board.desc = element.element("desc").getText();
				board.host = element.element("host").getText();
				board.user = element.element("user").getText();
				board.port = element.element("port").getText();
				board.pw = element.element("pw").getText();
				board.top_keyword_1 = element.element("top_keyword_1").getText();
				board.top_keyword_2 = element.element("top_keyword_2").getText();

				boards.add(board);
			}
		} catch (Exception e) {
			System.out.println("获取Boards异常");
		}
		return boards;
	}

	public synchronized static void addBoard() {

	}

	public synchronized static void delBoard() {

	}

	/** --测试-- **/
	public static void main(String[] args) {

		// System.out.println(getCongByName("prefix"));
		// setConfigByName("prefix", "SDV1");
		// System.out.println(getCongByName("prefix"));

		System.out.println(getBoards().get(0).desc);
	}
}