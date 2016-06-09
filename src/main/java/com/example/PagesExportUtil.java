package com.example;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.OutputStream;
import java.sql.Blob;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;

import org.exoplatform.portal.config.model.ApplicationState;
import org.exoplatform.portal.config.model.ApplicationType;
import org.exoplatform.portal.config.model.Container;
import org.exoplatform.portal.config.model.ModelObject;
import org.exoplatform.portal.config.model.Page;
import org.exoplatform.portal.config.model.TransientApplicationState;
import org.exoplatform.portal.config.serialize.PortletApplication;
import org.exoplatform.portal.mop.management.binding.xml.PageMarshaller;
import org.exoplatform.portal.pom.data.ApplicationData;
import org.exoplatform.portal.pom.spi.portlet.Portlet;
import org.exoplatform.portal.pom.spi.portlet.Preference;

public class PagesExportUtil {

    public static Connection conn;

    public static final String USER_LIST_FILE = "user-list.txt";

    public static void main(String[] args) throws Exception {

        Properties properties = new Properties();
        BufferedInputStream inputStream = new BufferedInputStream(new FileInputStream("gatein-pages-export.properties"));
        properties.load(inputStream);
        inputStream.close();

        Class.forName(properties.getProperty("driverClass"));
        conn = DriverManager.getConnection(properties.getProperty("connectionUrl"), properties.getProperty("username"),
                properties.getProperty("password"));

        BufferedReader reader = new BufferedReader(new FileReader(USER_LIST_FILE));
        while (reader.ready()) {

            try {
                String user = reader.readLine();
                if (user == null || user.trim().isEmpty()) {
                    continue;
                }
                System.out.println();
                System.out.println("user = " + user);
                System.out.println("-----------------------------");

                // System.out.println("used memory = " + ((Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024 * 1024)) + " MB");

                String userId = getUser(user);
                if (userId != null) {
                    List<String> pageIdList = getPages(userId);

                    List<Page> pageList = new ArrayList<Page>();
                    for (String pageId : pageIdList) {
                        Page page = createPage(pageId);
                        pageList.add(page);
                    }

                    BufferedOutputStream outputStream = new BufferedOutputStream(new FileOutputStream("output/" + user + ".xml"));

                    exportPages(pageList, outputStream);

                    outputStream.close();
                } else {
                    System.out.println("No user data found for user " + user);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        reader.close();

        System.out.println();
        System.out.println("=================================");
        System.out.println("verifying file existence...");

        // verify file existence
        BufferedReader reader2 = new BufferedReader(new FileReader(USER_LIST_FILE));
        while (reader2.ready()) {
            String user = reader2.readLine();
            if (user == null || user.trim().isEmpty()) {
                continue;
            }
            File file = new File("output/" + user + ".xml");
            if (file.exists()) {
                //System.out.println(user + ".xml exists");
            } else {
                System.out.println(user + ".xml doesn't exist");
            }
        }
        reader2.close();

        System.out.println("finish");
    }

    private static void exportPages(List<Page> pageList, OutputStream outputStream) {
        Page.PageSet pages = new Page.PageSet();
        pages.setPages(new ArrayList<Page>(pageList));

        PageMarshaller marshaller = new PageMarshaller();
        marshaller.marshal(pages, outputStream);
    }

    private static Page createPage(String pageId) throws Exception {
        Page page = new Page();
        page.setId(pageId);

        String name = getSingleValueBySQL("select NAME from JCR_SITEM where ID = '" + pageId + "'");
        page.setName(name.replace("[http://www.gatein.org/jcr/mop/1.0/]", ""));

        String title = getSinglePropertyByPropNameAndItemId("[http://www.gatein.org/jcr/gatein/1.0/]name", pageId);
        page.setTitle(title);

        String[] accessPermissions = getMultiplePropertyByPropNameAndItemId("[http://www.gatein.org/jcr/gatein/1.0/]access-permissions",
                pageId);
        page.setAccessPermissions(accessPermissions);

        String showMaxWindow = getAttributeByAttrNameAndItemId("[http://www.gatein.org/jcr/mop/1.0/]show-max-window", pageId);
        page.setShowMaxWindow(Boolean.valueOf(showMaxWindow));

        ArrayList<ModelObject> children = getChildren(pageId);
        page.setChildren(children);

        return page;
    }

    private static ArrayList<ModelObject> getChildren(String pageId) throws Exception {
        ArrayList<ModelObject> children = new ArrayList<ModelObject>();
        String rootcomponent = getItemIdByNameAndParentId("[http://www.gatein.org/jcr/mop/1.0/]rootcomponent", pageId);
        List<String> childIds = getChildComponents(rootcomponent);
        for (String childId : childIds) {
            ModelObject child = createChild(childId);
            children.add(child);
        }
        return children;
    }

    private static ModelObject createChild(String childId) throws Exception {
        System.out.println("NODE : " + childId);

        boolean isContainer = false;
        String primaryType = getSinglePropertyByPropNameAndItemId("[http://www.jcp.org/jcr/1.0]primaryType", childId);
        if (primaryType.equals("[http://www.gatein.org/jcr/mop/1.0/]uicontainer")) {
            isContainer = true;
        }
        String type = getAttributeByAttrNameAndItemId("[http://www.gatein.org/jcr/mop/1.0/]type", childId);
        if (isContainer && type == null) {
            return createContainer(childId);
        } else {
            return createPortletApplication(childId);
        }
    }

    private static ModelObject createContainer(String itemId) throws Exception {

        System.out.println("createContainer() : itemId = " + itemId);

        Container container = new Container();

        String name = getSinglePropertyByPropNameAndItemId("[http://www.gatein.org/jcr/gatein/1.0/]name", itemId);
        if (name != null) {
            container.setTitle(name);
        }

        String id = getAttributeByAttrNameAndItemId("[http://www.gatein.org/jcr/mop/1.0/]id", itemId);
        container.setId(id);

        String[] accessPermissions = getMultiplePropertyByPropNameAndItemId("[http://www.gatein.org/jcr/gatein/1.0/]access-permissions",
                itemId);
        container.setAccessPermissions(accessPermissions);

        String template = getAttributeByAttrNameAndItemId("[http://www.gatein.org/jcr/mop/1.0/]template", itemId);
        container.setTemplate(template);

        String factoryId = getAttributeByAttrNameAndItemId("[http://www.gatein.org/jcr/mop/1.0/]factory-id", itemId);
        container.setFactoryId(factoryId);

        ArrayList<ModelObject> children = new ArrayList<ModelObject>();
        List<String> childIds = getChildComponents(itemId);
        for (String childId : childIds) {
            ModelObject child = createChild(childId);
            children.add(child);
        }
        container.setChildren(children);

        return container;
    }

    private static PortletApplication createPortletApplication(String itemId) throws Exception {

        System.out.println("createPortletApplication() : itemId = " + itemId);

        ApplicationData<Portlet> applicationData = new ApplicationData<Portlet>(null, null, ApplicationType.PORTLET, null, null, null,
                null, null, false, false, false, null, null, null, new HashMap<String, String>(),
                Collections.singletonList("app-edit-permissions"));
        PortletApplication portletApplication = new PortletApplication(applicationData);

        //testQuery("select * from JCR_SITEM where PARENT_ID = '" + childId + "'");
        //testAttributes(childId);

        String name = getSinglePropertyByPropNameAndItemId("[http://www.gatein.org/jcr/gatein/1.0/]name", itemId);
        if (name != null) {
            portletApplication.setTitle(name);
        }

        String description = getSinglePropertyByPropNameAndItemId("[http://www.gatein.org/jcr/gatein/1.0/]description", itemId);
        if (description != null) {
            portletApplication.setDescription(description);
        }

        Portlet portlet = setupPortletPreferences(itemId);

        String type = getAttributeByAttrNameAndItemId("[http://www.gatein.org/jcr/mop/1.0/]type", itemId);
        String contentId = getContentId(itemId);
        if (contentId != null) {
            ApplicationState<Portlet> newState = new TransientApplicationState<Portlet>(contentId, portlet);
            portletApplication.setState(newState);
        } else if (type.equals("dashboard")) {
            ApplicationState<Portlet> newState = new TransientApplicationState<Portlet>("dashboard/DashboardPortlet", portlet);
            portletApplication.setState(newState);
        } else {
            throw new RuntimeException("Unknown portlet : type = " + type + ", contentId = " + contentId);
        }

        String[] accessPermissions = getMultiplePropertyByPropNameAndItemId("[http://www.gatein.org/jcr/gatein/1.0/]access-permissions",
                itemId);
        portletApplication.setAccessPermissions(accessPermissions);

        String theme = getAttributeByAttrNameAndItemId("[http://www.gatein.org/jcr/mop/1.0/]theme", itemId);
        portletApplication.setTheme(theme);

        String showInfoBar = getAttributeByAttrNameAndItemId("[http://www.gatein.org/jcr/mop/1.0/]showinfobar", itemId);
        portletApplication.setShowInfoBar(Boolean.valueOf(showInfoBar));

        String showMode = getAttributeByAttrNameAndItemId("[http://www.gatein.org/jcr/mop/1.0/]showmode", itemId);
        portletApplication.setShowApplicationMode(Boolean.valueOf(showMode));

        String showWindowState = getAttributeByAttrNameAndItemId("[http://www.gatein.org/jcr/mop/1.0/]showwindowstate", itemId);
        portletApplication.setShowApplicationState(Boolean.valueOf(showWindowState));

        String width = getAttributeByAttrNameAndItemId("[http://www.gatein.org/jcr/mop/1.0/]width", itemId);
        portletApplication.setWidth(width);

        String height = getAttributeByAttrNameAndItemId("[http://www.gatein.org/jcr/mop/1.0/]height", itemId);
        portletApplication.setHeight(height);

        return portletApplication;
    }

    private static Portlet setupPortletPreferences(String itemId) throws Exception {

        System.out.println("setupPortletPreferences() : itemId = " + itemId);

        Portlet portlet = new Portlet();

        String customizationId = getItemIdByNameAndParentId("[http://www.gatein.org/jcr/mop/1.0/]customization", itemId);

        if (customizationId == null) {
            return portlet;
        }

        String stateId = getItemIdByNameAndParentId("[http://www.gatein.org/jcr/mop/1.0/]state", customizationId);

        if (stateId == null) {
            return portlet;
        }

        List<String> preferenceIdList = getSingleValueListBySQL("select ID from JCR_SITEM where PARENT_ID = '" + stateId
                + "' and NAME like '[http://www.gatein.org/jcr/mop/1.0/]%'");
        for (String preferenceId : preferenceIdList) {
            String name = getSingleValueBySQL("select NAME from JCR_SITEM where ID = '" + preferenceId + "'");
            // NOTE: single value
            String value = getSinglePropertyByPropNameAndItemId("[http://www.gatein.org/jcr/mop/1.0/]value", preferenceId);
            String readonly = getSinglePropertyByPropNameAndItemId("[http://www.gatein.org/jcr/mop/1.0/]readonly", preferenceId);
            portlet.putPreference(new Preference(name.replace("[http://www.gatein.org/jcr/mop/1.0/]", ""), value, Boolean.valueOf(readonly)));
        }

        return portlet;
    }

    private static String getContentId(String itemId) throws Exception {
        System.out.println("getContentId() : itemId = " + itemId);

        String customizationId = getItemIdByNameAndParentId("[http://www.gatein.org/jcr/mop/1.0/]customization", itemId);

        if (customizationId == null) {
            return null;
        }

        String contentId = getSinglePropertyByPropNameAndItemId("[http://www.gatein.org/jcr/mop/1.0/]contentid", customizationId);

        if (contentId == null) {
            return null;
        }
        System.out.println("contentId = " + contentId);

        return contentId;
    }

    private static List<String> getChildComponents(String rootcomponent) throws Exception {
        String query = "select * from JCR_SITEM"
                + " where PARENT_ID = '"
                + rootcomponent
                + "' and NAME like '[http://www.gatein.org/jcr/mop/1.0/]%' and NAME != '[http://www.gatein.org/jcr/mop/1.0/]attributes' order by N_ORDER_NUM";

        List<String> idList = getSingleValueListBySQL(query);
        return idList;
    }

    private static String getItemIdByNameAndParentId(String itemName, String parentId) throws Exception {

        if (parentId == null) {
            System.out.println("  getItemIdByNameAndParentId() : parentId = null");
            return null;
        }

        String query = "select ID, NAME from JCR_SITEM where PARENT_ID = '" + parentId + "'";

        System.out.println("  " + query);

        String itemId = null;

        Statement stmt = null;
        ResultSet rs = null;

        try {
            stmt = conn.createStatement();
            rs = stmt.executeQuery(query);
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
                System.out.println("  " + itemName + " not found for PARENT_ID = " + parentId);
                return null;
            }

            return itemId;

        } finally {
            rs.close();
            stmt.close();
        }

    }

    private static String getAttributeByAttrNameAndItemId(String attrName, String itemId) throws Exception {

        System.out.println("getAttributeByAttrNameAndItemId() : attrName = " + attrName + ", itemId = " + itemId);

        // Step 1 : get the attributes node of the page
        String attributesId = getItemIdByNameAndParentId("[http://www.gatein.org/jcr/mop/1.0/]attributes", itemId);

        // Step 2 : get the attribute node of the name
        String attrId = getItemIdByNameAndParentId(attrName, attributesId);

        if (attrId == null) {
            return null;
        }

        // Step 3 : get the value node of the attribute
        String attrValuePropId = getItemIdByNameAndParentId("[http://www.gatein.org/jcr/mop/1.0/]value", attrId);

        // Step 4 : get the value from JCR_SVALUE
        String attrValue = getSingleValueBySQL("select DATA from JCR_SVALUE where PROPERTY_ID = '" + attrValuePropId + "'");

        return attrValue;
    }

    private static String getSinglePropertyByPropNameAndItemId(String propName, String itemId) throws Exception {

        System.out.println("getSinglePropertyByPropNameAndItemId() : propName = " + propName + ", itemId = " + itemId);

        // Step 1 : get the prop node of the name
        String propId = getItemIdByNameAndParentId(propName, itemId);

        if (propId == null) {
            return null;
        }

        // Step 2 : get the value from JCR_SVALUE
        String propValue = getSingleValueBySQL("select DATA from JCR_SVALUE where PROPERTY_ID = '" + propId + "'");

        return propValue;
    }

    private static String[] getMultiplePropertyByPropNameAndItemId(String propName, String itemId) throws Exception {

        System.out.println("getMultiplePropertyByPropNameAndItemId() : propName = " + propName + ", itemId = " + itemId);

        // Step 1 : get the prop node of the name
        String propId = getItemIdByNameAndParentId(propName, itemId);

        // Step 2 : get the value from JCR_SVALUE
        String[] propValues = getMultipleValueBySQL("select DATA from JCR_SVALUE where PROPERTY_ID = '" + propId + "'");

        return propValues;
    }

    private static String getSingleValueBySQL(String query) throws Exception {

        System.out.println("  " + query);

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
        } else if (value instanceof byte[]) {
            return new String((byte[]) value);
        } else {
            throw new RuntimeException("Unexpected type : " + value.getClass());
        }
    }

    private static String[] getMultipleValueBySQL(String query) throws Exception {

        System.out.println("  " + query);

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
            } else if (value instanceof byte[]) {
                values[i] = new String((byte[]) value);
            } else {
                throw new RuntimeException("Unexpected type : " + value.getClass());
            }
        }

