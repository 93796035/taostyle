package com.stgo.taostyle.web;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import javax.imageio.ImageIO;
import javax.servlet.http.HttpServletRequest;

import com.stgo.taostyle.service.MediaUploadService;
import com.stgo.taostyle.service.ProductService;
import com.stgo.taostyle.service.ServiceService;
import com.stgo.taostyle.service.TextContentService;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.lang3.StringUtils;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.Tag;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.google.zxing.common.BitMatrix;
import com.stgo.taostyle.domain.MediaUpload;
import com.stgo.taostyle.domain.Person;
import com.stgo.taostyle.domain.Product;
import com.stgo.taostyle.domain.Service;
import com.stgo.taostyle.domain.TextContent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class TaoImage {

    private static final int BLACK = 0xFF000000;
    private static final int WHITE = 0xFFFFFFFF;

    private static final int OPERATAION_SCALE = 0;
    private static final int OPERATAION_CUT = 1;

    private static final int MinDiffForOrentationCheck = 2;// means if the diff bigger than 2/10, then check.

    @Autowired
    private MediaUploadService mediaUploadService;
    @Autowired
    private TextContentService textContentService;
    @Autowired
    private ServiceService serviceService;
    @Autowired
    private ProductService productService;

    static BufferedImage toBufferedImage(
            BitMatrix matrix) {
        int width = matrix.getWidth();
        int height = matrix.getHeight();
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                image.setRGB(x, y, matrix.get(x, y) ? BLACK : WHITE);
            }
        }
        return image;
    }

    private static void writeToFile(
            BitMatrix matrix,
            String format,
            File file) throws IOException {
        BufferedImage image = toBufferedImage(matrix);
        if (!ImageIO.write(image, format, file)) {
            throw new IOException("Could not write an image of format " + format + " to " + file);
        }
    }

    private static void writeToStream(
            BitMatrix matrix,
            String format,
            OutputStream stream) throws IOException {
        BufferedImage image = toBufferedImage(matrix);
        if (!ImageIO.write(image, format, stream)) {
            throw new IOException("Could not write an image of format " + format);
        }
    }

    
    public static int getBitmapDegree(FileItem tFileItem ) {
    	try {
    		Metadata metadata = ImageMetadataReader.readMetadata(tFileItem.getInputStream());
    		
	    	for (Directory directory : metadata.getDirectories()) {
	    		if("Exif IFD0".equals(directory.getName())){
	    			int orientation = directory.getInt(ExifIFD0Directory.TAG_ORIENTATION);
    				System.out.println("orientation|"+orientation);
	    			for (Tag tag : directory.getTags()) {
	    				System.out.println(tag +"|"+tag.getTagType());
	    			}
	    		}
	    	}
    	}catch(Exception e) {
    		System.out.println("............");
    		//L.e("TaoImage", "exception when reading the matadata of an fileItem", e);
    	}
    	return 0;
    }
    
    // Since we use servlet URL as image src property. so the full image name is not needed any more.
    // public static void initImage(Model model, String pKey, int pNum){
    // for(int i = 1; i <= pNum; i++){
    // String tKey = pKey + i;
    // String tType = MediaUpload.findMediaByKey(tKey).getContentType();
    //
    // if(StringUtils.isBlank(tType)){
    // tType = DEFAULT_IMAGE_TYPE;
    // }
    // model.addAttribute(tKey, tKey + tType);
    // //System.out.println("tKey:"+ tKey + " value:" + tKey + tType);
    // }
    // }

    public static BufferedImage resizeAndRotateImage(
            HttpServletRequest request,
            BufferedImage im,
            String keyString) {

        int[] size = null;
        String prf = null;

        if (keyString.contains("service_")) {
            if (keyString.indexOf("_thum") > 0) {
                size = new int[] { 144, 192 };
                prf = "img_service_thum_";
            } else {
                size = new int[] { 720, 960 };
                prf = "img_service_";
            }
        } else if (keyString.contains("gallery_")) { // for the gallery pictures.
            if (keyString.indexOf("_thum") > 0) {
                size = new int[] { 160, 107 };
                prf = "img_gallery_thum_";
            } else {
                size = new int[] { 1072, 712 };
                prf = "img_gallery_";
            }
        } else if (keyString.contains("QRCode_")) {
            size = new int[] { 300, 300 };
            prf = "Qrcode_";
        } else if (keyString.contains("product_")) {
            if (keyString.indexOf("_thum") > 0) {
                size = new int[] { 144, 192 };
                prf = "img_product_thum_";
            } else if (keyString.endsWith("_2d")) {
                size = new int[] { 207, 70 };
                prf = "img_product_2d_";
            } else if (keyString.endsWith("_3d")) {
                size = new int[] { 207, 70 };
                prf = "img_product_3d_";
            } else {
                size = new int[] { 720, 960 };
                prf = "img_product_";
            }
        } else if (keyString.contains("promotion_")) { // the three promotion impages on main page.
            size = new int[] { 576, 160 };
            prf = "img_promotion_";
        } else if (keyString.contains("bg_")) { // the three bg pictures for flash page, main page, and subpage.
            size = new int[] { 1920, -1 };
            prf = "img_bg_";
        } else if (keyString.contains("logo")) {
            size = new int[] { -1, 40 };
            prf = "img_logo_";
            im = rotateImageAndFillSize(request, im, prf + CC.W, prf + CC.H, size, false);
            im = makeImageWithSize(im, size); // scale operation will executed.
            size = new int[] { 200, -1 };
        } else if (keyString.contains("log_") || keyString.contains("firstLine_") || keyString.contains("category_")) {
            size = new int[] { 300, -1 };
            prf = keyString.substring(0, keyString.indexOf('_') + 1);
            prf = "img_" + prf;
            im = rotateImageAndFillSize(request, im, prf + CC.W, prf + CC.H, size, false);
            im = makeImageWithSize(im, size); // scale operation will executed.
            size = new int[] { -1, 200 };// cut if too high instead of scale.
            return makeImageWithSize(im, size);
        } else if (keyString.contains("slide_superBig_")) { // the slide pictures on the hero area of main page.
        	int maxWidth = 1920;
        	try {
        		maxWidth = Integer.valueOf((String)request.getSession().getAttribute(CC.maxMainImageWidth));
        	}catch(Exception e) {
        		//do nothing
        	}
            size = new int[] { maxWidth, -1 };
            im = rotateImageAndFillSize(request, im, "img_slide_big_w", "img_slide_big_h", size, false);
            im = makeImageWithSize(im, size); // scale operation will executed.
            

        	int maxHeiht = 600;
        	try {
        		maxHeiht = Integer.valueOf((String)request.getSession().getAttribute(CC.maxMainImageHeight));
        	}catch(Exception exp) {
        		//do nothing
        	}
            size = new int[] { -1, maxHeiht }; // negative parameter will result in cut
            return makeImageWithSize(im, size);
        } else if (keyString.contains("html_")) {
            size = new int[] { 1000, 600 };
            im = rotateImageAndFillSize(request, im, "img_html_w", "img_html_h", size, false);
            im = makeImageWithSize(im, size); // scale operation will executed.
            size = new int[] { -1, 600 }; // negative parameter will result in cut
            return makeImageWithSize(im, size);
        } else if(keyString.contains(CC.defaultservice)){
            size = new int[] { 144, 192 };
            prf = "img_service_thum_";
        } else {
            return null;
        }
        im = rotateImageAndFillSize(request, im, prf + CC.W, prf + CC.H, size, false);
        return makeImageWithSize(im, size);
    }

    private static BufferedImage rotateImageAndFillSize(
            HttpServletRequest request,
            BufferedImage im,
            String keyWidth,
            String keyHeight,
            int[] size,
            boolean isHeightFirst) {

        int widthLimit = size[0];
        int heightLimit = size[1];
        widthLimit = getCustValueWithDefalt(request, keyWidth, widthLimit);
        heightLimit = getCustValueWithDefalt(request, keyHeight, heightLimit);

        if (widthLimit > 0 && heightLimit > 0) {
            size[0] = widthLimit;
            size[1] = heightLimit;
            im = rotateAndResetSizeIfNeeded(request, im, size);
        } else if (widthLimit <= 0) {
            widthLimit = Integer.MAX_VALUE;
        } else {
            heightLimit = Integer.MAX_VALUE;
        }

        // now -1 is translated into int.max. all the value should be bigger than 0;
        int width = im.getWidth();
        int height = im.getHeight();
        if (isHeightFirst) {
            if (heightLimit < height) {
                width = width * heightLimit / height;
                height = heightLimit;
            }
            if (widthLimit < width) {
                height = height * widthLimit / width;
                width = widthLimit;
            }
        } else {
            if (widthLimit < width) {
                height = height * widthLimit / width;
                width = widthLimit;
            }
            if (heightLimit < height) {
                width = width * heightLimit / height;
                height = heightLimit;
            }
        }
        size[0] = width;
        size[1] = height;
        return im;
    }

    private static BufferedImage rotate90DX(
            BufferedImage bi) {
        int width = bi.getWidth();
        int height = bi.getHeight();

        BufferedImage biFlip = new BufferedImage(height, width, bi.getType());

        for (int i = 0; i < width; i++)
            for (int j = 0; j < height; j++)
                biFlip.setRGB(j, width - 1 - i, bi.getRGB(i, j));

        return biFlip;
    }

    private static BufferedImage rotate90SX(
            BufferedImage bi) {
        int width = bi.getWidth();
        int height = bi.getHeight();

        BufferedImage biFlip = new BufferedImage(height, width, bi.getType());

        for (int i = 0; i < width; i++)
            for (int j = 0; j < height; j++)
                biFlip.setRGB(height - 1 - j, i, bi.getRGB(i, j));

        return biFlip;
    }

    /**
     * 
     * @param request
     * @param im
     * @param size
     *            the width and height must be both valid value, -1 and 0 are not allowed.
     * @return
     */
    private static BufferedImage rotateAndResetSizeIfNeeded(
            HttpServletRequest request,
            BufferedImage im,
            int[] size) {

        int desWidth = size[0];
        int desHeight = size[1];
        int width = im.getWidth();
        int height = im.getHeight();

        if (desWidth <= 0 || desHeight <= 0) {
            TaoDebug.error(
                    "Unexpected parameter when rotateAndRefillSizeIfNeeded! the width: {} height:{} should not be negative",
                    desWidth, desHeight);
            size[0] = width;
            size[1] = height;
            return im;
        }

        boolean isRotateNeeded = false;
        if (desWidth == desHeight || width == height) {
            isRotateNeeded = false;
        } else if (desWidth > desHeight && width > height) {
            isRotateNeeded = false;
        } else if (desWidth < desHeight && width < height) {
            isRotateNeeded = false;
        } else {
            double minDiff =
                    getCustValueWithDefalt(request, CC.MinDiffForOrentationCheck, MinDiffForOrentationCheck) / 10.0;
            if (width > height) {
                isRotateNeeded = (double) (width - height) / width > minDiff;
            } else {
                isRotateNeeded = (double) (height - width) / height > minDiff;
            }
        }

        if (isRotateNeeded && request != null) {
            im = "right".equalsIgnoreCase((String) request.getSession().getAttribute(CC.rotateDirection))
                    ? rotate90DX(im)
                    : rotate90SX(im);
            size[0] = height;
            size[1] = width;
        } else {
            size[0] = width;
            size[1] = height;
        }

        return im;
    }

    /**
     * 
     * @param im
     * @return
     */
    private static BufferedImage makeImageWithSize(
            BufferedImage im,
            int[] size) {

        int width = size[0];
        int height = size[1];
        if (im.getWidth() == width && im.getHeight() == height) {
            return im;
        }

        Image image = null;
        if (width > 0 && height > 0) {
            image = im.getScaledInstance(width, height, java.awt.Image.SCALE_SMOOTH);
        } else {
            if (width < 0 && im.getHeight() > height) {
                width = im.getWidth();
                image = im.getSubimage(0, 0, width, height);
            } else if (height < 0 && im.getWidth() > width) {
                height = im.getHeight();
                image = im.getSubimage(0, 0, width, height);
            } else {
                width = im.getWidth();
                height = im.getHeight();
                image = im;
            }
        }

        BufferedImage inputbig = new BufferedImage(width, height, BufferedImage.TYPE_INT_BGR);
        inputbig.getGraphics().drawImage(image, 0, 0, null); // this way is better.
        // inputbig.getGraphics().drawImage(image, 0, 0, pToWidth, pToHeight, null); //the created thum image is not
        // clear
        // enough with this way.

        return inputbig;

    }

    public static byte[] getByteFormateImage(
            BufferedImage im,
            String pFormat) {
        byte[] bFR = null;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (pFormat.startsWith("."))
            pFormat = pFormat.substring(1);

        try {
            ImageIO.write(im, pFormat, out);
            bFR = out.toByteArray();
        } catch (Exception e) {
            e.printStackTrace();
        }

        return bFR;
    }

    static byte[] composePic(
            BufferedImage bg_src,
            byte[] logo_byteAry) {

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {

            ByteArrayInputStream logo_in = new ByteArrayInputStream(logo_byteAry);
            BufferedImage logo_src = ImageIO.read(logo_in);

            int bg_w = bg_src.getWidth(null);
            int bg_h = bg_src.getHeight(null);

            int logo_w = logo_src.getWidth(null);
            int logo_h = logo_src.getHeight(null);
            if (logo_w > 100) {
                TaoDebug.error("logo width is bigger than 100, can not put it on QRCode. the width:{}", logo_w);
                return null;
            }

            int x = (bg_w - logo_w) / 2;
            int y = (bg_h - logo_h) / 2;

            BufferedImage tag = new BufferedImage(bg_w, bg_h, BufferedImage.TYPE_INT_RGB);
            Graphics2D g2d = tag.createGraphics();
            g2d.drawImage(bg_src, 0, 0, bg_w, bg_h, null);

            g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_ATOP, 1.0f)); // opacity set up start
            g2d.fillRoundRect(x - 2, y - 2, logo_w + 4, logo_h + 4, 2, 2);
            g2d.drawImage(logo_src, x, y, logo_w, logo_h, new Color(255, 255, 255), null);
            g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER)); // opacity set up end

            ImageIO.write(tag, "jpg", out);// write into disk.
            // JPEGImageEncoder encoder = JPEGCodec.createJPEGEncoder(out); encoder.encode(tag);

            return out.toByteArray();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                out.close();
            } catch (Exception e) {
                TaoDebug.error("unexpected Exception when closing output steam in composePic. exceptions{}",
                        e.toString());
            }
        }
        return null;
    }

    // public boolean writeHighQuality(BufferedImage im, String fileFullPath) {
    // try {
    // FileOutputStream newimage = new FileOutputStream(fileFullPath+System.currentTimeMillis()+".jpg"); //create the
    // fill output stream.
    // JPEGImageEncoder encoder = JPEGCodec.createJPEGEncoder(newimage);
    // JPEGEncodeParam jep = JPEGCodec.getDefaultJPEGEncodeParam(im);
    // jep.setQuality(1f, true);//set the quality.
    // encoder.encode(im, jep); //start to code.
    // newimage.close();
    // return true;
    // } catch (Exception e) {
    // return false;
    // }
    // }

    private static int getCustValueWithDefalt(
            HttpServletRequest request,
            String key,
            int defaultValue) {
        if (request == null) {
            return defaultValue;
        }
        if (StringUtils.isBlank(key)) {
            TaoDebug.error("Non-null key value expected when {}", "getCustValueWithDefalt");
            return defaultValue;
        }
        Object userCustomise = request.getSession().getAttribute(key);
        int value = 0;
        if (userCustomise != null) {
            try {
                value = Integer.parseInt(userCustomise.toString());
            } catch (Exception e) {
                value = defaultValue;
            }
        }
        return value == 0 ? defaultValue : value;
    }

    static long validateThumNeededImageCount(
            String tKeyStr,
            List<MediaUpload> mediaUploads) {
        if (CollectionUtils.isEmpty(mediaUploads)) {
            return 0;
        }
        int size = mediaUploads.size();
        if (size % 2 != 0) {
            TaoDebug.error("none even length found of mediauploads with key : {}", tKeyStr);
            return -1;
        } else {
            size = size / 2;
        }

        boolean[] aryForImage = new boolean[size];
        boolean[] aryForThum = new boolean[size];
        int keyLength = tKeyStr.length();
        String path = null;
        for (MediaUpload mediaUpload : mediaUploads) {
            path = mediaUpload.getFilepath();
            path = path.substring(keyLength);
            if (!path.startsWith("_")) {
                TaoDebug.error("none end with '_[number] filepath of mediaupload found! key : {}", tKeyStr);
                return -1;
            }

            path = path.substring(1);
            boolean isThum = path.endsWith("_thum");
            if (isThum) {
                path = path.substring(0, path.length() - 5);
            }

            int index = 0;
            try {
                index = Integer.valueOf(path);
            } catch (Exception e) {
                TaoDebug.error("none end with '_[number] filepath of mediaupload found! key : {}, path:{}", tKeyStr,
                        path);
                return -1;
            }

            if (index < 1 || index > size) {
                TaoDebug.error("none expected index found in filepath of mediaupload! key : {}, index:{}", tKeyStr,
                        index);
                return size;
            }

            if (isThum) {
                if (aryForThum[index - 1]) {
                    TaoDebug.error("unexpected duplex index occured of mediaupload with key : {}_{}", tKeyStr, index);
                    return -1;
                }
                aryForThum[index - 1] = true;
            } else {
                if (aryForImage[index - 1]) {
                    TaoDebug.error("unexpected duplex index occured of mediaupload with key : {}_{}_thum", tKeyStr,
                            index);
                    return -1;
                }
                aryForImage[index - 1] = true;
            }
        }

        // verify if all are not false;
        for (int i = 0; i < size; i++) {
            if (!aryForImage[i]) {
                TaoDebug.error("unexpected index missing of mediaupload with key : {}_{}", tKeyStr, i + 1);
                return -1;
            }
            if (!aryForThum[i]) {
                TaoDebug.error("unexpected index missing of mediaupload with key : {}_{}_thum", tKeyStr, i + 1);
                return -1;
            }
        }

        return size;
    }

    static List<String> stripThumOrderAndValidate(
            List<String> imageKeys) {
        String[] imageKeysFR = new String[imageKeys.size() / 2];
        for (int i = imageKeys.size() - 1; i >= 0; i--) {
            String key = imageKeys.get(i);
            if (!key.endsWith("_thum")) {
                int index = key.lastIndexOf("_");
                try {
                    index = Integer.valueOf(key.substring(index + 1));
                } catch (Exception e) {
                    TaoDebug.error("non-number character found in filePath index position. key :{}", key);
                    return null;
                }
                imageKeysFR[index - 1] = key;
            }
        }

        for (int i = imageKeysFR.length - 1; i >= 0; i--) {
            if (imageKeysFR[i] == null) {
                TaoDebug.error("unexpected empty element appeared in filePath list of mediaupload. position :{}", i);
                return null;
            }
        }
        return Arrays.asList(imageKeysFR);
    }

    public static BufferedImage readWebp(byte[] data) throws IOException {
        ByteArrayInputStream bis = new ByteArrayInputStream(data);
        return ImageIO.read(bis);
    }

    void makeRoomForNewImages(
            String keyStr,
            long imageInsertPosition,
            long countOfExistingImage,
            int countOfNewImage,
            boolean isThumNeeded,
            Person person) {
        if (imageInsertPosition > countOfExistingImage) {
        	if(imageInsertPosition > countOfExistingImage + 1) {
	            TaoDebug.error("given position are out of bounds! pos:{}, bounds:{}", imageInsertPosition,
	                    countOfExistingImage);
        	}
            return;
        }

        for (long i = countOfExistingImage; i >= imageInsertPosition; i--) {//adjust the images after the location by increase their file path
            String key = keyStr + "_" + i;
            MediaUpload mediaUpload = mediaUploadService.findMediaByKeyAndPerson(key, person);
            if (mediaUpload != null) {
                mediaUpload.setFilepath(keyStr + "_" + (i + countOfNewImage));
                mediaUploadService.persist(mediaUpload);
            }
            if (isThumNeeded) {
                MediaUpload mediaThumb = mediaUploadService.findMediaByKeyAndPerson(key + "_thum", person);
                if (mediaThumb != null) {
                    mediaThumb.setFilepath(keyStr + "_" + (i + countOfNewImage) + "_thum");
                    mediaUploadService.persist(mediaThumb);
                }
            }
            // popup texts.
            List<TextContent> textContents = textContentService.findAllMatchedTextContents("%_" + key, null, person);
            if (textContents != null) {
                for (TextContent textContent : textContents) {
                    String posInPage = textContent.getPosInPage();
                    int index = posInPage.lastIndexOf('_');
                    posInPage = posInPage.substring(0, index + 1);
                    textContent.setPosInPage(posInPage + (i + countOfNewImage));
                    textContentService.persist(textContent);
                }
            }
            // description
            textContents = textContentService.findAllMatchedTextContents("%_" + key + "_description", null, person);
            if (textContents != null) {
                for (TextContent textContent : textContents) {
                    String posInPage = textContent.getPosInPage();
                    posInPage = posInPage.substring(0, posInPage.length() - 12);
                    int index = posInPage.lastIndexOf('_');
                    posInPage = posInPage.substring(0, index + 1);
                    textContent.setPosInPage(posInPage + (i + countOfNewImage) + "_description");
                    textContentService.persist(textContent);
                }
            }
            if (key.startsWith("service_")) {
                Service service = serviceService.findServiceByCatalogAndPerson(key.substring(8), person);
                if (service != null) {
                    service.setC1("" + i + countOfNewImage);
                    service.setRecordStatus((int)(i + countOfNewImage));	//this value will be used for order, c1 as a string is not OK for orderby.
                    serviceService.persist(service);
                }
            } else if (key.startsWith("product_")) {
                Product product = productService.findProductByCatalogAndPerson(key.substring(8), person);
                if (product != null) {
                    product.setC1(keyStr + "_" + (i + countOfNewImage));
                    productService.persist(product);
                }
            }
        }
    }

    void vacumGellaryImages(
            Person person,
            String tKeyStr) {
        List<MediaUpload> medias = mediaUploadService.listAllMediaUploadsByKeyAndPerson(tKeyStr, person);
        if (medias == null) {
            return;
        }

        HashMap mediaMapForImag = new HashMap();
        HashMap mediaMapForThum = new HashMap();
        for (MediaUpload tMedia : medias) {
            String filtPath = tMedia.getFilepath();
            boolean isThum = false;
            if (filtPath.endsWith("_thum")) {
                isThum = true;
                filtPath = filtPath.substring(0, filtPath.length() - 5);
            }
            int startOfLastNumber = filtPath.lastIndexOf("_") + 1;
            String tLastNumber = filtPath.substring(startOfLastNumber);
            if (isThum) {
                if (mediaMapForThum.get(tLastNumber) != null) {
                    TaoDebug.error("unexpected duplicated key occured, Filepath:{}", tMedia.getFilepath());
                }
                mediaMapForThum.put(tLastNumber, tMedia);
            } else {
                if (mediaMapForImag.get(tLastNumber) != null) {
                    TaoDebug.error("unexpected duplicated key occured, Filepath:{}", tMedia.getFilepath());
                }
                mediaMapForImag.put(tLastNumber, tMedia);
            }
        }

        // List<TextContent> textContents = textContentService.findAllMatchedTextContents("%_" + tKeyStr, null, person);
        // if (textContents == null) {
        // return;
        // }
        //
        // HashMap descriptionTexts = new HashMap();
        // HashMap popUpTexts = new HashMap();
        // for (TextContent textContent : textContents) {
        // String posInPage = textContent.getPosInPage();
        // boolean isDescrip = false;
        // if (posInPage.endsWith("_description")) {
        // isDescrip = true;
        // posInPage = posInPage.substring(0, posInPage.length() - 12);
        // }
        // int startOfLastNumber = posInPage.lastIndexOf("_") + 1;
        // String tLastNumber = posInPage.substring(startOfLastNumber);
        // if (isDescrip) {
        // if (descriptionTexts.get(tLastNumber) != null) {
        // TaoDebug.error("unexpected duplicated key occured, PosInPage:{}", textContent.getPosInPage());
        // }
        // descriptionTexts.put(tLastNumber, textContent);
        // } else {
        // if (popUpTexts.get(tLastNumber) != null) {
        // TaoDebug.error("unexpected duplicated key occured, PosInPage:{}", textContent.getPosInPage());
        // }
        // popUpTexts.put(tLastNumber, textContent);
        // }
        // }

        // here we still use medias to know the right length, cause some texc might missing.
        int size = medias.size() / 2;

        for (int i = 1; i <= size; i++) {
            String key = String.valueOf(i);

            // if no such item, get one from map.
            if (mediaMapForThum.get(key) == null) {
                if (mediaMapForImag.get(key) != null) {
                    TaoDebug.error("unexpected non-consistence in medialupload,(thum missing) key:{}", key);
                    return;
                }
                int j = i + 1;
                String keyJ;
                while (true) {
                    keyJ = String.valueOf(j);
                    Object tMedia = mediaMapForImag.get(keyJ);
                    if (tMedia != null) {
                        Object tMediaThum = mediaMapForThum.get(keyJ);
                        if (tMediaThum == null) {
                            TaoDebug.error(
                                    "!!! After validation!!!unexpected non-consistence in medialupload,(only Image left) key:{}",
                                    keyJ);
                            return;
                        }

                        // adjust thum
                        MediaUpload mediaUpload = (MediaUpload) tMediaThum;
                        String filtPath = mediaUpload.getFilepath();
                        filtPath = filtPath.substring(0, filtPath.length() - 5);
                        int startOfLastNumber = filtPath.lastIndexOf("_") + 1;
                        filtPath = filtPath.substring(0, startOfLastNumber) + i + "_thum";
                        mediaUpload.setFilepath(filtPath);
                        mediaUploadService.persist(mediaUpload);
                        mediaMapForThum.remove(keyJ);

                        // adjust image
                        mediaUpload = (MediaUpload) tMedia;
                        String oldFiltPath = mediaUpload.getFilepath();
                        filtPath = mediaUpload.getFilepath();
                        startOfLastNumber = filtPath.lastIndexOf("_") + 1;
                        filtPath = filtPath.substring(0, startOfLastNumber) + i;
                        mediaUpload.setFilepath(filtPath);
                        mediaUploadService.persist(mediaUpload);
                        mediaMapForImag.remove(keyJ);

                        // adjust texts in pop up
                        List<TextContent> textContents =
                                textContentService.findAllMatchedTextContents("%_" + oldFiltPath, person);
                        if (textContents != null) {
                            for (TextContent textContent : textContents) {
                                String posInPage = textContent.getPosInPage();
                                // posInPage = posInPage.substring(0, posInPage.length() - 12);
                                startOfLastNumber = posInPage.lastIndexOf("_") + 1;
                                posInPage = posInPage.substring(0, startOfLastNumber) + i;// + "_description";
                                textContent.setPosInPage(posInPage);
                                textContentService.persist(textContent);
                            }
                        }

                        // adjust texts in detail
                        textContents = textContentService.findAllMatchedTextContents("%_" + oldFiltPath + "_%", person);
                        if (textContents != null) {
                            for (TextContent textContent : textContents) {
                                String posInPage = textContent.getPosInPage();
                                posInPage = posInPage.substring(0, posInPage.length() - 12);
                                startOfLastNumber = posInPage.lastIndexOf("_") + 1;
                                posInPage = posInPage.substring(0, startOfLastNumber) + i + "_description";
                                textContent.setPosInPage(posInPage);
                                textContentService.persist(textContent);
                            }
                        }

                        break;
                    } else {
                        j++;
                        if (j > 1000) {
                            break;
                        }
                    }
                }
            } else { // if has such item, remove it from map.
                if (mediaMapForImag.get(key) == null) {
                    TaoDebug.error("unexpected non-consistence in medialupload,(only thum left) key:{}", key);
                    return;
                }

                mediaMapForThum.remove(key);
                mediaMapForImag.remove(key);
            }
        }
    }
}
