package ru.intertrust.cm.core.business.api.crypto;

import java.io.InputStream;

import ru.intertrust.cm.core.business.api.dto.Id;
import ru.intertrust.cm.core.business.api.dto.VerifyResult;

/**
 * Сервис для выполнения криптографических операций на платформе CM5.
 * @author larin
 *
 */
public interface CryptoTool {

    /**
     * Проверка усовершенствованной ЭП включеннной в документ
     * @param inputStream поток содержащий документ со встроенной электронной подписью
     * @return
     */
    VerifyResult verify(InputStream document);
    
    /**
     * Проверка Усовершенствованной ЭП не включенной в документ
     * @param document поток содержащий документ
     * @param signature электронная подпись
     * @return
     */
    VerifyResult verify(InputStream document, byte[] signature);
    
    /**
     * Проверка стандартной ЭП
     * @param document поток содержащий документ
     * @param signature электронная подпись
     * @param signerSertificate сертификат подписавшено документ в формате DER
     * @return
     */
    VerifyResult verify(InputStream document, byte[] signature, byte[] signerSertificate);

    /**
     * Проверка стандартной ЭП с помощью сертификата, указанного в доменном объекте персоны
     * @param document поток содержащий документ
     * @param signature электронная подпись
     * @param personId идентификатор персоны которой был подписан документ
     * @return
     */
    VerifyResult verify(InputStream document, byte[] signature, Id personId);

    /**
     * Проверка стандартной ЭП с помощью сертификата, указанного в доменном объекте персоны
     * @param documentId идентификатор документа
     * @return
     */
    VerifyResult verify(Id documrntId);
}
