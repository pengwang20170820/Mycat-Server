package io.mycat.server.global;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.SAXReader;
import org.dom4j.io.XMLWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.mycat.MycatServer;
import io.mycat.MycatStartup;
import io.mycat.backend.datasource.PhysicalDBNode;
import io.mycat.config.MycatConfig;
import io.mycat.config.model.SchemaConfig;
import io.mycat.config.model.TableConfig;
import io.mycat.server.global.xml.model.CheckResult;
import io.mycat.server.global.xml.model.DataNode;
import io.mycat.server.global.xml.model.Table;

public class GlobalCheckUtil {
	
	
	 private static final Logger LOGGER = LoggerFactory.getLogger(GlobalCheckUtil.class);
	
	private static GlobalCheckUtil instance=new GlobalCheckUtil();

	private static Map<String, TableConfig> globalTableMap = new ConcurrentHashMap<>();

	public static String filePath = GlobalCheckUtil.class.getClassLoader().getResource("").getFile()
			+ "/global_check.xml";

	private GlobalCheckUtil() {
	}
	
	
	public static GlobalCheckUtil getInstance() {
		return instance;
	}
	
	static {
		getGlobalTable();
	}

	private static void getGlobalTable() {
		MycatConfig config = MycatServer.getInstance().getConfig();
		Map<String, SchemaConfig> schemaMap = config.getSchemas();
		SchemaConfig schemaMconfig = null;
		for (String key : schemaMap.keySet()) {
			if (schemaMap.get(key) != null) {
				schemaMconfig = schemaMap.get(key);
				Map<String, TableConfig> tableMap = schemaMconfig.getTables();
				if (tableMap != null) {
					for (String k : tableMap.keySet()) {
						TableConfig table = tableMap.get(k);
						if (table != null && table.isGlobalTable()) {
							globalTableMap.put(table.getName().toUpperCase(), table);
						}
					}
				}
			}
		}
	}

	public void initXmlModel()  {

		File file = new File(filePath);
		CheckResult checkResult = CheckResult.getInstance();
		if (file.exists()) {

			SAXReader reader = new SAXReader();
			Document document=null;
			try {
				document = reader.read(new File(filePath));
			} catch (DocumentException e) {
				
				LOGGER.warn("读取全局检测文件失败");
			}
			Element root = document.getRootElement();
			
			List<Element> dataNodeEs=root.elements("dataNode");
			
			List<DataNode> dataNodes = new ArrayList<DataNode>();
			for(Element dataNodeE:dataNodeEs) {
				DataNode dataNode=new DataNode();
				dataNode.setName(dataNodeE.attribute("name").getText());
				
				
				List<Element> tableEs=dataNodeE.elements("table");
				
				List<Table> tables = new ArrayList<Table>();
			    for(Element tableE:tableEs) {
			    	Table table=new Table();
			    	table.setName(tableE.attribute("name").getText());
			    	table.setHashCode(new Long(tableE.getText()));
			    	tables.add(table);
			    }
			    dataNode.setTables(tables);
			    dataNodes.add(dataNode);
			}
			checkResult.setDataNodes(dataNodes);
			
		} else {
			MycatConfig config = MycatServer.getInstance().getConfig();
			Map<String, PhysicalDBNode> dbNodes = config.getDataNodes();

			List<DataNode> dataNodes = new ArrayList<DataNode>();
			for (String dataNodeName : dbNodes.keySet()) {
				DataNode dataNode = new DataNode();
				dataNode.setName(dataNodeName);
				List<Table> tables = new ArrayList<Table>();
				for (String tableName : globalTableMap.keySet()) {
					Table table = new Table();
					table.setName(tableName);
					table.setHashCode(0L);
					tables.add(table);
				}
				dataNode.setTables(tables);
				dataNodes.add(dataNode);
			}
			checkResult.setDataNodes(dataNodes);
		}
	}

	public void saveXmlFile() {

		CheckResult checkResult = CheckResult.getInstance();
		Document document = DocumentHelper.createDocument();
		Element root = document.addElement("check");

		for (DataNode dataNode : checkResult.getDataNodes()) {
			Element dataNodeE = root.addElement("dataNode");
			dataNodeE.addAttribute("name", dataNode.getName());

			for (Table table : dataNode.getTables()) {
				Element tableE = dataNodeE.addElement("table");
				tableE.addAttribute("name", table.getName());
				tableE.addText(table.getHashCode().toString());
			}
		}
		OutputFormat format = OutputFormat.createPrettyPrint();
		try {
			XMLWriter writer = new XMLWriter(new FileOutputStream(filePath), format);
			writer.setEscapeText(false);
			writer.write(document);
			writer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static void main(String[] args) throws FileNotFoundException, DocumentException {
		GlobalCheckUtil check=new GlobalCheckUtil();
		check.initXmlModel();
		check.saveXmlFile();

	}

}
