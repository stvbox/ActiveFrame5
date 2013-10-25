package ru.intertrust.cm.core.gui.impl.server.widget;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.util.Properties;

/**
 * @author Yaroslav Bondarchuk
 *         Date: 21.10.13
 *         Time: 13:15
 */
@Controller
public class AttachmentUploader {
    private static Logger log = LoggerFactory.getLogger(AttachmentUploader.class);

    @ResponseBody
    @RequestMapping("/attachment-upload")
    public String upload(@RequestParam(value = "fileUpload") MultipartFile file) {

        InputStream inputStream = null;
        OutputStream outputStream = null;

        String fileName = file.getOriginalFilename();
        long time = System.nanoTime();
        String pathToSave = "";
        String savedFileName = fileName + "-_-" + time;
        try {
            Properties props = PropertiesLoaderUtils.loadAllProperties("deploy.properties");
            String attachmentStorage = props.getProperty("attachment.save.location");

            inputStream = file.getInputStream();
            pathToSave = attachmentStorage + savedFileName;
            File newFile = new File(pathToSave);
            if (!newFile.exists()) {
                newFile.createNewFile();
            }
            outputStream = new FileOutputStream(newFile);
            int read = 0;
            byte[] bytes = new byte[1024];

            while ((read = inputStream.read(bytes)) != -1) {
                outputStream.write(bytes, 0, read);
            }

        } catch (IOException e) {
            log.error("Error while uploading: " + e);
            return "";
        } finally {
            close(inputStream);
            close(outputStream);
        }
         return savedFileName;
    }

    private  void close(Closeable c) {
        if (c == null) return;
        try {
            c.close();
        } catch (IOException e) {
            log.error("Error while uploading: " + e);
        }
    }
}