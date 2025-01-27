package ru.intertrust.cm.remoteclient.permissions.test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import javax.naming.NamingException;

import ru.intertrust.cm.core.business.api.CollectionsService;
import ru.intertrust.cm.core.business.api.CrudService;
import ru.intertrust.cm.core.business.api.PermissionService;
import ru.intertrust.cm.core.business.api.access.AccessVerificationService;
import ru.intertrust.cm.core.business.api.dto.DomainObject;
import ru.intertrust.cm.core.business.api.dto.DomainObjectPermission;
import ru.intertrust.cm.core.business.api.dto.DomainObjectPermission.Permission;
import ru.intertrust.cm.core.business.api.dto.Id;
import ru.intertrust.cm.core.business.api.dto.IdentifiableObject;
import ru.intertrust.cm.core.business.api.dto.IdentifiableObjectCollection;
import ru.intertrust.cm.core.business.api.dto.ReferenceValue;
import ru.intertrust.cm.core.business.api.dto.Value;
import ru.intertrust.cm.core.gui.api.server.ActionService;
import ru.intertrust.cm.core.gui.model.action.ActionContext;
import ru.intertrust.cm.remoteclient.AssertExeption;
import ru.intertrust.cm.remoteclient.ClientBase;

/**
 * ВСЕ тесты проходят только если в группе "Administrators" всего два пользователя - "administrator" и "person1".
 * Такой состав определен в тестовых данных, дополнительно формировать группы не нужно.
 *
 * Состав групп можно проверить запросом:
 * select * from user_group g
 * inner join group_group gg on (g.id = gg.parent_group_id)
 * inner join group_member m on (gg.child_group_id = m.usergroup)
 * inner join person p on (p.id = m.person_id)
 * where g.group_name = 'Administrators'
 *
 */
public class TestPermission extends ClientBase {
    private Random random = new Random();
    
