package com.example;

import java.sql.Blob;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.exoplatform.portal.config.model.ApplicationState;
import org.exoplatform.portal.config.model.ApplicationType;
import org.exoplatform.portal.config.model.ModelObject;
import org.exoplatform.portal.config.model.Page;
import org.exoplatform.portal.config.model.TransientApplicationState;
import org.exoplatform.portal.config.serialize.PortletApplication;
import org.exoplatform.portal.mop.management.binding.xml.PageMarshaller;
import org.exoplatform.portal.pom.data.ApplicationData;
import org.exoplatform.portal.pom.spi.portlet.Portlet;
import org.exoplatform.portal.pom.spi.portlet.Preference;

public class QuickSQL {

    public static Connection conn;

    public static int level = 0;

    public static void main(String[] args) throws Exception {
        Class.forName("oracle.jdbc.driver.OracleDriver");
        conn = DriverManager.getConnection("jdbc:oracle:thin:@localhost:49161:xe", "epp522jcr", "oracle");

        String query = "select * from JCR_SITEM where PARENT_ID = 'portal-system04c464c97f000001350d5a52c3c153a3'";
        //testQuery(query);
        treeView("portal-system04c464c97f000001350d5a52c3c153a3");
    }

    private static void treeView(String id) throws Exception {
        // TODO Auto-generated method stub
        String name = getSingleValueBySQL("select NAME from JCR_SITEM where ID = '" + id + "'");
        if (name == null) {
            log("<no name>");
        } else {
            log(trimNS(name));
        }
        level++;

        List<String> propIdList = getSingleValueListBySQL("select ID from JCR_SITEM where PARENT_ID = '" + id + "' and I_CLASS = '2'");
        for (String propId : propIdList) {
            String propName = getSingleValueBySQL("select NAME from JCR_SITEM where ID = '" + propId + "'");
            String propValue = getSingleValueBySQL("select DATA from JCR_SVALUE where PROPERTY_ID = '" + propId + "'");
            log(trimNS(propName) + " : " + propValue);
        }

        List<String> itemIdList = getSingleValueListBySQL("select ID from JCR_SITEM where PARENT_ID = '" + id + "' and I_CLASS = '1'");
        for (String itemId : itemIdList) {
            treeView(itemId);
        }

        level--;
    }

    private static void log(String message) {
        System.out.println(indent(level) + message);
    }

