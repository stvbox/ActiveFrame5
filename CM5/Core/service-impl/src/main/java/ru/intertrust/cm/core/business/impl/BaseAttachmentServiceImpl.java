package ru.intertrust.cm.core.business.impl;

import com.healthmarketscience.rmiio.RemoteInputStream;
import com.healthmarketscience.rmiio.RemoteInputStreamClient;
import com.healthmarketscience.rmiio.SimpleRemoteInputStream;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import ru.intertrust.cm.core.business.api.BaseAttachmentService;
import ru.intertrust.cm.core.business.api.CrudService;
import ru.intertrust.cm.core.business.api.dto.DomainObject;
import ru.intertrust.cm.core.business.api.dto.GenericDomainObject;
import ru.intertrust.cm.core.business.api.dto.Id;
import ru.intertrust.cm.core.business.api.dto.StringValue;
import ru.intertrust.cm.core.config.AttachmentTypeConfig;
import ru.intertrust.cm.core.config.ConfigurationExplorer;
import ru.intertrust.cm.core.config.DomainObjectTypeConfig;
import ru.intertrust.cm.core.dao.access.AccessControlService;
import ru.intertrust.cm.core.dao.access.AccessToken;
import ru.intertrust.cm.core.dao.access.DomainObjectAccessType;
import ru.intertrust.cm.core.dao.api.*;
import ru.intertrust.cm.core.dao.dto.AttachmentInfo;
import ru.intertrust.cm.core.dao.exception.DaoException;
import ru.intertrust.cm.core.model.FatalException;
import ru.intertrust.cm.core.model.SystemException;
import ru.intertrust.cm.core.model.UnexpectedException;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Created by andrey on 25.04.14.
 */
public abstract class BaseAttachmentServiceImpl implements BaseAttachmentService {
  
    final static org.slf4j.Logger logger = LoggerFactory.getLogger(RemoteAttachmentServiceImpl.class);

    @Autowired
    private AttachmentContentDao attachmentContentDao;
    @Autowired
    private DomainObjectDao domainObjectDao;
    @Autowired
    private CrudService crudService;
    @Autowired
    private ConfigurationExplorer configurationExplorer;
    @Autowired
    private AccessControlService accessControlService;
    @Autowired
    private DomainObjectTypeIdCache domainObjectTypeIdCache;
    @Autowired
    private CurrentUserAccessor currentUserAccessor;

    public void setCurrentUserAccessor(CurrentUserAccessor currentUserAccessor) {
        this.currentUserAccessor = currentUserAccessor;
    }

    @Override
    public DomainObject createAttachmentDomainObjectFor(Id objectId, String attachmentType) {
        try {
            GenericDomainObject attachmentDomainObject = (GenericDomainObject) crudService.createDomainObject(attachmentType);

            String domainObjectType = domainObjectTypeIdCache.getName(objectId);

            String attchmentLinkedField = getAttachmentOwnerObject(attachmentType, domainObjectType);

            attachmentDomainObject.setReference(attchmentLinkedField, objectId);
            return attachmentDomainObject;
        } catch (SystemException e) {
            throw e;
        } catch (Exception ex) {
            logger.error("Unexpected exception caught in createAttachmentDomainObjectFor", ex);
            throw new UnexpectedException("AttachmentService", "createAttachmentDomainObjectFor",
                    "objectId:" + objectId + " attachmentType:" + attachmentType, ex);
        }
    }

    public void setAccessControlService(AccessControlService accessControlService) {
        this.accessControlService = accessControlService;
    }

    public void setDomainObjectTypeIdCache(DomainObjectTypeIdCache domainObjectTypeIdCache) {
        this.domainObjectTypeIdCache = domainObjectTypeIdCache;
    }

