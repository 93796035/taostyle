package com.stgo.taostyle.web;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.imageio.ImageIO;

import com.stgo.taostyle.backend.security.TaoEncrypt;
import com.stgo.taostyle.domain.Customize;
import com.stgo.taostyle.domain.Feature;
import com.stgo.taostyle.domain.MediaUpload;
import com.stgo.taostyle.domain.Person;
import com.stgo.taostyle.domain.Service;
import com.stgo.taostyle.domain.TextContent;
import com.stgo.taostyle.domain.UserAccount;
import com.stgo.taostyle.domain.orders.MainOrder;
import com.stgo.taostyle.domain.orders.Material;
import com.stgo.taostyle.domain.orders.TaxonomyMaterial;

import com.stgo.taostyle.service.*;
import flexjson.JSONDeserializer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class TaoDbUtil {

    private static final String HEADER_CONTENT_TYPE = "Content-Type";
    private static final String EXPORTER_HTTP_METHOD = "GET";

    @Autowired
    private PersonService personService;
    @Autowired
    private CustomizeService customizeService;
    @Autowired
    private MediaUploadService mediaUploadService;
    @Autowired
    private TextContentService textContentService;
    @Autowired
    private FeatureService featureService;
    @Autowired
    private ServiceService serviceService;
    @Autowired
    private UserAccountService userAccountService;
    @Autowired
    private MaterialService materialService;
    @Autowired
    private MainOrderService mainOrderService;
    @Autowired
    private TaxonomyMaterialService taxonomyMaterialService;


    public int saveToLocalDB(
            Person sourcePerson,
            List<String> tList,
            String url,
            Person curPerson) {
        // clean existing db content of person.
        cleanPersonRecords(curPerson);

        HashMap<String, UserAccount> userMap = new HashMap<String, UserAccount>();
        HashMap<Long, MainOrder> mainOrderMap = new HashMap<Long, MainOrder>();

        if (!saveUserAccountToLocalDB(curPerson, tList.get(0), userMap)) // useraccount
            return 0;
        if (!saveCustomizesToLocalDB(curPerson, tList.get(1))) // customize
            return 1;
        if (!saveFeatureToLocalDB(curPerson, tList.get(2))) // feature
            return 2;
        // if (!saveMainOrdersToLocalDB(curPerson, tList.get(3), mainOrderMap, userMap)) // mainOrder
        // return 3;
        // if (!saveMaterialsToLocalDB(curPerson, tList.get(4), mainOrderMap)) // material
        // return 4;
        if (!saveTaxonomyMaterialsToLocalDB(curPerson, tList.get(5))) // taxonomyMaterial
            return 5;
        if (!saveServicesToLocalDB(curPerson, tList.get(6))) // service
            return 6;
        if (!saveTextContentsToLocalDB(curPerson, tList.get(7))) // textContent
            return 7;
        if (!saveMediaUploadsToLocalDB(sourcePerson.getName(), tList.get(8), url, curPerson)) // uploadMedia
            return 8;

        return -1;
    }

    public void cleanPersonRecords(
            Person person) {
        List<Customize> customizes = customizeService.findAllCustomizesByPerson(person);
        for (Customize customize : customizes) {
            customizeService.remove(customize);
        }

        List<TextContent> textContents = textContentService.findAllMatchedTextContents("%", null, person);
        for (TextContent textContent : textContents) {
            textContentService.remove(textContent);
        }

        List<MediaUpload> mediaUploads = mediaUploadService.findAllMediaUploadByPerson(person);
        for (MediaUpload mediaUpload : mediaUploads) {
            mediaUploadService.remove(mediaUpload);
        }

        List<Feature> features = featureService.findAllFeaturesByPerson(person);
        for (Feature feature : features) {
            featureService.remove(feature);
        }

        List<Service> services = serviceService.findServiceByPerson(person);
        for (Service service : services) {
            serviceService.deleteService(service);
        }

        List<MainOrder> mainOrders = mainOrderService.findMainOrdersByPerson(person, "DESC");
        for (MainOrder mainOrder : mainOrders) {
            List<Material> materials = materialService.findAllMaterialsByMainOrder(mainOrder);
            for (Material material : materials) {
                materialService.remove(material);
            }
            List<TaxonomyMaterial> taxonomyMaterials = taxonomyMaterialService.findAllTaxonomyMaterialsByMainOrder(mainOrder);
            for (TaxonomyMaterial taxonomyMaterial : taxonomyMaterials) {
                taxonomyMaterialService.remove(taxonomyMaterial);
            }
            mainOrderService.remove(mainOrder);
        }

        List<UserAccount> userAccounts = userAccountService.findAllUserAccountsByPerson(person);
        for (UserAccount userAccount : userAccounts) {
            mediaUploads = mediaUploadService.findMediaByAuthor(userAccount);
            for (MediaUpload mediaUpload : mediaUploads) {
                mediaUploadService.remove(mediaUpload);
            }
            userAccountService.remove(userAccount);
        }
    }

    private boolean saveUserAccountToLocalDB(
            Person person,
            String json,
            HashMap<String, UserAccount> userMap) {
        List<UserAccount> pList = new JSONDeserializer<List<UserAccount>>().use(null, ArrayList.class)
                .use("values", UserAccount.class).deserialize(json);

        System.out.println(
                "start to save UserAccount! there are [" + pList.size() + "] items to save---------------------");

        for (int i = 0; i < pList.size(); i++) {
            System.out.println(i);
            UserAccount pUA = pList.get(i);
            String oldLoginname = pUA.getLoginname();
            int p = oldLoginname.indexOf("*");
            String newLoginname = oldLoginname.substring(0, p + 1) + person.getId();
            UserAccount tUA = userAccountService.findUserAccountByName(newLoginname);
            if (tUA != null) { // have same one. update properties
                TaoDebug.error(
                        "UserAccount not cleaned well before saving exported db into local db. useraccount name: {}, person: {}",
                        newLoginname);
                userAccountService.remove(tUA);
            }
            pUA.setLoginname(newLoginname);
            pUA.setPerson(person);
            pUA.setId(null); // make the Id null, or the recode with that ID will be replaced.
            userAccountService.persist(pUA);
            userMap.put(oldLoginname, pUA);
        }
        return true;
    }

    private boolean saveCustomizesToLocalDB(
            Person person,
            String json) {
        List<Customize> items = new JSONDeserializer<List<Customize>>().use(null, ArrayList.class)
                .use("values", Customize.class).deserialize(json);

        System.out.println(
                "start to save customizes! there are [" + items.size() + "] items to save---------------------");

        for (int i = 0; i < items.size(); i++) {
            System.out.println(i);
            Customize instance = items.get(i);
            Customize customize = customizeService.findCustomizeByKeyAndPerson(instance.getCusKey(), person);
            if (customize != null) { // have same one. update properties
                TaoDebug.error(
                        "Customize not cleaned well before saving exported db into local db. Customize key: {}, person: {}",
                        instance.getCusKey());
                customizeService.remove(customize);
            }
            instance.setPerson(person);
            instance.setId(null); // make the Id null, or the recode with that ID will be replaced.
            customizeService.persist(instance);
        }
        return true;
    }

    private boolean saveFeatureToLocalDB(
            Person person,
            String json) {
        List<Feature> items = new JSONDeserializer<List<Feature>>().use(null, ArrayList.class)
                .use("values", Feature.class).deserialize(json);

        System.out
                .println("start to save Feature! there are [" + items.size() + "] items to save---------------------");

        for (int i = 0; i < items.size(); i++) {
            System.out.println(i);
            Feature instance = items.get(i);
            instance.setPerson(person);
            instance.setId(null); // make the Id null, or the recode with that ID will be replaced.
            featureService.persist(instance);
        }
        return true;
    }

    private boolean saveMainOrdersToLocalDB(
            Person person,
            String json,
            HashMap<Long, MainOrder> mainOrderMap,
            HashMap<String, UserAccount> userMap) {
        List<MainOrder> items = new JSONDeserializer<List<MainOrder>>().use(null, ArrayList.class)
                .use("values", MainOrder.class).deserialize(json);

        System.out.println(
                "start to save MainOrder! there are [" + items.size() + "] items to save---------------------");

        for (int i = 0; i < items.size(); i++) {
            System.out.println(i);
            MainOrder instance = items.get(i);
            String loginName = instance.getContactPerson().getLoginname();
            instance.setContactPerson(userMap.get(loginName));
            mainOrderMap.put(instance.getId(), instance);
            instance.setPerson(person);
            instance.setId(null); // make the Id null, or the recode with that ID will be replaced.
            mainOrderService.persist(instance);
        }
        return true;
    }

    private boolean saveMaterialsToLocalDB(
            Person person,
            String json,
            HashMap<Long, MainOrder> mainOrderMap) {
        List<Material> items = new JSONDeserializer<List<Material>>().use(null, ArrayList.class)
                .use("values", Material.class).deserialize(json);

        System.out
                .println("start to save Material! there are [" + items.size() + "] items to save---------------------");

        for (int i = 0; i < items.size(); i++) {
            System.out.println(i);
            Material instance = items.get(i);
            instance.setMainOrder(mainOrderMap.get(instance.getMainOrder().getId()));
            instance.setPerson(person);
            instance.setId(null); // make the Id null, or the recode with that ID will be replaced.
            materialService.persist(instance);
        }
        return true;
    }

    private boolean saveTaxonomyMaterialsToLocalDB(
            Person person,
            String json) {
        List<TaxonomyMaterial> items = new JSONDeserializer<List<TaxonomyMaterial>>().use(null, ArrayList.class)
                .use("values", TaxonomyMaterial.class).deserialize(json);

        System.out.println(
                "start to save TaxonomyMaterial! there are [" + items.size() + "] items to save---------------------");

        for (int i = 0; i < items.size(); i++) {
            System.out.println(i);
            TaxonomyMaterial instance = items.get(i);
            instance.setPerson(person);
            instance.setId(null); // make the Id null, or the recode with that ID will be replaced.
            taxonomyMaterialService.persist(instance);
        }
        return true;
    }

    private boolean saveServicesToLocalDB(
            Person person,
            String json) {
        List<Service> items = new JSONDeserializer<List<Service>>().use(null, ArrayList.class)
                .use("values", Service.class).deserialize(json);

        System.out
                .println("start to save Service! there are [" + items.size() + "] items to save---------------------");

        for (int i = 0; i < items.size(); i++) {
            System.out.println(i);
            Service instance = items.get(i);
            instance.setPerson(person);
            instance.setId(null); // make the Id null, or the recode with that ID will be replaced.
            serviceService.persist(instance);
        }
        return true;
    }

    private boolean saveTextContentsToLocalDB(
            Person person,
            String json) {
        List<TextContent> items = new JSONDeserializer<List<TextContent>>().use(null, ArrayList.class)
                .use("values", TextContent.class).deserialize(json);

        System.out.println(
                "start to save TextContent! there are [" + items.size() + "] items to save---------------------");

        for (int i = 0; i < items.size(); i++) {
            System.out.println(i);
            TextContent instance = items.get(i);
            instance.setPerson(person);
            instance.setId(null); // make the Id null, or the recode with that ID will be replaced.
            textContentService.persist(instance);
        }
        return true;
    }

    private boolean saveMediaUploadsToLocalDB(
            String sourcePerson,
            String json,
            String url,
            Person curPerson) {
        List<MediaUpload> items = new JSONDeserializer<List<MediaUpload>>().use(null, ArrayList.class)
                .use("values", MediaUpload.class).deserialize(json);

        System.out.println(
                "start to save MediaUpload! there are [" + items.size() + "] items to save---------------------");

        for (int i = 0; i < items.size(); i++) {
            System.out.println(i);
            MediaUpload instance = items.get(i);

            // send out request for the picture, when got the response, save it into the db.
            String version = "exportImage";
            String label = instance.getFilepath();
            StringBuilder msg = new StringBuilder("");

            StringBuilder enrichedURL = new StringBuilder(url + "security") //
                    .append("?version=").append(version) //
                    .append("&hostId=").append(sourcePerson) //
                    .append("&label=").append(label) //
                    .append("&message=").append(msg)//
                    .append("&time=").append("");//
            byte[] content = reqeustImageContentFromServer(enrichedURL.toString(), label, sourcePerson,
                    instance.getContentType());
            if(content != null) {
	            instance.setContent(content);
	            instance.setPerson(curPerson);
	            instance.setId(null); // make the Id null, or the recode with that ID will be replaced.
                mediaUploadService.persist(instance);
            }
        }
        return true;
    }

    private static byte[] reqeustImageContentFromServer(
            String url,
            String label,
            String personName,
            String tFormat) {
        HttpURLConnection urlConnection = null;
        byte[] byteAry = null;
        try {
            url = url.replace(" ", "%20");

            urlConnection = prepareConnection(url);
            int responseCode = urlConnection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                InputStream inputStream = urlConnection.getInputStream();
                // TODO: return the byte[]
                BufferedImage inputImage = ImageIO.read(inputStream);
                byteAry = TaoImage.getByteFormateImage(inputImage, tFormat);
            } else {
                TaoDebug.error(
                        "WARNING_connection to server not returning unexpected code :{}, when sending hostID: {}, label:{}",
                        responseCode, personName, label);
            }
        } catch (Exception e) {
            TaoDebug.error(
                    "WARNING_unexpected exception when creating connection to server: {}, with hostID:{}, label: {}",
                    url, personName, label);
        } finally {
            if (urlConnection != null) {
                urlConnection.disconnect();// 使用完关闭TCP连接，释放资源
            }
        }
        return byteAry;
    }

    private static HttpURLConnection prepareConnection(
            String uri) throws Exception {
        HttpURLConnection urlConnection = (HttpURLConnection) new URL(uri).openConnection();
        // urlConnection.setConnectTimeout(3000);// 连接的超时时间
        // urlConnection.setUseCaches(false);// 不使用缓存
        // urlConnection.setFollowRedirects(false);是static函数，作用于所有的URLConnection对象。
        // urlConnection.setInstanceFollowRedirects(true);// 是成员函数，仅作用于当前函数,设置这个连接是否可以被重定向
        // urlConnection.setReadTimeout(3000);// 响应的超时时间
        urlConnection.setRequestMethod(EXPORTER_HTTP_METHOD);// 设置请求的方式
        urlConnection.addRequestProperty(HEADER_CONTENT_TYPE, "application/octet-stream");
        urlConnection.setDoInput(true);// 设置这个连接是否可以写入数据
        urlConnection.setDoOutput(true);// 设置这个连接是否可以输出数据
        urlConnection.connect();// 连接，从上述至此的配置必须要在connect之前完成，实际上它只是建立了一个与服务器的TCP连接
        return urlConnection;
    }

	public Person verifyAccountInfo(String accountInfo) {
		int pos = accountInfo.indexOf(":");
        if (pos < 1)
            return null;

        String personName = accountInfo.substring(0, pos);
        Person person = personService.findPersonByName(personName);
        if (person == null)
            return null;

        String password = TaoEncrypt.stripURLFriendlyPassword(accountInfo.substring(pos + 1));
        if (!person.getPassword().equals(password)) // directly compare with the encrypt password.
            return null;
        
		return person;
	}
}