    public static void main(String[] args) {
        try {
            TestPermission test = new TestPermission();
            test.execute(args);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private CrudService.Remote getCrudService() throws NamingException{
        return (CrudService.Remote) getService("CrudServiceImpl", CrudService.Remote.class);
    }

    private CrudService.Remote getCrudService(String login, String password) throws NamingException{
        return (CrudService.Remote) getService("CrudServiceImpl", CrudService.Remote.class, login, password);
    }

    private CollectionsService.Remote getCollectionService() throws NamingException{
        return (CollectionsService.Remote) getService("CollectionsServiceImpl", CollectionsService.Remote.class);
    }
    
    private PermissionService.Remote getPermissionService() throws NamingException{
        return (PermissionService.Remote) getService("PermissionService",
                PermissionService.Remote.class);
    }
    
    public void execute(String[] args) throws Exception {
        try {
            super.execute(args);

            //Создаем внутренний документ
            DomainObject internalDocument = getCrudService().createDomainObject("Internal_Document");
            internalDocument.setString("Name", "Тестовый документ " + System.nanoTime());
            internalDocument.setString("ReturnOnReject", "YES");
            internalDocument.setLong("Stage", 0L);
            internalDocument.setString("RegNum", "InternalDoc111");
            internalDocument.setReference("docAuthor", getEmployeeId("Сотрудник 3"));
            internalDocument.setReference("Registrant", getEmployeeId("Сотрудник 4"));
            internalDocument = getCrudService().save(internalDocument);

            List<Id> negotiationCards = new ArrayList<Id>();
            
            //Создание карточек согласования
            for (int i = 0; i < 2; i++) {
                DomainObject negotiation = createNegotiationCard(internalDocument.getId(), "Сотрудник " + (i + 5));
                negotiationCards.add(negotiation.getId());
            }
                        
            //Проверка прав
            EtalonPermissions etalon = new EtalonPermissions();
            etalon.addPermission(getEmployeeId("Сотрудник 3"), Permission.Delete);
            etalon.addPermission(getEmployeeId("Сотрудник 3"), Permission.Write);
            etalon.addPermission(getEmployeeId("Сотрудник 3"), Permission.Read);
            etalon.addPermission(getPersonId("administrator"), Permission.Read);
            etalon.addPermission(getPersonId("administrator"), Permission.Write);
            etalon.addPermission(getPersonId("administrator"), Permission.Delete);
            etalon.addPermission(getEmployeeId("Сотрудник 1"), Permission.Read);
            etalon.addPermission(getEmployeeId("Сотрудник 1"), Permission.Write);
            etalon.addPermission(getEmployeeId("Сотрудник 1"), Permission.Delete);
            etalon.addPermission(getEmployeeId("Сотрудник 4"), Permission.Read);
            etalon.addActionPermission(getEmployeeId("Сотрудник 3"), "StartProcessAction");
            etalon.addActionPermission(getEmployeeId("Сотрудник 3"), "ChangeStatusAction");
            etalon.addActionPermission(getEmployeeId("Сотрудник 3"), "start-internal-document-process");            
            checkPermissions(internalDocument.getId(), etalon, "Status Draft");

            etalon = new EtalonPermissions();
            etalon.addPermission(getEmployeeId("Сотрудник 3"), Permission.Delete);
            etalon.addPermission(getEmployeeId("Сотрудник 3"), Permission.Write);
            etalon.addPermission(getEmployeeId("Сотрудник 3"), Permission.Read);
            etalon.addPermission(getPersonId("administrator"), Permission.Read);
            etalon.addPermission(getPersonId("administrator"), Permission.Write);
            etalon.addPermission(getPersonId("administrator"), Permission.Delete);
            etalon.addPermission(getEmployeeId("Сотрудник 1"), Permission.Read);
            etalon.addPermission(getEmployeeId("Сотрудник 1"), Permission.Write);
            etalon.addPermission(getEmployeeId("Сотрудник 1"), Permission.Delete);
            etalon.addPermission(getEmployeeId("Сотрудник 4"), Permission.Read);
            etalon.addActionPermission(getEmployeeId("Сотрудник 3"), "StartProcessAction");
            etalon.addActionPermission(getEmployeeId("Сотрудник 3"), "ChangeStatusAction");
            etalon.addActionPermission(getEmployeeId("Сотрудник 3"), "start-internal-document-process");            
            for (Id negotiationId  : negotiationCards) {
                checkPermissions(negotiationId, etalon, "Status Draft");
            }

            //Смена статуса + проверка прав. Статус сейчас меняется в строковом поле, после в точке расширения отлавливается это изменение 
            //и меняется статус уже с помощью метода setState. Это сделано для тестирования и невозможности сменить статус снаружи
            internalDocument.setString("ServerState", "Negotiation");
            internalDocument = getCrudService().save(internalDocument);
            internalDocument = getCrudService().find(internalDocument.getId());
            etalon = new EtalonPermissions();
            etalon.addPermission(getEmployeeId("Сотрудник 3"), Permission.Write);
            etalon.addPermission(getEmployeeId("Сотрудник 3"), Permission.Read);
            etalon.addPermission(getEmployeeId("Сотрудник 5"), Permission.Read);
            etalon.addPermission(getEmployeeId("Сотрудник 6"), Permission.Read);
            etalon.addPermission(getEmployeeId("Сотрудник 1"), Permission.Read);
            etalon.addPermission(getEmployeeId("Сотрудник 4"), Permission.Read);
            etalon.addPermission(getPersonId("administrator"), Permission.Read);
            checkPermissions(internalDocument.getId(), etalon, "Status Negotiation");
            
            etalon = new EtalonPermissions();
            etalon.addPermission(getEmployeeId("Сотрудник 3"), Permission.Write);
            etalon.addPermission(getEmployeeId("Сотрудник 3"), Permission.Delete);
            etalon.addPermission(getEmployeeId("Сотрудник 3"), Permission.Read);
            etalon.addPermission(getEmployeeId("Сотрудник 5"), Permission.Read);
            etalon.addPermission(getEmployeeId("Сотрудник 6"), Permission.Read);
            etalon.addPermission(getEmployeeId("Сотрудник 1"), Permission.Read);
            etalon.addPermission(getEmployeeId("Сотрудник 4"), Permission.Read);
            etalon.addPermission(getPersonId("administrator"), Permission.Read);
            for (Id negotiationId  : negotiationCards) {
                checkPermissions(negotiationId, etalon, "Status Negotiation");
            }            

            //Добавляем еще согласующего, права должны пересчитаться
            createNegotiationCard(internalDocument.getId(), "Сотрудник 7");
            etalon = new EtalonPermissions();
            etalon.addPermission(getEmployeeId("Сотрудник 3"), Permission.Write);
            etalon.addPermission(getEmployeeId("Сотрудник 3"), Permission.Read);
            etalon.addPermission(getEmployeeId("Сотрудник 5"), Permission.Read);
            etalon.addPermission(getEmployeeId("Сотрудник 6"), Permission.Read);
            etalon.addPermission(getEmployeeId("Сотрудник 7"), Permission.Read);
            etalon.addPermission(getEmployeeId("Сотрудник 1"), Permission.Read);
            etalon.addPermission(getEmployeeId("Сотрудник 4"), Permission.Read);
            etalon.addPermission(getPersonId("administrator"), Permission.Read);
            checkPermissions(internalDocument.getId(), etalon, "Add new Negotiator");

            etalon = new EtalonPermissions();
            etalon.addPermission(getEmployeeId("Сотрудник 3"), Permission.Write);
            etalon.addPermission(getEmployeeId("Сотрудник 3"), Permission.Delete);
            etalon.addPermission(getEmployeeId("Сотрудник 3"), Permission.Read);
            etalon.addPermission(getEmployeeId("Сотрудник 5"), Permission.Read);
            etalon.addPermission(getEmployeeId("Сотрудник 6"), Permission.Read);
            etalon.addPermission(getEmployeeId("Сотрудник 7"), Permission.Read);
            etalon.addPermission(getEmployeeId("Сотрудник 1"), Permission.Read);
            etalon.addPermission(getEmployeeId("Сотрудник 4"), Permission.Read);
            etalon.addPermission(getPersonId("administrator"), Permission.Read);
            for (Id negotiationId  : negotiationCards) {
                checkPermissions(negotiationId, etalon, "Add new Negotiator");
            }            

            internalDocument.setString("ServerState", "Registration");
            internalDocument = getCrudService().save(internalDocument);
            internalDocument = getCrudService().find(internalDocument.getId());
            etalon = new EtalonPermissions();
            etalon.addPermission(getEmployeeId("Сотрудник 3"), Permission.Write);
            etalon.addPermission(getEmployeeId("Сотрудник 3"), Permission.Read);
            etalon.addPermission(getEmployeeId("Сотрудник 4"), Permission.Write);
            etalon.addPermission(getEmployeeId("Сотрудник 4"), Permission.Read);
            etalon.addPermission(getPersonId("administrator"), Permission.Read);
            etalon.addPermission(getEmployeeId("Сотрудник 1"), Permission.Read);
            checkPermissions(internalDocument.getId(), etalon, "Status Registration");

            etalon = new EtalonPermissions();
            etalon.addPermission(getEmployeeId("Сотрудник 3"), Permission.Write);
            etalon.addPermission(getEmployeeId("Сотрудник 3"), Permission.Delete);
            etalon.addPermission(getEmployeeId("Сотрудник 3"), Permission.Read);
            etalon.addPermission(getEmployeeId("Сотрудник 4"), Permission.Delete);
            etalon.addPermission(getEmployeeId("Сотрудник 4"), Permission.Write);
            etalon.addPermission(getEmployeeId("Сотрудник 4"), Permission.Read);
            etalon.addPermission(getPersonId("administrator"), Permission.Read);
            etalon.addPermission(getEmployeeId("Сотрудник 1"), Permission.Read);
            for (Id negotiationId  : negotiationCards) {
                checkPermissions(negotiationId, etalon, "Status Registration");
            }            
            //Сотрудником 3 редактируем документ и карточку согласования
            DomainObject tmpEmp = getEmployee("Сотрудник 3");
            if (tmpEmp == null) {
                throw new AssertExeption("Can't find \"Сотрудник 3\" employee.");
            }
            changeObject(internalDocument.getId(), tmpEmp.getString("login"), "Name", "Тестовый документ " + System.nanoTime());
            internalDocument = getCrudService().find(internalDocument.getId());
            changeObject(negotiationCards.get(0), tmpEmp.getString("login"), "Name", "Карточка согласующего " + System.nanoTime());

            //Пытаемся удалить под сотрудником 5 карточку согласования, должны получить ошибку
            try{
                tmpEmp = getEmployee("Сотрудник 7");
                if (tmpEmp == null) {
                    throw new AssertExeption("Can't find \"Сотрудник 7\" employee.");
                }

                deleteObject(negotiationCards.get(0), tmpEmp.getString("login"));
                assertTrue("Не должно быть прав на удаление", false);

            }catch(AssertExeption assertException){
                throw assertException;
            }catch(Exception ignoreException){
                //Ошибка должна быть, работает правильно
            }

            //Пытаемся удалить под пользователем 3 должно удалится, так как должен отработать мапинг прав
            tmpEmp = getEmployee("Сотрудник 3");
            if (tmpEmp == null) {
                throw new AssertExeption("Can't find \"Сотрудник 3\" employee.");
            }
            deleteObject(negotiationCards.get(0), tmpEmp.getString("login"));
            negotiationCards.remove(0);
            
            internalDocument.setString("ServerState", "Registred");
            internalDocument = getCrudService().save(internalDocument);
            internalDocument = getCrudService().find(internalDocument.getId());
            etalon = new EtalonPermissions();
            //В этом статусе право read имеют все пользователи
            List<Id> allPersons = getAllPersons();
            for (Id personId : allPersons) {
                etalon.addPermission(personId, Permission.Read);
            }
            etalon.addPermission(getEmployeeId("Сотрудник 3"), Permission.Delete);
            checkPermissions(internalDocument.getId(), etalon, "Status Registred");
            for (Id negotiationId  : negotiationCards) {
                checkPermissions(negotiationId, etalon, "Status Registred");
            }
            
            //Удаляем карточку согласования. Должно удалится без ошибок
            tmpEmp = getEmployee("Сотрудник 3");
            if (tmpEmp == null) {
                throw new AssertExeption("Can't find \"Сотрудник 3\" employee.");
            }
            deleteObject(negotiationCards.get(0), tmpEmp.getString("login"));
            negotiationCards.remove(0);

            internalDocument.setString("ServerState", "OnRevision");
            internalDocument = getCrudService().save(internalDocument);
            internalDocument = getCrudService().find(internalDocument.getId());
            etalon = new EtalonPermissions();
            etalon.addPermission(getEmployeeId("Сотрудник 3"), Permission.Read);
            etalon.addPermission(getEmployeeId("Сотрудник 3"), Permission.Write);
            etalon.addPermission(getEmployeeId("Сотрудник 4"), Permission.Read);
            etalon.addPermission(getPersonId("administrator"), Permission.Read);
            etalon.addPermission(getEmployeeId("Сотрудник 1"), Permission.Read);
            checkPermissions(internalDocument.getId(), etalon, "Status OnRevision");
            for (Id negotiationId  : negotiationCards) {
                checkPermissions(negotiationId, etalon, "Status OnRevision");
            }            

            
            //Статус "Complete" отсутствует в матрице, при переходе в этот статус права должны обнулиться полностью
            internalDocument.setString("ServerState", "Complete");
            internalDocument = getCrudService().save(internalDocument);
            etalon = new EtalonPermissions();
            checkPermissions(internalDocument.getId(), etalon, "Status Complete");
            for (Id negotiationId  : negotiationCards) {
                checkPermissions(negotiationId, etalon, "Status Complete");
            }            
            
            
            //Создаем письмо
            DomainObject letter = getCrudService().createDomainObject("letter");
            letter.setString("subject", "Тестовое письмо " + System.nanoTime());
            letter = getCrudService().save(letter);

            etalon = new EtalonPermissions();
            etalon.addPermission(getPersonId("administrator"), Permission.Read);
            etalon.addPermission(getPersonId("administrator"), Permission.Write);
            etalon.addPermission(getPersonId("administrator"), Permission.Delete);
            etalon.addPermission(getEmployeeId("Сотрудник 1"), Permission.Read);
            etalon.addPermission(getEmployeeId("Сотрудник 1"), Permission.Write);
            etalon.addPermission(getEmployeeId("Сотрудник 1"), Permission.Delete);
            etalon.addPermission(getEmployeeId("Сотрудник 2"), Permission.Read);
            etalon.addActionPermission(getEmployeeId("Сотрудник 1"), "action1");
            etalon.addActionPermission(getPersonId("administrator"), "action1");
            checkPermissions(letter.getId(), etalon, "New letter");

            //Проверяем косвенные права с учетом наследования объектов у которых заимствуются права
            DomainObject testOutgoingDocument = getCrudService().createDomainObject("test_outgoing_document");
            testOutgoingDocument.setString("name", "Тестовый документ " + System.nanoTime());
            testOutgoingDocument.setReference("author", getEmployee("Сотрудник 2"));
            testOutgoingDocument.setReference("signer", getEmployee("Сотрудник 3"));
            testOutgoingDocument = getCrudService().save(testOutgoingDocument);
            
            DomainObject testResolution = getCrudService().createDomainObject("test_resolution");
            testResolution.setString("name", "Тестовый документ " + System.nanoTime());
            testResolution.setReference("executor", getEmployee("Сотрудник 4"));
            testResolution.setReference("document", testOutgoingDocument);
            testResolution = getCrudService().save(testResolution);

            etalon = new EtalonPermissions();
            etalon.addPermission(getEmployeeId("Сотрудник 2"), Permission.Read );
            etalon.addPermission(getEmployeeId("Сотрудник 2"), Permission.Write );
            etalon.addPermission(getEmployeeId("Сотрудник 2"), Permission.Delete );
            etalon.addPermission(getEmployeeId("Сотрудник 3"), Permission.Read);
            etalon.addPermission(getEmployeeId("Сотрудник 3"), Permission.Write);
            etalon.addPermission(getEmployeeId("Сотрудник 3"), Permission.Delete);
            checkPermissions(testOutgoingDocument.getId(), etalon, "test_outgoing_document");
            checkPermissions(testResolution.getId(), etalon, "test_resolution");
            
            //Проверка косвенных прав на "абстрактные" типы
            DomainObject testOrganization = getCrudService().createDomainObject("organization_test");
            testOrganization.setString("Name", "Name " + System.nanoTime());
            testOrganization = getCrudService().save(testOrganization);
            
            DomainObject testDepartment = getCrudService().createDomainObject("department_test");
            testDepartment.setString("name", "Name " + System.nanoTime());
            testDepartment.setReference("Organization", testOrganization);
            testDepartment = getCrudService().save(testDepartment);
            
            DomainObject testEmployee = getCrudService().createDomainObject("employee_test");
            testEmployee.setString("name", "Name " + System.nanoTime());
            testEmployee.setString("login", testEmployee.getString("name"));
            testEmployee.setString("Position", "Boss");
            testEmployee.setReference("Department", testDepartment);
            testEmployee = getCrudService().save(testEmployee);

            etalon = new EtalonPermissions();
            for (Id personId : allPersons) {
                etalon.addPermission(personId, Permission.Read);
                etalon.addPermission(personId, Permission.Write);
                etalon.addPermission(personId, Permission.Delete);
                etalon.addActionPermission(personId, "action1");                
            }            
            checkPermissions(testEmployee.getId(), etalon, "test_employee");
            
            CollectionsService notAdminCollectionService = (CollectionsService)getService("CollectionsServiceImpl", CollectionsService.Remote.class, "person1", "admin");
            IdentifiableObjectCollection collection = notAdminCollectionService.findCollectionByQuery("select * from person_test");
            assertTrue("test employee query", collection.size() > 0);
            
            //Проверка косвенных прав, ссылающихся на матрицу с read everybody
            DomainObject testType1 = getCrudService().createDomainObject("test_type_1");
            testType1.setString("name", "Name " + System.nanoTime());
            testType1 = getCrudService().save(testType1);
            
            DomainObject testType2 = getCrudService().createDomainObject("test_type_2");
            testType2.setString("name", "Name " + System.nanoTime());
            testType2.setReference("test_type_1", testType1);
            testType2 = getCrudService().save(testType2);
            
            notAdminCollectionService = (CollectionsService)getService("CollectionsServiceImpl", CollectionsService.Remote.class, "person1", "admin");
            collection = notAdminCollectionService.findCollectionByQuery("select * from test_type_1");
            assertTrue("test test_type_1 query", collection.size() > 0);

            collection = notAdminCollectionService.findCollectionByQuery("select * from test_type_2");
            assertTrue("test test_type_2 query", collection.size() > 0);
            
            //Проверка прав на родительский тип в случае матрицы у дочернего типа CMFIVE-1494
            DomainObject testType4 = getCrudService().createDomainObject("test_type_4");
            testType4.setString("name", "Name " + System.nanoTime());
            testType4.setReference("employee", getEmployeeId("Сотрудник 5"));
            testType4 = getCrudService().save(testType4);
            
            notAdminCollectionService = (CollectionsService)getService("CollectionsServiceImpl", CollectionsService.Remote.class, "person5", "admin");
            String query = "select id, created_date from (select t4.id, t3.created_date, t3.status, t4.employee from test_type_4 t4 left join test_type_3 t3 on t3.id = t4.id where t4.id = {0}) t";
            List<Value> params = new ArrayList<Value>();
            params.add(new ReferenceValue(testType4.getId()));
            collection = notAdminCollectionService.findCollectionByQuery(query, params);
            assertTrue("test test_type_4 query", collection.size() > 0 && collection.get(0).getTimestamp("created_date") != null);
            
            //Проверка прав на дочерний тип в случае матрицы у родительского типа CMFIVE-1541
            DomainObject testType6 = getCrudService().createDomainObject("test_type_6");
            testType6.setString("name", "Name " + System.nanoTime());
            testType6.setString("description", "Description " + System.nanoTime());
            Id employee5Id = getEmployeeId("Сотрудник 5");
            testType6.setReference("employee", employee5Id);
            testType6 = getCrudService().save(testType6);
            
            CrudService notAdminCrudservice = (CrudService.Remote) getService("CrudServiceImpl", CrudService.Remote.class, "person6", "admin");
            List<DomainObject> linkedObjects = notAdminCrudservice.findLinkedDomainObjects(employee5Id, "test_type_5", "employee");
            assertTrue("test_type_6 linked object count", linkedObjects.size() > 0);
            assertTrue("tes_type_6 name field", linkedObjects.get(0) != null);
            
            //Проверка косвенных прав на создание
            testEmployee = notAdminCrudservice.createDomainObject("employee_test");
            testEmployee.setString("name", "Name " + System.nanoTime());
            testEmployee.setString("login", testEmployee.getString("name"));
            testEmployee.setString("Position", "Boss");
            testEmployee.setReference("Department", testDepartment);
            testEmployee = notAdminCrudservice.save(testEmployee);

            //Проверка косвенных прав на удаление
            notAdminCrudservice.delete(testEmployee.getId());
            
            //Проверка косвенных прав на создание 2
            testEmployee = notAdminCrudservice.createDomainObject("employee_test");
            testEmployee.setString("name", "Name " + System.nanoTime());
            testEmployee.setString("login", testEmployee.getString("name"));
            testEmployee.setString("Position", "Boss");
            testEmployee.setReference("Department", testDepartment);
            testEmployee = notAdminCrudservice.save(testEmployee);

            //Проверка косвенных прав на удаление с помощью метода множественного удаления 
            notAdminCrudservice.delete(Collections.singletonList(testEmployee.getId()));
            
            //Проверка создания обьектов и наличия к ним доступа из той же транзакции CMFIVE-1779
            DomainObject country1 = notAdminCrudservice.createDomainObject("country");
            country1.setString("name", "Name-" + System.nanoTime());
            country1 = notAdminCrudservice.save(country1);
            
            // То же самое но для read-everybody (CMFIVE-4778)
            DomainObject testType17 = notAdminCrudservice.createDomainObject("test_type_17");
            testType17.setString("name", "Name-" + System.nanoTime());
            testType17 = notAdminCrudservice.save(testType17);
            
            //И дочерний тип
            DomainObject testType19 = notAdminCrudservice.createDomainObject("test_type_19");
            testType19.setString("name", "Name-" + System.nanoTime());
            testType19 = notAdminCrudservice.save(testType19);
                        
            // То же самое но для matrix-reference (CMFIVE-4778)
            DomainObject testType18 = notAdminCrudservice.createDomainObject("test_type_18");
            testType18.setString("name", "Name-" + System.nanoTime());
            testType18.setReference("test_type_17", testType17.getId());
            testType18 = notAdminCrudservice.save(testType18);
            
            DomainObject testType20 = notAdminCrudservice.createDomainObject("test_type_20");
            testType20.setString("name", "Name-" + System.nanoTime());
            testType20.setReference("test_type_17", testType19.getId());
            testType20 = notAdminCrudservice.save(testType20);

            
            getCrudService().delete(country1.getId());
            getCrudService().delete(testType18.getId());
            getCrudService().delete(testType17.getId());
            getCrudService().delete(testType20.getId());
            getCrudService().delete(testType19.getId());
            
            
            //Проверка мапинга прав
            notAdminCrudservice = (CrudService.Remote) getService("CrudServiceImpl", CrudService.Remote.class, "person6", "admin");
            DomainObject testType7 = notAdminCrudservice.createDomainObject("test_type_7");
            testType7.setString("description", "Description " + System.nanoTime());
            testType7 = notAdminCrudservice.save(testType7);            

            DomainObject testType8 = notAdminCrudservice.createDomainObject("test_type_8");
            testType8.setString("description", "Description " + System.nanoTime());
            testType8.setReference("test_type_7", testType7);
            testType8 = notAdminCrudservice.save(testType8);  
            
            //маппинг create-child
            DomainObject testType9 = notAdminCrudservice.createDomainObject("test_type_9");
            testType9.setString("description", "Description " + System.nanoTime());
            testType9.setReference("test_type_8", testType8);
            testType9 = notAdminCrudservice.save(testType9);  
            
            //Проверка прав на действия
            ActionService notAdminActionService = (ActionService)getService("ActionServiceImpl", ActionService.Remote.class, "person6", "admin");
            List<ActionContext> actions = notAdminActionService.getActions(testType8.getId());
            assertTrue("Action count", actions.size()  == 1);
            
            //Удаляем test9 чтоб не мешал удалить  test8
            getCrudService().delete(testType9.getId());
            //Проверка косвенных прав на удаление при мапинге прав
            notAdminCrudservice = (CrudService.Remote) getService("CrudServiceImpl", CrudService.Remote.class, "person6", "admin");
            notAdminCrudservice.delete(testType8.getId());

            DomainObject testType11 = getCrudService().createDomainObject("test_type_11");
            testType11.setString("test_type_11", "12344234 42341234123 4123 412");
            testType11 = getCrudService().save(testType11);
            
            etalon = new EtalonPermissions();
            for (Id personId : allPersons) {
                etalon.addPermission(personId, Permission.Read);
                etalon.addPermission(personId, Permission.Write);
            }
            checkPermissions(testType11.getId(), etalon, "test_type_11_Active");

            testType11.setString("status_name", "Complete");
            testType11 = getCrudService().save(testType11);
            
            etalon = new EtalonPermissions();
            for (Id personId : allPersons) {
                etalon.addPermission(personId, Permission.Read);
            }
            checkPermissions(testType11.getId(), etalon, "test_type_11_Complete");

            //Проверка прав на аудит
            DomainObject test13Do = createTest13();
            Id test13AuditId = getTest13AuditLog(test13Do.getId());
            test13Do = ((CrudService.Remote) getService("CrudServiceImpl", CrudService.Remote.class, "person2", "admin")).find(test13Do.getId());
            AccessVerificationService accessVerificationService = ((AccessVerificationService.Remote) getService("AccessVerificationServiceImpl", AccessVerificationService.Remote.class, "person2", "admin"));
            assertTrue("Test 13 check read access", accessVerificationService.isReadPermitted(test13AuditId));

            assertTrue("Test 13 check write access", accessVerificationService.isWritePermitted(test13AuditId));

            assertFalse("Test 13 check delete access", accessVerificationService.isDeletePermitted(test13AuditId));
            
            //Проверка удаление ДО при наличие в матрице статичной группы и роли
            DomainObject testType21 = getCrudService().createDomainObject("test_type_21");
            testType21.setString("description", "_" + System.currentTimeMillis());
            testType21.setReference("author", getPersonId("admin"));
            testType21 = getCrudService().save(testType21);
            
            getCrudService().delete(testType21.getId());
            log("Test delete DO with static group and context role: OK");
            
            //Проверка комбенированных прав (заимствованных на чтение и собственных на запись и удаление
            notAdminCrudservice = (CrudService.Remote) getService("CrudServiceImpl", CrudService.Remote.class, "person6", "admin");
            DomainObject testType14 = notAdminCrudservice.createDomainObject("test_type_14");
            testType14.setString("name", "Name-" + System.nanoTime());
            testType14 = getCrudService().save(testType14);

            DomainObject testType23 = null;
            
            //Создаем случайное количество type_23 объектов, чтоб сдвинулись идентификаторы друг относительно друга
            List<Id> test23Ids = new ArrayList<Id>();
            for (int i=0; i< 1+random.nextInt(10); i++) {
                testType23 = getCrudService().createDomainObject("test_type_23");
                testType23.setString("name", "name_" + System.currentTimeMillis());
                testType23.setReference("author", getEmployeeId("Сотрудник 1"));
                testType23.setReference("test_type_14", testType14.getId());
                testType23 = getCrudService().save(testType23);
                test23Ids.add(testType23.getId());
            }

            etalon = new EtalonPermissions();
            for (Id personId : allPersons) {
                etalon.addPermission(personId, Permission.Read);
            }         
            etalon.addPermission(getEmployeeId("Сотрудник 1"), Permission.Write);
            etalon.addPermission(getEmployeeId("Сотрудник 1"), Permission.Delete);
            etalon.addActionPermission(getEmployeeId("Сотрудник 1"), "test_action_permission");            
            checkPermissions(testType23.getId(), etalon, "Check combine permissions");            
            
            //Проверка метода find
            testType23 = ((CrudService.Remote) getService("CrudServiceImpl", CrudService.Remote.class, "person5", "admin")).find(testType23.getId());
            assertTrue("Combine permission find", testType23 != null);
            
            //Проверка получение в коллекции
            notAdminCollectionService = (CollectionsService)getService("CollectionsServiceImpl", CollectionsService.Remote.class, "person5", "admin");
            params.clear();
            params.add(new ReferenceValue(testType23.getId()));
            collection = notAdminCollectionService.findCollectionByQuery("select id from test_type_23 where id = {0}", params);
            assertTrue("Combine permission query", collection.size() > 0);
            
            testType23.setString("name", "name-" + System.currentTimeMillis());
            //Проверка на запись сначала у того у кого прав быть не должно
            try{
                ((CrudService.Remote) getService("CrudServiceImpl", CrudService.Remote.class, "person2", "admin")).save(testType23);
                //Должна быть ошибка если нет то работает некорректно
                assertTrue("person 2 Has permission on type_23", false);
            }catch(AssertExeption assertException){
                throw assertException;
            }catch(Exception ignoreException){
                //Ошибка должна быть, работает правильно
            }
            
            //Теперь у кого есть права
            ((CrudService.Remote) getService("CrudServiceImpl", CrudService.Remote.class, "person1", "admin")).save(testType23);

            
            //Проверяем право на выполнение действий под тем у кого прав не должно быть
            notAdminActionService = (ActionService)getService("ActionServiceImpl", ActionService.Remote.class, "person2", "admin");
            List<ActionContext> type23Actions = notAdminActionService.getActions(testType23.getId());
            assertTrue("Actions on type_23", type23Actions.size() == 0);
            
            //Проверяем право на выполнение действий под тем у кого должны быть права 
            notAdminActionService = (ActionService)getService("ActionServiceImpl", ActionService.Remote.class, "person1", "admin");
            type23Actions = notAdminActionService.getActions(testType23.getId());
            assertTrue("Actions on type_23", type23Actions.size() == 1);
            
            
            //Проверка на удаление сначала тем у кого не должно быть прав
            try{
                ((CrudService.Remote) getService("CrudServiceImpl", CrudService.Remote.class, "person2", "admin")).delete(testType23.getId());
                //Должна быть ошибка если нет то работает некорректно
                assertTrue("person 2 Has permission on type_23", false);
            }catch(AssertExeption assertException){
                throw assertException;
            }catch(Exception ignoreException){
                //Ошибка должна быть, работает правильно
            }
            
            //А потом у кого они должны быть            
            ((CrudService.Remote) getService("CrudServiceImpl", CrudService.Remote.class, "person1", "admin")).delete(test23Ids);
            
            getCrudService().delete(testType14.getId());
            log("Test combine permissions: OK");
            

            //Тест CREATE-CHILD для наследников CMFIVE-5338
            notAdminCrudservice = (CrudService)getService("CrudServiceImpl", CrudService.Remote.class, "person5", "admin");
            DomainObject test24 = notAdminCrudservice.createDomainObject("test_type_24");
            test24.setString("name", "_" + System.currentTimeMillis());
            test24.setReference("author", getPersonId("person5"));
            notAdminCrudservice = (CrudService)getService("CrudServiceImpl", CrudService.Remote.class, "person5", "admin");
            test24 = notAdminCrudservice.save(test24);
            
            DomainObject test25 = notAdminCrudservice.createDomainObject("test_type_25");
            test25.setString("name", "_" + System.currentTimeMillis());
            test25.setReference("test_type_24", test24.getId());
            notAdminCrudservice.save(test25);
            
            DomainObject test26 = notAdminCrudservice.createDomainObject("test_type_26");
            test26.setString("name", "_" + System.currentTimeMillis());
            test26.setString("description", "_" + System.currentTimeMillis());
            test26.setReference("test_type_24", test24.getId());
            notAdminCrudservice.save(test26);
            log("Test CMFIVE-5338: OK");
            
            //CMFIVE-5381 Проверка создания ДО с immutable полями под системным токеном из точки расширения
            notAdminCrudservice = (CrudService)getService("CrudServiceImpl", CrudService.Remote.class, "person5", "admin");
            test24 = notAdminCrudservice.createDomainObject("test_type_24");
            //Флаг точке расширения, что надо создать дочерние test_type_25
            test24.setString("name", "create-test25-by-system");
            //Автора надо указать другого пользователя, отличного от создателя
            test24.setReference("author", getPersonId("person2"));
            notAdminCrudservice = (CrudService)getService("CrudServiceImpl", CrudService.Remote.class, "person5", "admin");
            test24 = notAdminCrudservice.save(test24);
            log("Test CMFIVE-5381: OK");
            
            //CMFIVE-5892 Проверка наличия прав на создание связанного под пользовательскими правами в одной транзакции
            notAdminCrudservice = (CrudService)getService("CrudServiceImpl", CrudService.Remote.class, "person5", "admin");
            test24 = notAdminCrudservice.createDomainObject("test_type_24");
            //Флаг точке расширения, что надо создать дочерние test_type_25
            test24.setString("name", "create-test25-by-person");
            //Автора надо указать person5, под кем выполняется сохранение
            test24.setReference("author", getPersonId("person5"));
            notAdminCrudservice = (CrudService)getService("CrudServiceImpl", CrudService.Remote.class, "person5", "admin");
            test24 = notAdminCrudservice.save(test24);
                        
            log("Test CMFIVE-5892: OK");
            
            //Проверка создания ДО с имутабле полями в дочернем типе  CMFIVE-5397 
            notAdminCrudservice = (CrudService)getService("CrudServiceImpl", CrudService.Remote.class, "person5", "admin");
            test24 = notAdminCrudservice.createDomainObject("test_type_24");
            test24.setString("name", "_" + System.currentTimeMillis());
            test24.setReference("author", getPersonId("person5"));
            notAdminCrudservice = (CrudService)getService("CrudServiceImpl", CrudService.Remote.class, "person5", "admin");
            test24 = notAdminCrudservice.save(test24);
            
            DomainObject test28 = notAdminCrudservice.createDomainObject("test_type_28");
            test28.setString("name", "_" + System.currentTimeMillis());
            test28.setReference("test_type_24", test24.getId());
            notAdminCrudservice.save(test28);
            log("Test  CMFIVE-5397: OK");            

            //Лишние update, здесь только вызовы, анализ производится в файле логов
            notAdminCrudservice = (CrudService)getService("CrudServiceImpl", CrudService.Remote.class, "person5", "admin");
            test24 = notAdminCrudservice.createDomainObject("test_type_24");
            test24.setString("name", "_" + System.currentTimeMillis());
            test24.setReference("author", getPersonId("person5"));
            notAdminCrudservice = (CrudService)getService("CrudServiceImpl", CrudService.Remote.class, "person5", "admin");
            test24 = notAdminCrudservice.save(test24); 
            //Повторный save, права не меняются, update в таблице acl не должно быть
            test24.setString("name", "_" + System.currentTimeMillis());
            test24 = notAdminCrudservice.save(test24);
            
            //CMFIVE-6007
            notAdminCrudservice = (CrudService)getService("CrudServiceImpl", CrudService.Remote.class, "person5", "admin");
            DomainObject test29 = notAdminCrudservice.createDomainObject("test_type_29");
            test29.setString("name", "_" + System.currentTimeMillis());
            test29.setReference("author", getPersonId("person5"));
            notAdminCrudservice = (CrudService)getService("CrudServiceImpl", CrudService.Remote.class, "person5", "admin");
            test29 = notAdminCrudservice.save(test29);
            
            DomainObject test30 = notAdminCrudservice.createDomainObject("test_type_30");
            test30.setString("name", "_" + System.currentTimeMillis());
            test30.setReference("test_type_29", test29.getId());
            notAdminCrudservice.save(test30);
            
            DomainObject test30_1 = notAdminCrudservice.createDomainObject("test_type_30");
            test30_1.setString("name", "_" + System.currentTimeMillis());
            notAdminCrudservice.save(test30_1);
            
            log("Test  CMFIVE-6007: OK");             
            
            log("CMFIVE-8450");
            notAdminCrudservice = (CrudService)getService("CrudServiceImpl", CrudService.Remote.class, "person5", "admin");
            DomainObject test34 = notAdminCrudservice.createDomainObject("test_type_34");
            test34.setString("name", "_" + System.currentTimeMillis());
            test34 = notAdminCrudservice.save(test34);            

            try{
                DomainObject test35 = notAdminCrudservice.createDomainObject("test_type_35");
                test35.setString("name", "_" + System.currentTimeMillis());
                test35.setReference("test_type_34", test34.getId());
                test35 = notAdminCrudservice.save(test35);
                // Должна быть ошибка если нет то работает некорректно
                assertTrue("person 5 Has create permission on type_35", false);
            }catch(AssertExeption assertException){
                throw assertException;                
            }catch(Exception ex){
                //Правильное исключение, person5 не должен иметь прав на создание
                assertTrue("Correct exception", ex != null);
            }
            log("CMFIVE-8450 OK");

            // CMFIVE-35534
            DomainObject test36 = notAdminCrudservice.createDomainObject("TEST_TYPE_36");
            test36.setString("name", "_" + System.currentTimeMillis());
            test36 = notAdminCrudservice.save(test36);

            DomainObject test37 = notAdminCrudservice.createDomainObject("Test_Type_37");
            test37.setString("name", "_" + System.currentTimeMillis());
            test37.setReference("owner", test36.getId());
            notAdminCrudservice.save(test37);
            log("CMFIVE-35534 OK");

            // CMFIVE-33965 Права группы Administrators
            // CMFIVE-33965 - 1. Матрица с read-everybody="true"
            notAdminCrudservice = (CrudService.Remote) getService("CrudServiceImpl", CrudService.Remote.class, "administrator", "admin");
            DomainObject test38 = notAdminCrudservice.createDomainObject("test_type_38");
            test38.setString("name", "_" + System.currentTimeMillis());
            test38 = notAdminCrudservice.save(test38);
            test38 = notAdminCrudservice.save(test38);

            // Права есть хоть и acl пустой
            etalon = new EtalonPermissions();
            checkPermissions(test38.getId(), etalon, "Test 38");
            notAdminCrudservice = (CrudService.Remote) getService("CrudServiceImpl", CrudService.Remote.class, "administrator", "admin");
            notAdminCrudservice.delete(test38.getId());

            // CMFIVE-33965 - 2. Матрица с правами write группем Administrators
            notAdminCrudservice = (CrudService.Remote) getService("CrudServiceImpl", CrudService.Remote.class, "administrator", "admin");
            DomainObject test39 = notAdminCrudservice.createDomainObject("test_type_39");
            test39.setString("name", "_" + System.currentTimeMillis());
            test39 = notAdminCrudservice.save(test39);
            test39 = notAdminCrudservice.save(test39);

            // Права есть, ACL не пустой
            etalon = new EtalonPermissions();
            etalon.addPermission(getPersonId("administrator"), Permission.Write);
            etalon.addPermission(getPersonId("person1"), Permission.Write);
            checkPermissions(test39.getId(), etalon, "Test 39");
            notAdminCrudservice = (CrudService.Remote) getService("CrudServiceImpl", CrudService.Remote.class, "administrator", "admin");
            notAdminCrudservice.delete(test39.getId());

            // CMFIVE-33965 - 3. Матрицы нет совсем
            notAdminCrudservice = (CrudService.Remote) getService("CrudServiceImpl", CrudService.Remote.class, "administrator", "admin");
            DomainObject test40 = notAdminCrudservice.createDomainObject("test_type_40");
            test40.setString("name", "_" + System.currentTimeMillis());
            test40 = notAdminCrudservice.save(test40);
            test40 = notAdminCrudservice.save(test40);

            // Права есть хоть и acl пустой
            etalon = new EtalonPermissions();
            checkPermissions(test40.getId(), etalon, "Test 40");
            notAdminCrudservice = (CrudService.Remote) getService("CrudServiceImpl", CrudService.Remote.class, "administrator", "admin");
            notAdminCrudservice.delete(test40.getId());

            // Проверка переопределения матрицы
            Id person6 = getPersonId("person6");
            notAdminCrudservice = (CrudService.Remote) getService("CrudServiceImpl", CrudService.Remote.class, "person5", "admin");
            DomainObject personAltUids = notAdminCrudservice.createDomainObject("person_alt_uids");
            personAltUids.setString("alter_uid", "xxx" + System.currentTimeMillis());
            personAltUids.setString("alter_uid_type", "yyy_");
            personAltUids.setLong("idx", 0L);
            personAltUids.setReference("person", person6);
            personAltUids = notAdminCrudservice.save(personAltUids);
            personAltUids.setString("alter_uid", "zzz" + System.currentTimeMillis());
            personAltUids = notAdminCrudservice.save(personAltUids);
            notAdminCrudservice.delete(personAltUids.getId());
            assertTrue("Extends access matrix", true);

            // Проверка прав на типы
            DomainObject testType51 = getCrudService().createDomainObject("test_type_51");
            testType51 = getCrudService().save(testType51);

            DomainObject testType52 = getCrudService().createDomainObject("test_type_52");
            testType52.setReference("test_type_50", testType51);
            testType52 = getCrudService().save(testType52);

            testType52.setString("stringField", "_" + System.currentTimeMillis());
            testType52 = getCrudService("person5", "admin").save(testType52);


            if (hasError) {
                log("Test ERROR");
            }else{
                log("Test OK");
            }
        } catch(Throwable ex) {
            ex.printStackTrace();
        } finally {
            writeLog();
        }
    }

    
    
    private Id getTest13AuditLog(Id auditedId) throws NamingException{
        List<Value> params = new ArrayList<Value>();
        params.add(new ReferenceValue(auditedId));
        IdentifiableObjectCollection collection =
                getCollectionService().findCollectionByQuery("select t.id from test_type_12_al t where t.domain_object_id = {0}", params);
        Id result = null;
        if (collection.size() > 0) {
            result = collection.getId(0);
        }
        return result;
    }
    
    private void deleteObject(Id objectId, String login) throws NamingException {
        CrudService.Remote localCrudService = (CrudService.Remote) getService("CrudServiceImpl", CrudService.Remote.class, login, "admin"); 
        localCrudService.delete(objectId);
    }

    private DomainObject changeObject(Id objectId, String login, String field, String value) throws NamingException {
        CrudService.Remote localCrudService = (CrudService.Remote) getService("CrudServiceImpl", CrudService.Remote.class, login, "admin"); 
        DomainObject domainObject = localCrudService.find(objectId);
        domainObject.setString(field, value);
        domainObject = localCrudService.save(domainObject);
        return domainObject;
    }

    private List<Id> getAllPersons() throws NamingException {
        List<Id> result = new ArrayList<Id>();
        IdentifiableObjectCollection collection =
                getCollectionService().findCollectionByQuery("select id from person");
        for (IdentifiableObject identifiableObject : collection) {
            result.add(identifiableObject.getId());
        }
        return result;
    }

    private void checkPermissions(Id domainObjectId, EtalonPermissions etalon, String massage) throws Exception {
        List<DomainObjectPermission> serverPermission = getPermissionService().getObjectPermissions(domainObjectId);
        etalon.compare(serverPermission, massage);
    }

    private Id getEmployeeId(String employeeName) throws NamingException {
        IdentifiableObjectCollection collection =
                getCollectionService().findCollectionByQuery("select t.id from Employee t where t.Name = '" + employeeName
                        + "'");
        Id result = null;
        if (collection.size() > 0) {
            result = collection.getId(0);
        }
        return result;
    }

    private DomainObject getEmployee(String employeeName) throws NamingException {
        DomainObject result = null;
        IdentifiableObjectCollection collection =
                getCollectionService().findCollectionByQuery("select t.id from Employee t where t.Name = '" + employeeName
                        + "'");
        if (collection.size() > 0) {
            result = getCrudService().find(collection.getId(0));
        }
        return result;
    }    
    
    private Id getPersonId(String personLogin) throws NamingException {
        IdentifiableObjectCollection collection =
                getCollectionService().findCollectionByQuery("select t.id from person t where t.login = '" + personLogin
                        + "'");
        Id result = null;
        if (collection.size() > 0) {
            result = collection.getId(0);
        }
        return result;
    }

    private DomainObject createNegotiationCard(Id documentId, String employeeName) throws NamingException {
        DomainObject negotiationCard = getCrudService().createDomainObject("Negotiation_Card");
        negotiationCard.setString("Name", "карточка согласующего");
        negotiationCard.setReference("Parent_Document", documentId);
        negotiationCard.setReference("Negotiator", getEmployeeId(employeeName));
        negotiationCard = getCrudService().save(negotiationCard);
        return negotiationCard;
    }

    private DomainObject createTest13() throws NamingException {
        DomainObject test13 = getCrudService().createDomainObject("test_type_13");
        test13.setString("description", "xxx" + System.currentTimeMillis());
        test13 = getCrudService().save(test13);
        return test13;
    }    
    
    private class EtalonPermissions {
        private Map<Id, List<Permission>> personPermission = new HashMap<Id, List<Permission>>();
        private Map<Id, List<String>> personActionPermission = new HashMap<Id, List<String>>();
        private Map<Id, List<String>> createChildPermission = new HashMap<Id, List<String>>();

        public void addPermission(Id personId, Permission permission) {
            List<Permission> permissions = personPermission.get(personId);
            if (permissions == null) {
                permissions = new ArrayList<Permission>();
                personPermission.put(personId, permissions);
            }
            permissions.add(permission);
        }

        public void addActionPermission(Id personId, String action) {
            List<String> actions = personActionPermission.get(personId);
            if (actions == null) {
                actions = new ArrayList<String>();
                personActionPermission.put(personId, actions);
            }
            actions.add(action);
        }

        public void addCreateChild(Id personId, String childType) {
            List<String> childTypes = createChildPermission.get(personId);
            if (childTypes == null) {
                childTypes = new ArrayList<String>();
                createChildPermission.put(personId, childTypes);
            }
            childTypes.add(childType);
        }

        
        public boolean compare(List<DomainObjectPermission> serverPermission, String massage) throws Exception {
            boolean result = true;

            if (serverPermission.size() != personPermission.size()) {
                log("ACL NOT equals: ACL entry count not equals");
                result = false;
            }

            for (DomainObjectPermission domainObjectPermission : serverPermission) {
                //Проверка прав на CRUD
                List<Permission> etalonPermissions = personPermission.get(domainObjectPermission.getPersonId());
                for (Permission permission : domainObjectPermission.getPermission()) {
                    if (etalonPermissions == null || !etalonPermissions.contains(permission)) {
                        log("Permissions NOT equals: person id = " + domainObjectPermission.getPersonId() + " contain "
                                + permission + " but etalon not contain it");
                        result = false;
                    }
                }
                if (etalonPermissions != null) {
                    for (Permission permission : etalonPermissions) {
                        if (!domainObjectPermission.getPermission().contains(permission)) {
                            log("Permission NOT equals: person id = " + domainObjectPermission.getPersonId() + " contain "
                                    + permission + " but in base not contain it");
                            result = false;
                        }
                    }
                }
                
                //Проверка права на действия
                List<String> etalonActions = personActionPermission.get(domainObjectPermission.getPersonId());

                for (String action : domainObjectPermission.getActions()) {
                    if (etalonActions == null || !etalonActions.contains(action)) {
                        log("Actions NOT equals: person id = " + domainObjectPermission.getPersonId() + " contain "
                                + action + " but etalon not contain it");
                        result = false;
                    }
                }

                if (etalonActions != null) {
                    for (String action : etalonActions) {
                        if (!domainObjectPermission.getActions().contains(action)) {
                            log("Actions NOT equals: person id = " + domainObjectPermission.getPersonId() + " contain "
                                    + action + " but in base not contain it");
                            result = false;
                        }
                    }
                }

                //Проверка права на создание связанных
                List<String> etalonCreateChilds = createChildPermission.get(domainObjectPermission.getPersonId());

                for (String childType : domainObjectPermission.getCreateChildTypes()) {
                    if (etalonCreateChilds == null || !etalonCreateChilds.contains(childType)) {
                        log("Create child type NOT equals: person id = " + domainObjectPermission.getPersonId() + " contain "
                                + childType + " but etalon not contain it");
                        result = false;
                    }
                }

                if (etalonCreateChilds != null) {
                    for (String childType : etalonCreateChilds) {
                        if (!domainObjectPermission.getCreateChildTypes().contains(childType)) {
                            log("Create child type NOT equals: person id = " + domainObjectPermission.getPersonId() + " contain "
                                    + childType + " but in base not contain it");
                            result = false;
                        }
                    }
                }
                
            }

            if (result) {
                log(massage + " Compare OK");
            } else {
                log(massage + " Compare ERROR");
            }

            assertTrue(massage, result);
            return result;
        }
    }
}