    @Override
    public DomainObject saveAttachment(RemoteInputStream inputStream, DomainObject attachmentDomainObject) {
        InputStream contentStream = null;
        StringValue newFilePathValue = null;
        DomainObject savedDoaminObject = null;
        try {
            contentStream = RemoteInputStreamClient.wrap(inputStream);
            String fileName = attachmentDomainObject.getString(NAME);
            AttachmentInfo attachmentInfo = attachmentContentDao.saveContent(contentStream, fileName);
            String newFilePath = attachmentInfo.getRelativePath();

            attachmentDomainObject.setString(MIME_TYPE, attachmentInfo.getMimeType());
            attachmentDomainObject.setLong(CONTENT_LENGTH, attachmentInfo.getContentLength());
            
            if (newFilePath == null || newFilePath.isEmpty()) {
                throw new UnexpectedException("File isn't created. DO:" + attachmentDomainObject.getId());
            }
            newFilePathValue = new StringValue(newFilePath);
            StringValue oldFilePathValue = (StringValue) attachmentDomainObject.getValue(PATH);
            attachmentDomainObject.setValue(PATH, new StringValue(newFilePath));
            AccessToken accessToken = createSystemAccessToken();

            savedDoaminObject = domainObjectDao.save(attachmentDomainObject, accessToken);

            //предыдущий файл удаляем
            if (oldFilePathValue != null && !oldFilePathValue.isEmpty()) {
                //файл может быть и не удален, в случае если заблокирован
                attachmentDomainObject.setValue(PATH, oldFilePathValue);
                attachmentContentDao.deleteContent(attachmentDomainObject);
            }
            savedDoaminObject.setValue("path", newFilePathValue);
            return savedDoaminObject;
        } catch (IOException ex) {
            if (newFilePathValue != null && !newFilePathValue.isEmpty()) {
                attachmentDomainObject.setValue(PATH, newFilePathValue);
                attachmentContentDao.deleteContent(attachmentDomainObject);
            }
            logger.error("Unexpected exception caught in saveAttachment", ex);
            throw new UnexpectedException("AttachmentService", "saveAttachment",
                    "attachmentDomainObject:" + attachmentDomainObject.getId(), ex);
        } catch (SystemException e) {
            throw e;
        } catch (Exception ex) {
            logger.error("Unexpected exception caught in saveAttachment", ex);
            throw new UnexpectedException("AttachmentService", "saveAttachment",
                    "attachmentDomainObject:" + attachmentDomainObject.getId(), ex);
        } finally {
            if (contentStream != null) {
                try {
                    contentStream.close();
                } catch (IOException e) {
                    logger.error(e.getMessage());
                }
            }

        }
    }

    private AccessToken createSystemAccessToken() {
        return accessControlService.createSystemAccessToken("AttachmentService");
    }

    @Override
    public RemoteInputStream loadAttachment(Id attachmentDomainObjectId) {
        InputStream inFile = null;
        SimpleRemoteInputStream remoteInputStream = null;
        DomainObject attachmentDomainObject = crudService.find(attachmentDomainObjectId);
        try {
            InputStream inputStream = attachmentContentDao.loadContent(attachmentDomainObject);
            RemoteInputStream export = wrapStream(inputStream);
            return export;
        } catch (SystemException e) {
            throw e;
        } catch (Exception ex) {
            logger.error("Unexpected exception caught in loadAttachment", ex);
            throw new UnexpectedException("AttachmentService", "loadAttachment",
                    "attachmentDomainObjectId:" + attachmentDomainObjectId, ex);
        } finally {
            if (inFile != null) {
                try {
                    inFile.close();
                } catch (IOException e) {
                    logger.error(e.getMessage(), e);
                }
            }
            if (remoteInputStream != null) {
                remoteInputStream.close();
            }
        }
    }

    abstract RemoteInputStream wrapStream(InputStream inputStream) throws java.rmi.RemoteException;

    @Override
    public void deleteAttachment(Id attachmentDomainObjectId) {
        try {
            AccessToken accessToken = createSystemAccessToken();
            // [CMFIVE-705, 02/04/14] crudService.find(..) replaced with domainObjectDao.find(..) as crudService refers
            // to EjbContext which is unavailable at reports deployment time
            //DomainObject attachmentDomainObject = crudService.find(attachmentDomainObjectId);
            DomainObject attachmentDomainObject = domainObjectDao.find(attachmentDomainObjectId, accessToken);
            attachmentContentDao.deleteContent(attachmentDomainObject);
            //файл может быть и не удален
            domainObjectDao.delete(attachmentDomainObjectId, accessToken);
        } catch (SystemException e) {
            throw e;
        } catch (Exception ex) {
            logger.error("Unexpected exception caught in loadAttachment", ex);
            throw new UnexpectedException("AttachmentService", "loadAttachment",
                    "attachmentDomainObjectId:" + attachmentDomainObjectId, ex);
        }
    }

