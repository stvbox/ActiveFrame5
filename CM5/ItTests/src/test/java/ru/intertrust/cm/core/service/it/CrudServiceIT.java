package ru.intertrust.cm.core.service.it;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import javax.ejb.EJB;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.spec.EnterpriseArchive;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.context.ApplicationContext;

import ru.intertrust.cm.core.business.api.CrudService;
import ru.intertrust.cm.core.business.api.dto.DomainObject;
import ru.intertrust.cm.core.business.api.dto.GenericDomainObject;
import ru.intertrust.cm.core.business.api.dto.Id;

import ru.intertrust.cm.core.dao.api.DomainObjectTypeIdCache;
import ru.intertrust.cm.webcontext.ApplicationContextProvider;

/**
 * Иитеграционный тест для {@link CrudService}
 * @author atsvetkov
 *
 */
@RunWith(Arquillian.class)
public class CrudServiceIT extends IntegrationTestBase {

    @EJB
    private CrudService.Remote crudService;

    @Deployment
    public static Archive<EnterpriseArchive> createDeployment() {
        return createDeployment(new Class[] {CrudServiceIT.class, ApplicationContextProvider.class}, new String[] {"test-data/import-department.csv",
                "test-data/import-organization.csv",
                "test-data/import-employee.csv", "beans.xml"});
    }

    @Before
    public void init() throws IOException, LoginException {
        LoginContext lc = login("admin", "admin");
        lc.login();
        try {
            importTestData("test-data/import-organization.csv");
            importTestData("test-data/import-employee.csv");
        } finally {
            lc.logout();
        }
        initializeSpringBeans();
    }

    private void initializeSpringBeans() {
        ApplicationContext applicationContext = ApplicationContextProvider.getApplicationContext();
        domainObjectTypeIdCache = applicationContext.getBean(DomainObjectTypeIdCache.class);
    }

    @Test
    public void testSaveFindExists() {
        GenericDomainObject personDomainObject = createPersonDomainObject();
        DomainObject savedPersonObject = crudService.save(personDomainObject);
        assertNotNull(savedPersonObject);
        assertNotNull(savedPersonObject.getId());
        assertNotNull(savedPersonObject.getString("Login"));

        DomainObject foundPersonObject = crudService.find(savedPersonObject.getId());

        assertNotNull(foundPersonObject);
        assertNotNull(foundPersonObject.getId());
        assertNotNull(foundPersonObject.getString("Login"));
        boolean exists = crudService.exists(savedPersonObject.getId());
        assertTrue(exists);

    }

    @Test
    public void testFindDelete() {

        GenericDomainObject organization1 = createOrganizationDomainObject();
        DomainObject savedOrganization1 = crudService.save(organization1);

        DomainObject foundOrganization = crudService.find(savedOrganization1.getId());
        assertNotNull(foundOrganization);
        crudService.delete(foundOrganization.getId());
        foundOrganization = crudService.find(foundOrganization.getId());
        assertTrue(foundOrganization == null);

    }
    
    @Test
    public void testFindAndDeleteList() {

        GenericDomainObject person1 = createPersonDomainObject();
        GenericDomainObject organization1 = createOrganizationDomainObject();

        DomainObject savedPerson1 = crudService.save(person1);
        DomainObject savedOrganization1 = crudService.save(organization1);

        Id[] objects = new Id[] {savedPerson1.getId(), savedOrganization1.getId() };
        List<Id> objectIds = Arrays.asList(objects);

        List<DomainObject> foundObjects = crudService.find(objectIds);

        List<Id> foundIds = getIdList(foundObjects);
        assertNotNull(foundObjects);
        assertTrue(foundObjects.size() == 2);

        assertTrue(foundIds.contains(savedPerson1.getId()));
        assertTrue(foundIds.contains(savedOrganization1.getId()));

        crudService.delete(objectIds);
        foundObjects = crudService.find(objectIds);
        assertTrue(foundObjects.size() == 0);

    }

    private List<Id> getIdList(List<DomainObject> domainObjects) {
        List<Id> foundIds = new ArrayList<Id>();
        for(DomainObject domainObject : domainObjects){
            foundIds.add(domainObject.getId());
        }
        return foundIds;
    }

    
    @Test
    public void testFindLinkedDoaminObjects() {
        GenericDomainObject organization = createOrganizationDomainObject();
        DomainObject savedOrganization = crudService.save(organization);
        DomainObject department = createDepartmentDomainObject(savedOrganization);
        DomainObject savedDepartment = crudService.save(department);

        List<DomainObject> linkedObjects =
                crudService.findLinkedDomainObjects(savedOrganization.getId(), "Department", "Organization");
        assertNotNull(linkedObjects);
        assertEquals(linkedObjects.get(0).getId(), savedDepartment.getId());

        List<Id> linkedObjectsIds =
                crudService.findLinkedDomainObjectsIds(savedOrganization.getId(), "Department", "Organization");
        assertNotNull(linkedObjectsIds);
        assertEquals(linkedObjectsIds.get(0), savedDepartment.getId());

    }

   private static GenericDomainObject createPersonDomainObject() {
        GenericDomainObject personDomainObject = new GenericDomainObject();
        personDomainObject.setCreatedDate(new Date());
        personDomainObject.setModifiedDate(new Date());
        personDomainObject.setTypeName("Person");
        personDomainObject.setString("Login", "login " + new Date());
        return personDomainObject;
    }
    
    private static GenericDomainObject createOrganizationDomainObject() {
        GenericDomainObject organizationDomainObject = new GenericDomainObject();
        organizationDomainObject.setCreatedDate(new Date());
        organizationDomainObject.setModifiedDate(new Date());
        organizationDomainObject.setTypeName("Organization");
        organizationDomainObject.setString("Name", "Organization" + new Date());
        return organizationDomainObject;
    }

    private static GenericDomainObject createDepartmentDomainObject(DomainObject savedOrganizationObject) {
        GenericDomainObject departmentDomainObject = new GenericDomainObject();
        departmentDomainObject.setCreatedDate(new Date());
        departmentDomainObject.setModifiedDate(new Date());
        departmentDomainObject.setTypeName("Department");
        departmentDomainObject.setString("Name", "department1");
        departmentDomainObject.setReference("Organization", savedOrganizationObject.getId());
        return departmentDomainObject;
    }
}
