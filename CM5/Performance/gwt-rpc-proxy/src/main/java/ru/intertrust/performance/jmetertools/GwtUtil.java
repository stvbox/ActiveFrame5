package ru.intertrust.performance.jmetertools;

import java.net.MalformedURLException;
import java.util.Map;
import java.util.Random;

import org.apache.jmeter.protocol.http.sampler.HTTPSampleResult;
import org.apache.jmeter.protocol.http.sampler.HTTPSamplerProxy;

import ru.intertrust.cm.core.business.api.dto.Dto;
import ru.intertrust.cm.core.business.api.dto.Id;
import ru.intertrust.cm.core.gui.model.Command;
import ru.intertrust.cm.core.gui.model.action.SaveActionContext;
import ru.intertrust.cm.core.gui.model.form.FormState;
import ru.intertrust.cm.core.gui.model.form.widget.CollectionRowsResponse;
import ru.intertrust.cm.core.gui.model.form.widget.WidgetState;
import ru.intertrust.cm.core.gui.model.plugin.collection.CollectionRowItem;

import com.cedarsoftware.util.io.JsonWriter;
import com.google.gwt.user.client.rpc.SerializationException;

public class GwtUtil {
    private static final String AB = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz _-";
    private static Random rnd = new Random();

    public static boolean isError(HTTPSampleResult responce) {
        return responce.getResponseDataAsString().startsWith("//EX");
    }

    public static GwtRpcRequest decodeRequest(HTTPSamplerProxy sampleProxy) throws MalformedURLException {
        return GwtRpcRequest.decode(
                ((org.apache.jmeter.protocol.http.util.HTTPArgument) sampleProxy.getArguments().getArgument(0)).getValue(),
                sampleProxy.getUrl().toString());
    }

    public static Object decodeResponce(HTTPSampleResult sampleResult) throws SerializationException {
        return decodeResponce(sampleResult.getQueryString(), sampleResult.getResponseDataAsString(), sampleResult.getURL().toString());
    }

    public static Object decodeResponce(String request, String responce, String targetUri) throws SerializationException {
        if (request == null || request.length() == 0 || responce == null || responce.length() == 0) {
            return null;
        }

        GwtRpcRequest gwtRpcRequest = GwtRpcRequest.decode(request, targetUri);

        SyncClientSerializationStreamReader reader = new SyncClientSerializationStreamReader(gwtRpcRequest.getSerializationPolicy());
        reader.prepareToRead(responce.substring(4));
        Object responceObj = null;
        if (reader.hasData()) {
            responceObj = reader.readObject();
        }
        return responceObj;
    }

    public static String decodeResponceToJson(String request, String responce, String targetUri) throws SerializationException {
        if (request == null || request.length() == 0 || responce == null || responce.length() == 0) {
            return null;
        }
        Object responceObj = decodeResponce(request, responce, targetUri);
        String json = JsonWriter.objectToJson(responceObj);
        try {
            json = JsonWriter.formatJson(json);
        } catch (Exception ex) {
        }
        return json;
    }

    public static String decodeResponceToJson(String request, String responce) throws SerializationException {
        return decodeResponceToJson(request, responce, null);
    }

    /**
     * Возвращает объект состояния виджета
     * @param request
     * @param widgetId
     * @return
     */
    public static WidgetState getWidgetState(GwtRpcRequest request, String widgetId) {
        //Получаем нужный виджет
        Command command = (Command) request.getParameters()[0];
        SaveActionContext saveContext = (SaveActionContext) command.getParameter();
        FormState formState = saveContext.getFormState();
        Map widgetStateMap = formState.getFullWidgetsState();
        WidgetState widgetState = (WidgetState) widgetStateMap.get(widgetId);
        return widgetState;
    }

    public static String getRndString(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++)
            sb.append(AB.charAt(rnd.nextInt(AB.length())));
        return sb.toString();
    }
    
    public static void setRequest(HTTPSamplerProxy sampler, GwtRpcRequest request) throws SerializationException{
        sampler.getArguments().getArgument(0).setValue(request.encode());
    }

    public static Id getRndCollectionsRow(Dto responce){
        CollectionRowsResponse collectionRowsResponse = (CollectionRowsResponse)responce;
        CollectionRowItem item = collectionRowsResponse.getCollectionRows().get(rnd.nextInt(collectionRowsResponse.getCollectionRows().size()));
        return item.getId();
    }
    
}