    /**
     * Поиск вложений доменного объекта. Выполняет поиск всех вложеннний, указанных в цепочке наследования доменного
     * объекта.
     */
    @Override
    public List<DomainObject> findAttachmentDomainObjectsFor(Id domainObjectId) {
        try {
            String domainObjectTypeName = domainObjectTypeIdCache.getName(domainObjectId);
            List<DomainObject> foundAttachments = new ArrayList<>();

            collectAttachmentsForDOAndParentDO(domainObjectId, domainObjectTypeName, foundAttachments);
            return foundAttachments;
        } catch (SystemException e) {
            throw e;
        } catch (Exception ex) {
            logger.error("Unexpected exception caught in findAttachmentDomainObjectsFor", ex);
            throw new UnexpectedException("AttachmentService", "findAttachmentDomainObjectsFor",
                    "domainObjectId:" + domainObjectId, ex);
        }
    }

    private void collectAttachmentsForDOAndParentDO(Id domainObjectId, String domainObjectTypeName,
                                                    List<DomainObject> attachmentDomainObjects) {

        findAttachmentsDeclaredInParticularDO(domainObjectId, domainObjectTypeName, attachmentDomainObjects);

        DomainObjectTypeConfig domainObjectTypeConfig =
                configurationExplorer.getConfig(DomainObjectTypeConfig.class, domainObjectTypeName);

        String parentDomainObjectType = domainObjectTypeConfig.getExtendsAttribute();
        if (parentDomainObjectType != null) {
            collectAttachmentsForDOAndParentDO(domainObjectId, parentDomainObjectType, attachmentDomainObjects);
        }
    }

    private void findAttachmentsDeclaredInParticularDO(Id domainObjectId, String domainObjectTypeName,
                                                       List<DomainObject> attachmentDomainObjects) {
        DomainObjectTypeConfig domainObjectTypeConfig =
                configurationExplorer.getConfig(DomainObjectTypeConfig.class, domainObjectTypeName);

        if (domainObjectTypeConfig.getAttachmentTypesConfig() != null) {
            for (AttachmentTypeConfig attachmentTypeConfig : domainObjectTypeConfig.getAttachmentTypesConfig()
                    .getAttachmentTypeConfigs()) {
                DomainObjectTypeConfig attachDomainObjectTypeConfig =
                        configurationExplorer.getConfig(DomainObjectTypeConfig.class, attachmentTypeConfig.getName());
                String attachmentType = attachDomainObjectTypeConfig.getName();
                List<DomainObject> domainObjectList = findAttachmentDomainObjectsFor(domainObjectId, attachmentType);
                if (domainObjectList != null) {
                    attachmentDomainObjects.addAll(domainObjectList);
                }
            }
        }
    }

    @Override
    public List<DomainObject> findAttachmentDomainObjectsFor(Id domainObjectId, String attachmentType) {
        try {
            String user = currentUserAccessor.getCurrentUser();
            AccessToken accessToken = accessControlService.createAccessToken(user, domainObjectId, DomainObjectAccessType.READ);
            String domainObjectType = domainObjectTypeIdCache.getName(domainObjectId);

            String attchmentLinkedField = getAttachmentOwnerObject(attachmentType, domainObjectType);

            return domainObjectDao.findLinkedDomainObjects(domainObjectId, attachmentType, attchmentLinkedField, accessToken);
        } catch (SystemException e) {
            throw e;
        } catch (Exception ex) {
            logger.error("Unexpected exception caught in findAttachmentDomainObjectsFor", ex);
            throw new UnexpectedException("AttachmentService", "findAttachmentDomainObjectsFor",
                    "domainObjectId:" + domainObjectId + " attachmentType:" + attachmentType, ex);
        }
    }

    @Override
    public DomainObject copyAttachment(Id attachmentDomainObjectId, Id destinationDomainObjectId, String destinationAttachmentType) {
        DomainObject attachDomainObject = crudService.find(attachmentDomainObjectId);

        DomainObject attachmentCopyDomainObject = createAttachmentDomainObjectFor(destinationDomainObjectId, destinationAttachmentType);
        attachmentCopyDomainObject.setString(NAME, attachDomainObject.getString(NAME));
        attachmentCopyDomainObject.setString(MIME_TYPE, attachDomainObject.getString(MIME_TYPE));
        attachmentCopyDomainObject.setLong(CONTENT_LENGTH, attachDomainObject.getLong(CONTENT_LENGTH));
        attachmentCopyDomainObject.setString(DESCRIPTION, attachDomainObject.getString(DESCRIPTION));

        RemoteInputStream remoteInputStream = loadAttachment(attachmentDomainObjectId);
        attachmentCopyDomainObject = saveAttachment(remoteInputStream, attachmentCopyDomainObject);

        return attachmentCopyDomainObject;
    }

