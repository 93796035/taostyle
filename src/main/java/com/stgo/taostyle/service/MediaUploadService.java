package com.stgo.taostyle.service;

import com.stgo.taostyle.backend.security.TaoEncrypt;
import com.stgo.taostyle.backend.security.UserContextService;
import com.stgo.taostyle.domain.Customize;
import com.stgo.taostyle.domain.MediaUpload;
import com.stgo.taostyle.domain.Person;
import com.stgo.taostyle.domain.UserAccount;
import com.stgo.taostyle.repository.MediaUploadRepository;
import com.stgo.taostyle.service.dto.ImageData;
import com.stgo.taostyle.web.CC;
import com.stgo.taostyle.web.TaoImage;
import com.stgo.taostyle.web.TaoUtil;
import flexjson.JSONDeserializer;
import flexjson.JSONSerializer;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.multipart.commons.CommonsMultipartFile;

import javax.imageio.ImageIO;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.TypedQuery;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

@Service
public class MediaUploadService {
    private static final BigInteger SHARED_PRODUCT_FILE_OFFSET = BigInteger.valueOf(10754L);


    @Autowired
    private PersonService personService;
    @Autowired
    private CustomizeService customizeService;
    @Autowired
    private UserAccountService userAccountService;
    @Autowired
    private UserContextService userContextService;
    @Autowired
    private TaoUtil taoUtil;
    @Autowired
    private MediaUploadRepository mediaUploadRepository;

    // Count files by key and person
    @Transactional(readOnly = true)
    public long countMediaUploadsByKeyAndPerson(String pKey, Person person) {
        return mediaUploadRepository.countMediaUploadsByKeyAndPerson(pKey, person);
    }

    // List all media uploads by key and person
    @Transactional(readOnly = true)
    public List<MediaUpload> listAllMediaUploadsByKeyAndPerson(String pKey, Person person) {
        return mediaUploadRepository.listAllMediaUploadsByKeyAndPerson(pKey, person);
    }

    public List<ImageData> listAllMediaUploadsByPositionAndPerson(String pKey, Person person) {
        // 闂佸搫琚崕鎾敋濡ゅ啰绠旈柨鏇楀亾闁诡喖閰ｅ鍫曞灳閸欏鍋ㄩ梺?MediaUpload 闂佸憡甯楅〃澶愬Υ?
        List<MediaUpload> mediaUploads = mediaUploadRepository.listAllMediaUploadsByPositionAndPerson(pKey, person);

        // 闁哄鍎愰崜姘暦閸欏鈻?ImageData
        List<ImageData> imageDataList = new ArrayList<>();
        for(MediaUpload mediaUpload : mediaUploads){
            String filePath = mediaUpload.getFilepath();
            if(filePath.contains("thum")) {
                ImageData imageData = new ImageData();
                imageData.setThum(filePath);
                imageData.setBig(mediaUpload.getFilepath().substring(0, filePath.length() - 5));
                imageDataList.add(imageData);
            }
        }
        return imageDataList;
    }

    @Transactional
    public MediaUpload findMediaUpload(Long id) {
        if (id == null) return null;
        return mediaUploadRepository.findMediaUpload(id);
    }

    // Find media upload by key and person
    @Transactional(readOnly = true)
    public MediaUpload findMediaByKeyAndPerson(String key, Person person) {
        return mediaUploadRepository.findMediaByKeyAndPerson(key, person);
    }

    // Find media uploads by audient and person
    @Transactional(readOnly = true)
    public List<MediaUpload> findMediaByAudientAndPerson(UserAccount usr, Person person) {
        Customize customize = customizeService.findCustomizeByKeyAndPerson(CC.app_ContentManager, person);
        return mediaUploadRepository.findMediaByAudientAndPerson(usr, customize);
    }

    public List<String> listAllMediaUploadsKeyByKeyAndPerson(String pKey, Person person) {
        return mediaUploadRepository.listAllMediaUploadsKeyByKeyAndPerson(pKey, person);
    }

    // Find media uploads by author
    @Transactional(readOnly = true)
    public List<MediaUpload> findMediaByAuthor(UserAccount usr) {
        return mediaUploadRepository.findMediaByAuthor(usr);
    }

    // Find all media uploads by person
    @Transactional(readOnly = true)
    public List<MediaUpload> findAllMediaUploadByPerson(Person person) {
        return mediaUploadRepository.findAllMediaUploadByPerson(person);
    }

    @Transactional
    public void persist(MediaUpload mediaUpload) {
        mediaUploadRepository.persist(mediaUpload);
    }

