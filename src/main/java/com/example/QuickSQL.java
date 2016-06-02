package com.example;

import java.sql.Blob;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class QuickSQL {

    public static Connection conn;

    public static int level = 0;

    public static void main(String[] args) throws Exception {
        Class.forName("oracle.jdbc.driver.OracleDriver");
        conn = DriverManager.getConnection("jdbc:oracle:thin:@localhost:49161:xe", "epp522jcr", "oracle");

        //String query = "select * from JCR_SITEM where PARENT_ID = 'portal-system04c464c97f000001350d5a52c3c153a3'";
        //testQuery(query);

        String userId = getUser("john");
        treeView(userId);
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

        List<String> propIdList = getSingleValueListBySQL("select ID from JCR_SITEM where PARENT_ID = '" + id
                + "' and I_CLASS = '2' order by N_ORDER_NUM");
        for (String propId : propIdList) {
            String propName = getSingleValueBySQL("select NAME from JCR_SITEM where ID = '" + propId + "'");
            String propValue = getSingleValueBySQL("select DATA from JCR_SVALUE where PROPERTY_ID = '" + propId + "'");
            log(trimNS(propName) + " : " + propValue);
        }

        List<String> itemIdList = getSingleValueListBySQL("select ID from JCR_SITEM where PARENT_ID = '" + id
                + "' and I_CLASS = '1' order by N_ORDER_NUM");
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

    private static String getUser(String user) throws Exception {
        String query = "select ID from JCR_SITEM where NAME = '[http://www.gatein.org/jcr/mop/1.0/]" + user + "'";
        List<String> idList = getSingleValueListBySQL(query);
        if (idList.size() > 0) {
            return idList.get(0);
        } else {
            return null;
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