    @Override
    public List<DomainObject> copyAttachments(List<Id> attachmentDomainObjectIds, Id destinationDomainObjectId,
                                              String destinationAttachmentType) {
        if (attachmentDomainObjectIds == null || attachmentDomainObjectIds.isEmpty()) {
            return new ArrayList<>(0);
        }

        List<DomainObject> attachmentCopyDomainObjects = new ArrayList<>(attachmentDomainObjectIds.size());

        for(Id attachmentDomainObjectId : attachmentDomainObjectIds) {
            DomainObject attachDomainObject =
                    copyAttachment(attachmentDomainObjectId, destinationDomainObjectId, destinationAttachmentType);
            attachmentCopyDomainObjects.add(attachDomainObject);
        }

        return attachmentCopyDomainObjects;
    }

    @Override
    public List<DomainObject> copyAttachmentsFrom(Id sourceDomainObjectId, String sourceAttachmentType,
                                           Id destinationDomainObjectId, String destinationAttachmentType) {
        List<DomainObject> attachmentDomainObjects =
                findAttachmentDomainObjectsFor(sourceDomainObjectId, sourceAttachmentType);
        return copyAttachments(extractIds(attachmentDomainObjects), destinationDomainObjectId, destinationAttachmentType);
    }

    @Override
    public List<DomainObject> copyAllAttachmentsFrom(Id sourceDomainObjectId, Id destinationDomainObjectId,
                                              String destinationAttachmentType){
        List<DomainObject> attachmentDomainObjects = findAttachmentDomainObjectsFor(sourceDomainObjectId);
        return copyAttachments(extractIds(attachmentDomainObjects), destinationDomainObjectId, destinationAttachmentType);
    }

    @Override
    public List<DomainObject> copyAllAttachmentsFrom(Id sourceDomainObjectId, Id destinationDomainObjectId,
                                              Map<String, String> attachmentTypeMap) {
        if (attachmentTypeMap == null || attachmentTypeMap.isEmpty()) {
            return new ArrayList<>();
        }

        List<DomainObject> attachmentDomainObjects = new ArrayList<>();

        for (Map.Entry<String, String> entry : attachmentTypeMap.entrySet()) {
            attachmentDomainObjects.addAll(
                    copyAttachmentsFrom(sourceDomainObjectId, entry.getKey(), destinationDomainObjectId, entry.getValue()));
        }

        return attachmentDomainObjects;
    }

    private List<Id> extractIds(List<DomainObject> attachmentDomainObjects) {
        if (attachmentDomainObjects == null || attachmentDomainObjects.isEmpty()) {
            return new ArrayList<>(0);
        }

        List<Id> attachmentCopyDomainObjectIds = new ArrayList<>(attachmentDomainObjects.size());

        for(DomainObject attachmentDomainObject : attachmentDomainObjects) {
            attachmentCopyDomainObjectIds.add(attachmentDomainObject.getId());
        }

        return attachmentCopyDomainObjectIds;
    }

    private String getAttachmentOwnerObject(String attachmentType, String domainObjectType) {
        DomainObjectTypeConfig objectConfig =
                configurationExplorer.getConfig(DomainObjectTypeConfig.class, domainObjectType);

        String declaringAttachmentDomainObject = null;
        if (objectConfig.getAttachmentTypesConfig() != null
                && objectConfig.getAttachmentTypesConfig().getAttachmentTypeConfigs().size() > 0) {

            for (AttachmentTypeConfig attachmentTypeConfig : objectConfig.getAttachmentTypesConfig()
                    .getAttachmentTypeConfigs()) {
                if (attachmentType.equals(attachmentTypeConfig.getName())) {
                    declaringAttachmentDomainObject = domainObjectType;
                    break;
                }
            }
        }

        if (declaringAttachmentDomainObject == null) {
            String parentType = objectConfig.getExtendsAttribute();
            if (parentType != null) {
                return getAttachmentOwnerObject(attachmentType, parentType);
            }
        }
        if (declaringAttachmentDomainObject == null) {
            throw new FatalException("Attachment declaration not found for " + attachmentType);
        }
        return declaringAttachmentDomainObject;
    }

    public void setCrudService(CrudService crudService) {
        this.crudService = crudService;
    }
}
