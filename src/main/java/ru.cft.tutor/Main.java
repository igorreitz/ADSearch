package ru.cft.tutor;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.*;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

/**
 * @author Igor Reitz on 11.06.2019
 */
public class Main {
    private static List<Employee> employees = new ArrayList<>();

    public static void main(String[] args) throws ParserConfigurationException, SAXException, IOException {
        // Создание фабрики и образца парсера
        SAXParserFactory factory = SAXParserFactory.newInstance();
        SAXParser parser = factory.newSAXParser();

        XMLHandler handler = new XMLHandler();
        parser.parse(new File("_________"), handler);
        System.out.println("-------------------------------------------------");
        employees.forEach((employee) -> {
            String emailFromAd = getEmailByEmployeeNumber(employee.getId());

            if (emailFromAd != null && !emailFromAd.isEmpty() && !employee.getEmail().equalsIgnoreCase(emailFromAd)) {
                System.out.println("Данные в 1С: " + employee);
                System.out.println("Email из домена (Active Directory): " + emailFromAd);
                System.out.println("-------------------------------------------------");
            }
        });
    }

    private static class XMLHandler extends DefaultHandler {
        private String id, lastname, firstname, email, lastElementName, sex;

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
            lastElementName = qName;
        }

        @Override
        public void characters(char[] ch, int start, int length) throws SAXException {
            String information = new String(ch, start, length);

            information = information.replace("\n", "").trim();

            if (!information.isEmpty()) {
                switch (lastElementName) {
                    case "id":
                        id = information;
                        break;
                    case "lastname":
                        lastname = information;
                        break;
                    case "firstname":
                        firstname = information;
                        break;
                    case "email":
                        email = information;
                        break;
                    case "sex":
                        sex = information;
                        break;
                }
            }
        }

        @Override
        public void endElement(String uri, String localName, String qName) throws SAXException {
            if ((id != null && !id.isEmpty()) &&
                    (lastname != null && !lastname.isEmpty()) &&
                    (firstname != null && !firstname.isEmpty()) &&
                    (email != null && !email.isEmpty()) &&
                    (sex != null && !sex.isEmpty())) {
                employees.add(new Employee(id, lastname, firstname, "", email, sex));
                id = null;
                lastname = null;
                firstname = null;

                email = null;
                sex = null;
            }
        }
    }

    public static String getEmailByEmployeeNumber(String empNumber) {
        try {
            // Activate paged results
            byte[] cookie = null;
            int count = 0;
            int total;

            Hashtable env = new Hashtable();

            env.put(Context.INITIAL_CONTEXT_FACTORY,
                    "com.sun.jndi.ldap.LdapCtxFactory");
            env.put(Context.REFERRAL, "follow");
            env.put(Context.SECURITY_AUTHENTICATION, "Simple");
            env.put(Context.SECURITY_PRINCIPAL, "______");
            env.put(Context.SECURITY_CREDENTIALS, "_______");
            env.put(Context.PROVIDER_URL, "ldap://_________:389");
            LdapContext ctx = new InitialLdapContext(env, null);

            ctx.setRequestControls(new Control[]{
                    new PagedResultsControl(10000, Control.CRITICAL)});

            do {
                // Perform the search
                NamingEnumeration results =
                        ctx.search("dc=____,dc=____,dc=ru", "(&(objectclass=user)(employeeNumber=" + empNumber + "))", getSimpleSearchControls());

                // Iterate over a batch of search results
                while (results != null && results.hasMore()) {
                    // Display an entry
                    SearchResult entry = (SearchResult) results.next();
                    javax.naming.directory.Attributes attrs = entry.getAttributes();
                    String result[] = new String[2];
                    if (attrs.get("mail") != null)
                    result = attrs.get("mail").toString().split(" ");
                    if (result[1] != null)
                        return result[1];
                }
                // Examine the paged results control response
                Control[] controls = ctx.getResponseControls();
                if (controls != null) {
                    for (int i = 0; i < controls.length; i++) {
                        if (controls[i] instanceof PagedResultsResponseControl) {
                            PagedResultsResponseControl prrc =
                                    (PagedResultsResponseControl) controls[i];
                            total = prrc.getResultSize();
                            cookie = prrc.getCookie();
                        } else {
                            // Handle other response controls (if any)
                        }
                    }
                }

                // Re-activate paged results
                ctx.setRequestControls(new Control[]{
                        new PagedResultsControl(10000, cookie, Control.CRITICAL)});
            } while (cookie != null);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }

    public static SearchControls getSimpleSearchControls() {
        SearchControls searchControls = new SearchControls();
        searchControls.setSearchScope(SearchControls.SUBTREE_SCOPE);
        searchControls.setTimeLimit(30000);
        String[] attrIDs =
                {"mail", "employeeNumber"};

        searchControls.setReturningAttributes(attrIDs);
        return searchControls;
    }
}