    @Transactional
    public void remove(MediaUpload mediaUpload) {
        mediaUploadRepository.remove(mediaUpload);
    }

    @Transactional
    public MediaUpload merge(MediaUpload mediaUpload) {
        return mediaUploadRepository.merge(mediaUpload);
    }

    public String toJson(MediaUpload mediaUpload) {
        return new JSONSerializer()
                .exclude("*.class").serialize(mediaUpload);
    }

    public String toJson(String[] fields, MediaUpload mediaUpload) {
        return new JSONSerializer()
                .include(fields).exclude("*.class").serialize(mediaUpload);
    }

    public static MediaUpload fromJsonToMediaUpload(String json) {
        return new JSONDeserializer<MediaUpload>()
                .use(null, MediaUpload.class).deserialize(json);
    }

    public static String toJsonArray(Collection<MediaUpload> collection) {
        return new JSONSerializer()
                .exclude("*.class").serialize(collection);
    }

    public static String toJsonArray(Collection<MediaUpload> collection, String[] fields) {
        return new JSONSerializer()
                .include(fields).exclude("*.class").serialize(collection);
    }

    public static Collection<MediaUpload> fromJsonArrayToMediaUploads(String json) {
        return new JSONDeserializer<List<MediaUpload>>()
                .use("values", MediaUpload.class).deserialize(json);
    }

    @Transactional
    public List<MediaUpload> findAllMediaUploads() {
        return mediaUploadRepository.findAllMediaUploads();
    }