    private static String indent(int level) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < level; i++) {
            sb.append("  ");
        }
        return sb.toString();
    }

    private static String trimNS(String name) {
        return name.replace("[http://www.gatein.org/jcr/mop/1.0/]", "").replace("[http://www.jcp.org/jcr/1.0]", "")
                .replace("[http://www.gatein.org/jcr/gatein/1.0/]", "");
    }

    private static void testAttributes(String itemId) throws Exception {
        String attributesId = getItemIdByNameAndParentId("[http://www.gatein.org/jcr/mop/1.0/]attributes", itemId);

        Statement stmt = null;
        ResultSet rs = null;

        try {
            stmt = conn.createStatement();
            rs = stmt.executeQuery("select ID, NAME from JCR_SITEM where PARENT_ID = '" + attributesId
                    + "' and NAME like '[http://www.gatein.org/jcr/mop/1.0/]%'");
            while (rs.next()) {
                String attrId = (String) rs.getObject(1);
                String name = (String) rs.getObject(2);

                String attrValuePropId = getItemIdByNameAndParentId("[http://www.gatein.org/jcr/mop/1.0/]value", attrId);
                String attrValue = getSingleValueBySQL("select DATA from JCR_SVALUE where PROPERTY_ID = '" + attrValuePropId + "'");

                System.out.println("  " + name + " : " + attrValue);
            }

        } finally {
            rs.close();
            stmt.close();
        }
    }

    private static String getItemIdByNameAndParentId(String itemName, String parentId) throws Exception {
        String itemId = null;

        Statement stmt = null;
        ResultSet rs = null;

        try {
            stmt = conn.createStatement();
            rs = stmt.executeQuery("select ID, NAME from JCR_SITEM where PARENT_ID = '" + parentId + "'");
            boolean found = false;
            while (rs.next()) {
                String name = (String) rs.getObject(2);
                if (name.equals(itemName)) {
                    itemId = (String) rs.getObject(1);
                    found = true;
                    break;
                }
            }

            if (!found) {
                //                throw new RuntimeException(itemName + " not found for PARENT_ID = " + parentId);
                System.out.println(itemName + " not found for PARENT_ID = " + parentId);
                return null;
            }

            return itemId;

        } finally {
            rs.close();
            stmt.close();
        }

    }

    private static String getAttributeByAttrNameAndItemId(String attrName, String pageId) throws Exception {

        // Step 1 : get the attributes node of the page
        String attributesId = getItemIdByNameAndParentId("[http://www.gatein.org/jcr/mop/1.0/]attributes", pageId);

        // Step 2 : get the attribute node of the name
        String attrId = getItemIdByNameAndParentId(attrName, attributesId);

        // Step 3 : get the value node of the attribute
        String attrValuePropId = getItemIdByNameAndParentId("[http://www.gatein.org/jcr/mop/1.0/]value", attrId);

        // Step 4 : get the value from JCR_SVALUE
        String attrValue = getSingleValueBySQL("select DATA from JCR_SVALUE where PROPERTY_ID = '" + attrValuePropId + "'");

        return attrValue;
    }

    private static String getSinglePropertyByPropNameAndItemId(String propName, String itemId) throws Exception {

        // Step 1 : get the prop node of the name
        String propId = getItemIdByNameAndParentId(propName, itemId);

        // Step 2 : get the value from JCR_SVALUE
        String propValue = getSingleValueBySQL("select DATA from JCR_SVALUE where PROPERTY_ID = '" + propId + "'");

        return propValue;
    }

    private static String[] getMultiplePropertyByPropNameAndItemId(String propName, String itemId) throws Exception {

        // Step 1 : get the prop node of the name
        String propId = getItemIdByNameAndParentId(propName, itemId);

        // Step 2 : get the value from JCR_SVALUE
        String[] propValues = getMultipleValueBySQL("select DATA from JCR_SVALUE where PROPERTY_ID = '" + propId + "'");

        return propValues;
    }

    private static String getSingleValueBySQL(String query) throws Exception {
        Statement stmt = null;
        ResultSet rs = null;
        Object value = null;

        try {
            stmt = conn.createStatement();
            rs = stmt.executeQuery(query);

            if (!rs.next()) {
                //                System.out.println("No result for " + query);
                return null;
            }

            value = rs.getObject(1);

        } finally {
            rs.close();
            stmt.close();
        }

        if (value instanceof String) {
            return (String) value;
        } else if (value instanceof Blob) {
            return new String(((Blob) value).getBytes(1, (int) ((Blob) value).length()), "UTF-8");
        } else {
            throw new RuntimeException("Unexpected type : " + value.getClass());
        }
    }

    private static String[] getMultipleValueBySQL(String query) throws Exception {
        Statement stmt = null;
        ResultSet rs = null;
        List<Object> valueList = new ArrayList<Object>();

        try {
            stmt = conn.createStatement();
            rs = stmt.executeQuery(query);

            while (rs.next()) {
                valueList.add(rs.getObject(1));
            }

        } finally {
            rs.close();
            stmt.close();
        }

        if (valueList.size() == 0) {
            //                System.out.println("No result for " + query);
            return new String[0];
        }

        String[] values = new String[valueList.size()];

        for (int i = 0; i < values.length; i++) {

            Object value = valueList.get(i);

            if (value instanceof String) {
                values[i] = (String) value;
            } else if (value instanceof Blob) {
                values[i] = new String(((Blob) value).getBytes(1, (int) ((Blob) value).length()), "UTF-8");
            } else {
                throw new RuntimeException("Unexpected type : " + value.getClass());
            }
        }

        return values;
    }

    private static List<String> getSingleValueListBySQL(String query) throws Exception {

        //        System.out.println(query);

        List<String> idList = new ArrayList<String>();

        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery(query);
        while (rs.next()) {
            idList.add((String) rs.getObject(1));
        }

        rs.close();
        stmt.close();

        return idList;
    }

    private static void testQuery(String query) throws Exception {
        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery(query);
        ResultSetMetaData rsmd = rs.getMetaData();

        while (rs.next()) {
            for (int i = 1; i <= rsmd.getColumnCount(); i++) {
                System.out.println(rsmd.getColumnName(i) + " : " + rs.getObject(i));
            }
            System.out.println("--------------");
        }

        rs.close();
        stmt.close();

        return;
    }

}
