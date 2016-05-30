package com.example;

import java.sql.Blob;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import org.exoplatform.portal.config.model.ModelObject;
import org.exoplatform.portal.config.model.Page;
import org.exoplatform.portal.mop.management.binding.xml.PageMarshaller;

public class PagesQueryUtil {

    public static Connection conn;

    public static void main(String[] args) throws Exception {
        Class.forName("oracle.jdbc.driver.OracleDriver");
        conn = DriverManager.getConnection("jdbc:oracle:thin:@localhost:49161:xe", "epp522jcr", "oracle");

        String userId = getUser("john");
        List<String> pageIdList = getPages(userId);

        List<Page> pageList = new ArrayList<Page>();
        for (String pageId : pageIdList) {
            Page page = createPage(pageId);
            pageList.add(page);
        }

        exportPages(pageList);

    }

    private static void exportPages(List<Page> pageList) {
        Page.PageSet pages = new Page.PageSet();
        pages.setPages(new ArrayList<Page>(pageList));

        PageMarshaller marshaller = new PageMarshaller();
        marshaller.marshal(pages, System.out);
    }

    private static Page createPage(String pageId) throws Exception {
        Page page = new Page();
        page.setId(pageId);

        String name = getValueBySQL("select NAME from JCR_SITEM where ID = '" + pageId + "'");
        page.setName(name.replace("[http://www.gatein.org/jcr/mop/1.0/]", ""));

        String title = getPropertyByPropNameAndItemId("[http://www.gatein.org/jcr/gatein/1.0/]name", pageId);
        page.setTitle(title);

        String accessPermissions = getPropertyByPropNameAndItemId("[http://www.gatein.org/jcr/gatein/1.0/]access-permissions", pageId);
        page.setAccessPermissions(accessPermissions.split(";"));

        String showMaxWindow = getPageAttributeByAttrNameAndItemId("[http://www.gatein.org/jcr/mop/1.0/]show-max-window", pageId);
        page.setShowMaxWindow(Boolean.valueOf(showMaxWindow));

        ArrayList<ModelObject> children = getChildren(pageId);
        page.setChildren(children);

        return page;
    }

    private static ArrayList<ModelObject> getChildren(String pageId) throws Exception {
        ArrayList<ModelObject> children = new ArrayList<ModelObject>();
        String rootcomponent = getItemIdByNameAndParentId("[http://www.gatein.org/jcr/mop/1.0/]rootcomponent", pageId);
        return children;
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
                throw new RuntimeException(itemName + " not found for PARENT_ID = " + parentId);
            }

            return itemId;

        } finally {
            rs.close();
            stmt.close();
        }

    }

    private static String getPageAttributeByAttrNameAndItemId(String attrName, String pageId) throws Exception {

        // Step 1 : get the attributes node of the page
        String attributesId = getItemIdByNameAndParentId("[http://www.gatein.org/jcr/mop/1.0/]attributes", pageId);

        // Step 2 : get the attribute node of the name
        String attrId = getItemIdByNameAndParentId(attrName, attributesId);
        
        // Step 3 : get the value node of the attribute
        String attrValuePropId = getItemIdByNameAndParentId("[http://www.gatein.org/jcr/mop/1.0/]value", attrId);

        // Step 4 : get the value from JCR_SVALUE
        String attrValue = getValueBySQL("select DATA from JCR_SVALUE where PROPERTY_ID = '" + attrValuePropId + "'");
        
        return attrValue;
    }

    private static String getPropertyByPropNameAndItemId(String propName, String itemId) throws Exception {

        // Step 1 : get the prop node of the name
        String propId = getItemIdByNameAndParentId(propName, itemId);

        // Step 2 : get the value from JCR_SVALUE
        String propValue = getValueBySQL("select DATA from JCR_SVALUE where PROPERTY_ID = '" + propId + "'");
        
        return propValue;
    }

    private static String getValueBySQL(String query) throws Exception {
        Statement stmt = null;
        ResultSet rs = null;
        Object value = null;

        try {
            stmt = conn.createStatement();
            rs = stmt.executeQuery(query);

            if (!rs.next()) {
                System.out.println("No result for " + query);
                return "";
            }

            value = rs.getObject(1);

        } finally {
            rs.close();
            stmt.close();
        }
        
        if (value instanceof String) {
            return (String)value;
        } else if (value instanceof Blob) {
            return new String(((Blob)value).getBytes(1, (int) ((Blob)value).length()), "UTF-8");
        } else {
            throw new RuntimeException("Unexpected type : " + value.getClass());
        }

    }


    private static List<String> getPages(String userId) throws Exception {
        String query = "select * from JCR_SITEM" + " where PARENT_ID in" + " (select ID from JCR_SITEM where PARENT_ID in"
                + "  (select ID from JCR_SITEM where PARENT_ID in" + "   (select ID from JCR_SITEM where PARENT_ID in"
                + "    (select ID from JCR_SITEM where PARENT_ID = '" + userId + "'" + "     and NAME like '%rootpage')" + "    and NAME like '%children')"
                + "   and NAME like '%pages')" + "  and NAME like '%children')" + " and NAME like '[http://www.gatein.org/jcr/mop/1.0/]%'";

        List<String> idList = runQuery(query);
        return idList;
    }

    private static String getUser(String user) throws Exception {
        String query = "select * from JCR_SITEM where NAME = '[http://www.gatein.org/jcr/mop/1.0/]" + user + "'";
        List<String> idList = runQuery(query);
        return idList.get(0);
    }

    private static List<String> runQuery(String query) throws Exception {
        List<String> idList = new ArrayList<String>();

        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery(query);
        while (rs.next()) {
            idList.add((String) rs.getObject(1));

            System.out.println(rs.getObject(1));
            System.out.println(rs.getObject(2));
            System.out.println(rs.getObject(3));
            // System.out.println(rs.getObject(4));
            // System.out.println(rs.getObject(5));
            // System.out.println(rs.getObject(6));
            // System.out.println(rs.getObject(7));
            // System.out.println(rs.getObject(8));
            // System.out.println(rs.getObject(9));
            // System.out.println(rs.getObject(10));
            System.out.println("--------------");
        }

        rs.close();
        stmt.close();

        return idList;
    }
}