    @Transactional
    public void saveImageToDisk(Model uiModel, CommonsMultipartFile content, String fileName, MediaUpload media,
                                String tKeyStr, HttpServletRequest request, HttpServletResponse response) {

        uiModel.asMap().clear();
        String tFormat = media.getContentType();
        String originalFilename = content.getOriginalFilename();
        if (originalFilename != null && originalFilename.lastIndexOf(".") > -1) {
            tFormat = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        tFormat = tFormat.toLowerCase();
        media.setContentType(tFormat);
        BufferedImage inputImage;

        try {

            // webp special handling
            if (".webp".equalsIgnoreCase(tFormat)) {
                byte[] bytes = content.getBytes();
                inputImage = TaoImage.readWebp(bytes); // 闂傚倸娲犻崑鎾绘偡閺囨碍顦风紒妤€鎳樺Λ鍐閻樺樊娼辨繛鎴炴尨閸嬫挸鈽夐幙鍐х盎濞村吋鍔欏畷妤呮煥鐎ｎ偆鍘靛┑?
                tFormat = ".jpg"; // 缂傚倷鑳堕崰宥囩博鐎涙ɑ濮滄い鎺嶇鎼村﹤鈽夐幘鍓佹pg婵烇絽娲︾换鍌炴偤?
                media.setContentType(tFormat);
            } else {
                ImageIO.scanForPlugins();
                inputImage = ImageIO.read(content.getInputStream());
            }
            if (inputImage == null) {
                logError("Failed to read input image from content", null);
                uiModel.addAttribute("error", "Failed to save image: invalid image file.");
                return;
            }

            // 缂傚倸鍊甸弲婊堝棘娓氣偓閻涱噣鎳滈崹顐闁哄鍎愰崜娆徝瑰鈧畷?
            BufferedImage bufferedImage = ".png".equalsIgnoreCase(tFormat)
                    ? inputImage
                    : TaoImage.resizeAndRotateImage(request, inputImage, media.getFilepath());
            if (bufferedImage == null) {
                logError("Failed to resize and rotate image", null);
                uiModel.addAttribute("error", "Failed to process image.");
                return;
            }

            // 闂佸吋鍎抽崲鑼躲亹?personId 闂佺儵鏅╅崰鏍礊瀹ュ宓侀柛鎾茬劍閺呮悂鏌熺€涙ê濮堥柡瀣暞缁傛帡骞嗛幍顔惧敶閻?
            String filePath = generateFilePathForPerson(media.getPerson(), tKeyStr, media.getContentType());

            // 闂佸憡甯楃粙鎴犵磽閹捐妫橀柛銉檮椤愯姤顨ラ悙瀛樻瀯缂佺粯姘ㄩ埀顒佺⊕閿氭繛瀛橈耿瀹?
            saveFileToDisk(bufferedImage, tFormat, filePath);

            // 闁荤姳绀佹晶浠嬫偪閸℃稑妫橀柛銉檮椤愯棄顭块崼鍡楀暟濮?
            media.setFilesize(content.getSize());
            mediaUploadRepository.persist(media);

            // 婵犮垼娉涚€氼噣骞冩繝鍕＝闁冲搫鍟▓锝夋煕?
            processThumbnail(uiModel, request, media, inputImage, tKeyStr);

        } catch (IOException e) {
            logError("IOException while saving image", e);
            uiModel.addAttribute("error", "Failed to save image to disk");
        } catch (Exception e) {
            logError("Exception while processing image", e);
            uiModel.addAttribute("error", "Unexpected error occurred.");
        }
    }

    public String generateFilePathForPerson(Person person, String tKeyStr, String contentType) {
        Long personId = person.getId();
        String personDirectory = new File(File.listRoots()[0], "media" + File.separator + personId).getPath() + File.separator;

        Path directoryPath = Paths.get(personDirectory);
        try {
            Files.createDirectories(directoryPath);
        } catch (IOException e) {
            logError("Failed to create directory: " + personDirectory, e);
        }

        return personDirectory + tKeyStr + (contentType !=null ? contentType : "");
    }


    public String generateFilePathForSharedProduct(String barcode, String contentType) {
        String normalizedBarcode = buildSharedProductFileStem(barcode);
        if (normalizedBarcode == null || normalizedBarcode.isEmpty()) {
            return null;
        }
        String productDirectory = new File(File.listRoots()[0], "media" + File.separator + "product").getPath() + File.separator;

        Path directoryPath = Paths.get(productDirectory);
        try {
            Files.createDirectories(directoryPath);
        } catch (IOException e) {
            logError("Failed to create directory: " + productDirectory, e);
        }

        return productDirectory + normalizedBarcode + (contentType != null ? contentType : "");
    }

    public File findSharedProductImageFile(String barcode, boolean thumbRequested) {
        if (barcode == null || barcode.trim().isEmpty()) {
            return null;
        }

        String trimmedBarcode = barcode.trim();
        String productDirectory = new File(File.listRoots()[0], "media" + File.separator + "product").getPath() + File.separator;

        List<String> candidateKeys = new ArrayList<String>();
        String newStem = buildSharedProductFileStem(trimmedBarcode);
        if (newStem != null && !newStem.isEmpty()) {
            candidateKeys.add(thumbRequested ? newStem + "_thum" : newStem);
        }
        candidateKeys.add(thumbRequested ? trimmedBarcode + "_thum" : trimmedBarcode);

        for (String candidateKey : candidateKeys) {
            for (String ext : new String[]{".png", ".jpg", ".jpeg", ".webp"}) {
                File file = new File(productDirectory + candidateKey + ext);
                if (file.exists()) {
                    return file;
                }
            }
        }
        return null;
    }

    private String buildSharedProductFileStem(String barcode) {
        if (barcode == null) {
            return null;
        }
        String sanitized = barcode.trim();
        if (sanitized.isEmpty()) {
            return null;
        }
        sanitized = sanitized.replaceAll("[\\\\/:*?\"<>|]", "_");
        if (sanitized.matches("\\d+")) {
            return "m_" + new BigInteger(sanitized).add(SHARED_PRODUCT_FILE_OFFSET).toString();
        }
        return "m_x_" + sanitized;
    }
    private void saveFileToDisk(BufferedImage image, String format, String filePath) throws IOException {
        File diskFile = new File(filePath);
        try {
            ImageIO.write(image, format.substring(1), diskFile);  // format like ".png"
        } catch (IOException e) {
            logError("Failed to save file to disk: " + filePath, e);
            throw e;
        }
    }

    private void processThumbnail(Model uiModel, HttpServletRequest request, MediaUpload media,
                                  BufferedImage inputImage, String tKeyStr) {
        if (taoUtil.isTHUMNeeded(request, tKeyStr, media.getPerson())) {
            String thumbFilePath = media.getFilepath() + "_thum";

            MediaUpload thumbMedia = findMediaByKeyAndPerson(thumbFilePath, media.getPerson());
            if (thumbMedia == null) {
                thumbMedia = new MediaUpload();
                thumbMedia.setPerson(media.getPerson());
                thumbMedia.setFilepath(thumbFilePath);
                thumbMedia.setContentType(media.getContentType());
            }

            try {
                BufferedImage thumbnail = TaoImage.resizeAndRotateImage(request, inputImage, thumbFilePath);
                if (thumbnail != null) {
                    String thumbFilePathOnDisk = generateFilePathForPerson(media.getPerson(), thumbFilePath, media.getContentType());
                    saveFileToDisk(thumbnail, media.getContentType(), thumbFilePathOnDisk);
                    thumbMedia.setFilesize(Files.size(Paths.get(thumbFilePathOnDisk)));
                    mediaUploadRepository.persist(thumbMedia);
                }
            } catch (IOException e) {
                logError("Failed to save thumbnail image", e);
                uiModel.addAttribute("error", "Failed to save thumbnail image");
            }

        }
    }

    private void logError(String message, Exception e) {
        if (e != null) {
            System.err.println(message + ": " + e.getMessage());
            e.printStackTrace();
        } else {
            System.err.println(message);
        }
    }


    public void saveImageToDB(Model uiModel, CommonsMultipartFile content, String fileName, MediaUpload media,
                                 String tKeyStr, HttpServletRequest request, HttpServletResponse response) {

        uiModel.asMap().clear();

        String tFormat = media.getContentType();

        BufferedImage inputImage = null;
        try {
            ImageIO.scanForPlugins();
            inputImage = ImageIO.read(content.getInputStream());
            if (inputImage == null) {
                System.out.println("ERROR!!!! got null inputImage when read from content!");
            }
            // Image big = inputImage.getScaledInstance(256, 256,Image.SCALE_DEFAULT);

            BufferedImage bufferedImage = ".png".equalsIgnoreCase(tFormat) ? inputImage
                    : TaoImage.resizeAndRotateImage(request, inputImage, media.getFilepath()); // because when uploading gallery,
            // the tKeyString is like
            // gallery_".
            if (bufferedImage == null) {
                System.out.println("ERROR!!!! got null bufferedImage after resizing image!");
            }
            byte[] tContent = TaoImage.getByteFormateImage(bufferedImage, tFormat);
            media.setContent(tContent == null ? content.getBytes() : tContent);

            media.setFilesize(tContent == null ? content.getSize() : tContent.length);
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("got exception when resizing the image!" + e);
        }

        mediaUploadRepository.persist(media);

        // --------------------------for Thum image---------------------------
        if (taoUtil.isTHUMNeeded(request, tKeyStr, media.getPerson())) {
            String filPathForThumb = media.getFilepath() + "_thum";

            MediaUpload mediaThumb = findMediaByKeyAndPerson(filPathForThumb, media.getPerson());
            if (mediaThumb == null) {
                mediaThumb = new MediaUpload();
                mediaThumb.setPerson(media.getPerson());
                mediaThumb.setFilepath(filPathForThumb);
            }
            try {
                // Image big = inputImage.getScaledInstance(256, 256,Image.SCALE_DEFAULT);
                inputImage = TaoImage.resizeAndRotateImage(request, inputImage, filPathForThumb);
                byte[] tContent = TaoImage.getByteFormateImage(inputImage, tFormat);

                mediaThumb.setContent(tContent); // ------------file content----------
                mediaThumb.setFilesize(tContent.length); // ------------file size-------------
                mediaThumb.setContentType(tFormat);
            } catch (Exception e) {
                e.printStackTrace();
                System.out.println("got exception when resizing the Thumimage!" + e);
            }
            mediaUploadRepository.persist(mediaThumb);
        }
    }

    public String saveDocumentToDB(Model uiModel, CommonsMultipartFile content, String fileName, MediaUpload media,
                                    String keyString, Person person) {
        if (StringUtils.isBlank(fileName)) {
            uiModel.addAttribute("notice", "Nothing will be uploaded because of no file was selected!");
            return "redirect:/dashboard";
        }
        // audient: leave to be null. all mediaUpload with publisher will be visible by
        // GE anyway.
        // String pUserName = session.getAttribute(CC.app_ContentManager);
        // tMedia.setAudient(userAccountService.findUserAccountByName(pUserName));
        // publisher
        String tUserName = userContextService.getCurrentUserName();
        UserAccount userAccount = userAccountService.findUserAccountByName(TaoEncrypt.enrichName(tUserName, person));
        media.setPublisher(userAccount);
        // submitDate
        media.setSubmitDate(new Date());
        // filePath
        String tFilePath = TaoEncrypt.stripUserName(tUserName) + "_";
        int pos = fileName.indexOf("_");
        if (pos > 0) {
            String authorName = fileName.substring(0, pos);
            UserAccount author = userAccountService.findUserAccountByName(TaoEncrypt.enrichName(authorName, person));
            if (author != null) {
                tFilePath = ""; // the file name is already starts with a users name, then do not add name on to
                // it.
            }
        }
        tFilePath = tFilePath + fileName;
        pos = tFilePath.indexOf(".");// remove the type from file path.
        if (pos > 0)
            tFilePath = tFilePath.substring(0, pos);

        pos = tFilePath.indexOf("_V");// remove the version from file path.
        if (pos > 0 && StringUtils.isNumeric(tFilePath.substring(pos + 2)))
            tFilePath = tFilePath.substring(0, pos);

        long tNum = countMediaUploadsByKeyAndPerson(tFilePath, person);
        if (tNum > 0) {
            tFilePath = tFilePath + "_V" + tNum;
        }
        media.setFilepath(tFilePath);
        // status
        media.setStatus(CC.STATUS_NEW);
        // content and fileSize
        media.setContent(content != null ? content.getBytes() : null); // ------------file content----------
        media.setFilesize(content != null ? content.getSize() : 0); // ------------file size-------------
        media.setPerson(person);
        mediaUploadRepository.persist(media);

        uiModel.asMap().clear();
        if ("personalDocument".equals(keyString)) {
            return "redirect:/dashboard";
        } else {
            uiModel.addAttribute("notice", fileName
                    + "is submitted successfully! Please feel free uploading more documents, or input in the editor directly.");
            return "sendEmail";
        }
    }

    public void saveJobApplicationToDB(Model uiModel, CommonsMultipartFile content, String fileName,
                                          MediaUpload media, HttpServletRequest request, HttpServletResponse response, Person person) {
        // always create new user, in case some one mistyped with client's email, thus,
        // data will be ruined.
        UserAccount userAccount = createANewUserForJobApplier(request, person);
        media.setPublisher(userAccount);

        // leave audient to be null. all mediaUpload with publisher will be visible by
        // GE anyway.
        // String pUserName = session.getAttributee(CC.app_ContentManager);
        // tMedia.setAudient(UserAccount.findUserAccountByName(pUserName));

        media.setSubmitDate(new Date());

        // filePath
        int pos = fileName.lastIndexOf('.');// remove the type from file path.
        if (pos > 0) {
            fileName = fileName.substring(0, pos) + "_V";
        }
        long tNum = countMediaUploadsByKeyAndPerson(fileName, person);
        if (tNum >= 0) {
            fileName = fileName + tNum;
        }

        media.setFilepath(fileName);
        media.setStatus(CC.STATUS_NEW);
        media.setContent(content != null ? content.getBytes() : null); // ------------file content----------
        media.setFilesize(content != null ? content.getSize() : 0); // ------------file size-------------
        mediaUploadRepository.persist(media);

        uiModel.asMap().clear();
        uiModel.addAttribute("email", userAccount.getEmail());
    }

    private UserAccount createANewUserForJobApplier(HttpServletRequest request, Person person) {

        String email = request.getParameter("email");
        String loginName = StringUtils.isBlank(email) ? String.valueOf(Calendar.getInstance().getTimeInMillis())
                : email;
        loginName = TaoEncrypt.enrichName(loginName, person);
        String password = TaoEncrypt.encryptPassword(person.getName());
        UserAccount existingOne = userAccountService.findUserAccountByName(loginName);
        if (existingOne != null) {
            return existingOne;
        }

        UserAccount userAccount = new UserAccount();
        userAccount.setPerson(person);
        userAccount.setAddress(request.getParameter("address"));
        userAccount.setCel(request.getParameter("mobile"));
        userAccount.setCity(request.getParameter("city"));
        userAccount.setEmail(email);
        userAccount.setLoginname(loginName);
        userAccount.setPassword(password);
        userAccountService.persist(userAccount);
        return userAccount;
    }

    @Transactional
    public void mediaMoving(long personId) {
        List<MediaUpload> mediaUploads = findAllMediaUploadByPerson(personService.findPerson(personId));

        for (MediaUpload media : mediaUploads) {
            byte[] content = media.getContent();

            if (media.getPerson().getId() > 2 && media.getPerson().getId() != 39 && content != null && content.length > 0) {
                try {
                    String filePath = generateFilePathForPerson(media.getPerson(), media.getFilepath(), media.getContentType());

                    File file = new File(filePath);
                    FileOutputStream fos = new FileOutputStream(file);
                    fos.write(content);
                    fos.flush();
                    fos.close();

                    media.setContent(null);
                    mediaUploadRepository.merge(media); // 闂佸搫娲ら悺銊╁蓟婵犲洤鏋侀柣妤€鐗嗙粊锕傚箹?

                    System.out.println("Moved content to disk and cleared content for media ID: " + media.getPerson().getId() + ":" + media.getId());
                } catch (Exception e) {
                    e.printStackTrace();
                    System.out.println("Failed to move content for media ID: " + media.getId());
                }
            }
        }
    }
}

