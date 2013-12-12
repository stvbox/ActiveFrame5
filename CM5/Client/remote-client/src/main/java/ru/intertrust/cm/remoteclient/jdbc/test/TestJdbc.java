package ru.intertrust.cm.remoteclient.jdbc.test;

import ru.intertrust.cm.core.business.api.CollectionsService;
import ru.intertrust.cm.core.business.api.ConfigurationService;
import ru.intertrust.cm.core.business.api.CrudService;
import ru.intertrust.cm.core.business.api.dto.DomainObject;
import ru.intertrust.cm.core.business.api.dto.IdentifiableObjectCollection;
import ru.intertrust.cm.core.business.api.dto.RdbmsId;
import ru.intertrust.cm.core.config.DomainObjectTypeConfig;
import ru.intertrust.cm.core.jdbc.JdbcDriver;
import ru.intertrust.cm.remoteclient.ClientBase;

import java.sql.*;
import java.util.Calendar;
import java.util.Collection;

public class TestJdbc extends ClientBase {

    private CrudService.Remote crudService;
    private CollectionsService.Remote collectionService;
    private ConfigurationService configService;

    public static void main(String[] args) {
        try {
            TestJdbc test = new TestJdbc();
            test.execute(args);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public void execute(String[] args) throws Exception {
        super.execute(args);

        crudService = (CrudService.Remote) getService(
                "CrudServiceImpl", CrudService.Remote.class);

        collectionService = (CollectionsService.Remote) getService(
                "CollectionsServiceImpl", CollectionsService.Remote.class);

        configService = (ConfigurationService) getService(
                "ConfigurationServiceImpl", ConfigurationService.Remote.class);

        Collection<DomainObjectTypeConfig> configs = configService.getConfigs(DomainObjectTypeConfig.class);

        //Создаем тестовый доменный объект
        DomainObject outgoingDocument = greateOutgoingDocument();

        //Выполняем запрос с помощью JDBC
        Class.forName(JdbcDriver.class.getName());

        Connection connection = DriverManager.getConnection("jdbc:sochi:remoting://localhost:4447", "admin", "admin");

        String query = "select t.id, t.name, t.created_date, t.author, t.long_field, t.status ";
        query += "from Outgoing_Document t ";
        query += "where t.created_date between ? and ? and t.Name = ? and t.Author = ? and t.Long_Field = ?";

        PreparedStatement prepareStatement =
                connection.prepareStatement(query);

        Calendar fromDate = Calendar.getInstance();
        fromDate.set(2000, 0, 1);
        prepareStatement.setTimestamp(1, new java.sql.Timestamp(fromDate.getTime().getTime()));
        prepareStatement.setTimestamp(2, new java.sql.Timestamp(System.currentTimeMillis()));
        prepareStatement.setString(3, "Outgoing_Document");
        prepareStatement.setLong(4, ((RdbmsId) outgoingDocument.getReference("Author")).getId());
        prepareStatement.setLong(5, 10);
        ResultSet resultset = prepareStatement.executeQuery();

        printResultSet(resultset);
        
        resultset.close();
        prepareStatement.close();

        query = "select t.id, t.name, t.created_date, t.author, t.long_field, t.status ";
        query += "from Outgoing_Document t ";
        Statement statement = connection.createStatement();
        if (statement.execute(query)){
            resultset = statement.getResultSet();

            printResultSet(resultset);
            resultset.close();
            statement.close();

        }

        statement = connection.createStatement();
        if (statement.execute(query)){
            resultset = statement.getResultSet();

            printResultSet(resultset);
            
            resultset.close();
            statement.close();
        }
        connection.close();

    }

    private DomainObject greateOutgoingDocument() {
        DomainObject document = crudService.createDomainObject("Outgoing_Document");
        document.setString("Name", "Outgoing_Document");
        document.setReference("Author", findDomainObject("Employee", "Name", "Employee-1"));
        document.setLong("Long_Field", 10L);

        document = crudService.save(document);
        return document;
    }

    private DomainObject findDomainObject(String type, String field, String fieldValue) {
        String query = "select t.id from " + type + " t where t." + field + "='" + fieldValue + "'";

        IdentifiableObjectCollection collection = collectionService.findCollectionByQuery(query);
        DomainObject result = null;
        if (collection.size() > 0) {
            result = crudService.find(collection.get(0).getId());
            log("Найден объект " + result.getTypeName() + " " + result.getId());
        }
        return result;
    }

    private void printResultSet(ResultSet resultset) throws SQLException{
        System.out.print("№\t");
        for (int i=1; i<=resultset.getMetaData().getColumnCount(); i++ ){
            System.out.print(resultset.getMetaData().getColumnName(i) + "\t");
        }
        System.out.print("\n");
        int rowCount = 0;
        while (resultset.next()) {
            System.out.print(rowCount + "\t");
            for (int i = 1; i <= resultset.getMetaData().getColumnCount(); i++) {
                System.out.print(resultset.getObject(i) + "\t");
            }
            System.out.print("\n");
            rowCount++;
        }
    }
}