        return values;
    }

    private static List<String> getPages(String userId) throws Exception {
        String query = "select ID from JCR_SITEM where PARENT_ID in (select ID from JCR_SITEM where PARENT_ID in (select ID from JCR_SITEM where PARENT_ID in (select ID from JCR_SITEM where PARENT_ID in"
                + " (select ID from JCR_SITEM where PARENT_ID = '"
                + userId
                + "' and NAME like '%rootpage') and NAME like '%children') and NAME like '%pages') and NAME like '%children') and NAME like '[http://www.gatein.org/jcr/mop/1.0/]%' order by N_ORDER_NUM";

        List<String> idList = getSingleValueListBySQL(query);
        return idList;
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

    private static List<String> getSingleValueListBySQL(String query) throws Exception {

        System.out.println("  " + query);

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
        while (rs.next()) {
            System.out.println("--------------");
            System.out.println(rs.getObject(1));
            System.out.println(rs.getObject(2));
            System.out.println(rs.getObject(3));
            System.out.println(rs.getObject(4));
            System.out.println(rs.getObject(5));
            System.out.println(rs.getObject(6));
            System.out.println(rs.getObject(7));
            System.out.println(rs.getObject(8));
            System.out.println(rs.getObject(9));
            System.out.println(rs.getObject(10));
            System.out.println("--------------");
        }

        rs.close();
        stmt.close();

        return;
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
}
