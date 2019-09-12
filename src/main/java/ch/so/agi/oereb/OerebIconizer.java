package ch.so.agi.oereb;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;
import javax.xml.bind.DatatypeConverter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OerebIconizer {
    Logger log = LoggerFactory.getLogger(this.getClass());
    
    /**
     * Gets all the symbols and the according type code from a QGIS 3 wms server.
     * 
     * @param configFileName GetStyles request url (= SLD file).
     * @param legendGraphicUrl GetLegendGraphic request url with the vendor specific parameters for single symbol support. 
     * The RULE parameter is added dynamically. The LAYER parameter must be included.
     * @throws Exception
     */
    public List<LegendEntry> getSymbolsQgis3Simple(String configFileName, String legendGraphicUrl) throws Exception {
        SymbolTypeCodeBuilder styleConfigBuilder = new Qgis3SimpleSymbolTypeCodeBuilder(configFileName, legendGraphicUrl);
        List<LegendEntry> legendEntries = styleConfigBuilder.build();
        return legendEntries;
    }
    
      
    /**
     * Saves symbols to disk. The type code is the file name.
     * 
     * @param legendEntries List with legend entries (type code, legend text and symbol).
     * @param directory Directory to save the symbols.
     * @throws IOException 
     * @throws Exception
     */
    public void saveSymbolsToDisk(List<LegendEntry> legendEntries, String directory) throws Exception {
        for (LegendEntry entry : legendEntries) {
            String typeCode = entry.getTypeCode();
            File symbolFile = Paths.get(directory, typeCode + ".png").toFile();
            ImageIO.write(entry.getSymbol(), "png", symbolFile);
        }
    }
    
    /**
     * Updates the symbols in a database table of the according type code.
     * 
     * @param typeCodeSymbols  Map with the type code and the symbols.
     * @param jdbcUrl JDBC url
     * @param dbUsr User name
     * @param dbPwd Password
     * @param dbQTable Qualified table name.
     * @param typeCodeAttrName Name of the type code attribute in the database.
     * @param typeCodeListAttrName Name of the type code list attribute in the database.
     * @param typeCodeListValue Name of the type code list.
     * @param symbolAttrName Name of the symbol attribute in the database.
     * @param legendTextAttrName Name of the legend text attribute in the database. If null it will not be updated.
     * @param useCommunalTypeCodes Highly Solothurn specific. If true the update query will substring type code in the where clause.
     * @throws Exception
     */
    public int updateSymbols(List<LegendEntry> legendEntries, String jdbcUrl, String dbUsr, String dbPwd, String dbQTable, String typeCodeAttrName, String typeCodeListAttrName, String typeCodeListValue, String symbolAttrName, String legendTextAttrName, boolean useCommunalTypeCodes) throws Exception {
        Connection conn = getDbConnection(jdbcUrl, dbUsr, dbPwd);
        
        //PreparedStatement pstmt = null;
        //String updateSql = "UPDATE " + dbQTable + " SET " + symbolAttrName + " = ?, " + legendTextAttrName + " = ? WHERE " + typeCodeAttrName + " = ?;";
        //log.info(updateSql);

        try {
            //pstmt = conn.prepareStatement(updateSql);
            int count = 0;
            for (LegendEntry entry : legendEntries) {
                log.info("TypeCode: " + entry.getTypeCode());
                log.info("LegendText: " + entry.getLegendText());
                log.info("Symbol: " + entry.getSymbol().toString());
                log.info("GeometryType: " + entry.getGeometryType());
                
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(entry.getSymbol(), "png", baos);
                byte[] symbolInByte = baos.toByteArray();
                String base64Encoded = Base64.getEncoder().encodeToString(symbolInByte);
                
                Statement stmt = conn.createStatement();
                
                // TODO: substring AND legendText update variant is missing (and others...)
                String sql = "";
                if (legendTextAttrName == null) {
                    if (useCommunalTypeCodes) {
                        sql = "UPDATE " + dbQTable + " SET " + symbolAttrName + " = decode('"+base64Encoded+"', 'base64') WHERE substring(" + typeCodeAttrName + ", 1, 3) = '"+entry.getTypeCode() +"' AND "+typeCodeListAttrName+" LIKE '"+typeCodeListValue+"%';";                                                   
                    } else {
                        sql = "UPDATE " + dbQTable + " SET " + symbolAttrName + " = decode('"+base64Encoded+"', 'base64') WHERE " + typeCodeAttrName + " = '"+entry.getTypeCode()+"' AND "+typeCodeListAttrName+" LIKE '"+typeCodeListValue+"%';";                     
                    }
                }
                else {
                    sql = "UPDATE " + dbQTable + " SET " + symbolAttrName + " = decode('"+base64Encoded+"', 'base64'), " + legendTextAttrName + " = '"+entry.getLegendText()+"' WHERE " + typeCodeAttrName + " = '"+entry.getTypeCode()+"' AND "+typeCodeListAttrName+" LIKE '"+typeCodeListValue+"%';";
                }
                log.info(sql);
                
                int c = stmt.executeUpdate(sql);
                count = count + c;
            }
            log.info("Number of updated records: " + String.valueOf(count));
            conn.close();
            
            return count;
        } catch (Exception e) {
            log.error(e.getMessage());
            throw new Exception(e);
        }
    }
            
    private Connection getDbConnection(String jdbcUrl, String dbUsr, String dbPwd) {
        Connection conn = null;

        try {
            conn = DriverManager.getConnection(jdbcUrl, dbUsr, dbPwd);
        } catch (SQLException e) {
            log.error(e.getMessage());
        }
        return conn;
    }
}
