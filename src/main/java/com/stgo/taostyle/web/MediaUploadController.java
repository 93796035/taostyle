package com.stgo.taostyle.web;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;

import com.stgo.taostyle.service.MediaUploadService;
import com.stgo.taostyle.service.TextContentService;
import com.stgo.taostyle.service.UserAccountService;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;
import com.stgo.taostyle.backend.security.TaoEncrypt;
import com.stgo.taostyle.backend.security.UserContextService;
import com.stgo.taostyle.domain.MediaUpload;
import com.stgo.taostyle.domain.Person;
import com.stgo.taostyle.domain.TextContent;
import com.stgo.taostyle.domain.UserAccount;

@Controller
@RequestMapping("/mediauploads")
public class MediaUploadController {

    @Autowired
    private TaoUtil taoUtil;
    @Autowired
    private TaoEncrypt taoEncrypt;
    @Inject
    private UserContextService userContextService;
    @Autowired
    private UserAccountService userAccountService;
    @Autowired
    private MediaUploadService mediaUploadService;
    @Autowired
    private TextContentService textContentService;

    @RequestMapping(value = "/{id}", produces = "text/html")
    public String show(
            @PathVariable("id")
            Long id,
            Model uiModel,
            HttpServletRequest request) {
        uiModel.addAttribute("mediaupload", mediaUploadService.findMediaUpload(id));
        Person person = taoUtil.getCurPerson(request);
        List<TextContent> items = textContentService.findContentsByMediaIdAndPerson(String.valueOf(id), person);
        if (items == null)
            items = new ArrayList<TextContent>();
        for (TextContent textContent : items) {
            String nameForDisplay = taoEncrypt.stripUserName(textContent.getPublisher());
            textContent.setPublisher(nameForDisplay);
        }
        uiModel.addAttribute("textcontents", items);
        return "mediauploads/show";
    }

    @RequestMapping(value = "/{id}", params = "form", produces = "text/html")
    public String updateForm(
            @PathVariable("id")
            Long id,
            Model uiModel,
            HttpServletRequest request) {
        Person person = taoUtil.getCurPerson(request);
        MediaUpload mediaUpload = mediaUploadService.findMediaUpload(id);
        if (mediaUpload.getAudient() == null) {
            Object contentManager = request.getSession().getAttribute(CC.app_ContentManager);
            if (contentManager != null) {
                String userName = contentManager.toString();
                mediaUpload.setAudient(userAccountService.findUserAccountByName(taoEncrypt.enrichName(request, userName)));
            }
        }
        uiModel.addAttribute("mediaupload", mediaUpload);
        uiModel.addAttribute("revisers", userAccountService.findAllUserAccountsByPerson(person));
        return "mediauploads/update";
    }

    @RequestMapping(method = RequestMethod.PUT, produces = "text/html")
    public String update(
            @Valid
            MediaUpload pMediaupload,
            BindingResult bindingResult,
            Model uiModel,
            HttpServletRequest request) {
        // if it's GE mode, then change the audient.
        Person person = taoUtil.getCurPerson(request);
        Object contentManager = request.getSession().getAttribute(CC.app_ContentManager);
        if (contentManager != null) {
            UserAccount userAccount =
                    userAccountService.findUserAccountByName(taoEncrypt.enrichName(request, contentManager.toString()));
            if (userAccount != null) {
                MediaUpload mediaUpload = mediaUploadService.findMediaUpload(pMediaupload.getId());
                String tUserName = userContextService.getCurrentUserName();
                // @Note: this is temporal workout, we used the filePath to save the comment(TextContent's content).
                // if filepath content changed, added a textContent. change the status to commented.
                String comment = pMediaupload.getFilepath();
                if (!mediaUpload.getFilepath().equalsIgnoreCase(comment)) {
                    TextContent textContent = new TextContent();
                    textContent.setContent(comment);
                    textContent.setPosInPage(String.valueOf(pMediaupload.getId()));
                    textContent.setPublisher(tUserName);
                    textContent.setMarkDate(new Date());
                    textContent.setPerson(person);
                    textContentService.persist(textContent);
                    if (!tUserName.equalsIgnoreCase(mediaUpload.getPublisher().getLoginname())) {
                        mediaUpload.setStatus(CC.STATUS_COMMENTED);
                    }
                }

                if (tUserName.equalsIgnoreCase(contentManager.toString())) {
                    // if audient changed, set status to waiting.
                    String audient = request.getParameter("audient");
                    UserAccount formerAudient = mediaUpload.getAudient();
                    if (formerAudient == null || !formerAudient.getLoginname().equals(audient)) {
                        if (contentManager.toString().equalsIgnoreCase(audient)) {
                            mediaUpload.setAudient(null);
                        } else {
                            mediaUpload.setAudient(userAccountService.findUserAccountByName(taoEncrypt.enrichName(request,
                                    audient)));
                            mediaUpload.setStatus(CC.STATUS_WAITING);
                        }
                    }
                } else { // if it's not GE, set audient back to null, GE will deal with it. the status might be waiting
                         // if there's no textContent given yet.
                    mediaUpload.setAudient(null);
                }
                //
                mediaUploadService.merge(mediaUpload);
            }
        }

        return "redirect:/mediauploads/" + pMediaupload.getId();
    }

    @RequestMapping("/getImage/{id}")
    public void getImage(
            @PathVariable("id")
            String id,
            HttpServletRequest request,
            HttpServletResponse response) {
        response.setContentType("image/jpg");
        Person person = taoUtil.getCurPerson(request);
        String langPrf = taoUtil.getLangPrfWithDefault(request);
        MediaUpload tMedia = mediaUploadService.findMediaByKeyAndPerson(langPrf + id, person);
        try {
            if (tMedia != null && tMedia.getContent() != null) {
                byte[] imageBytes = tMedia.getContent();
                response.getOutputStream().write(imageBytes);
                response.getOutputStream().flush();
            } else {
            }
        } catch (Exception e) {
            System.out.println("[ServiceController]Exception occured when fetching img of ID:" + id + "! (pid:" + person.getId() + " ) " + e);
        }
    }

    @RequestMapping(value = "/{id}", method = RequestMethod.DELETE, produces = "text/html")
    public String delete(
            @PathVariable("id")
            Long id,
            @RequestParam(value = "page", required = false)
            Integer page,
            @RequestParam(value = "size", required = false)
            Integer size,
            Model uiModel,
            HttpServletRequest request) {
        // remove relevant textContents
        Person person = taoUtil.getCurPerson(request);
        List<TextContent> textContents = textContentService.findContentsByMediaIdAndPerson(String.valueOf(id), person);
        if (textContents != null) {
            for (int i = 0; i < textContents.size(); i++) {
                textContentService.remove(textContents.get(i));
            }
        }
        // remove the mediaUpload.
        MediaUpload mediaUpload = mediaUploadService.findMediaUpload(id);
        if (mediaUpload == null) {
            return "redirect:/dashboard";
        }
        mediaUploadService.remove(mediaUpload);
        return "redirect:/dashboard";
    }


    @RequestMapping(value = "/{id}/content")
    public ResponseEntity<String> download(
            @PathVariable("id")
            Long id,
            HttpServletResponse response,
            Model uiModel) {
    	if(id <= 0) {
            HttpHeaders headers = new HttpHeaders();
            headers.add("Content-Type", "application/json;charset=UTF-8");

            // get content uploaded for use.
            File file = new File("C:\\DropBox\\workspace\\security\\simonstyle\\target\\simonstyle-1-SNAPSHOT.jar");
        	if(!file.exists()) {
        		return null;
        	}
        	
            String contentFR = "";
            try {
            	response.reset();
        		response.setCharacterEncoding("UTF-8");
        		response.setContentType("multipart/form-data");
//        		response.setHeader("Content-Disposition", "attachment;fileName=" + URLEncoder.encode(fileName, "UTF-8"));
        		
            	response.setHeader("Content-Disposition", "attachment;filename=simonstyle-1-SNAPSHOT.jar");
                OutputStream out = response.getOutputStream();
                response.setContentType("application/java-archive");
                byte[] bytesArray = new byte[(int) file.length()];
                FileInputStream fileInputStream = new FileInputStream(file);
                fileInputStream.read(bytesArray);
                IOUtils.copy(new ByteArrayInputStream(bytesArray), out);
                out.flush();                
                contentFR = new String(bytesArray, "UTF-8");
                out.close();
                fileInputStream.close();
//        		//2、 读取文件--输入流
//        		InputStream input=new FileInputStream(jar);
//        		//3、 写出文件--输出流
//        		OutputStream out = response.getOutputStream();
//        		byte[] buff =new byte[1024];
//        		int index=0;
//        		//4、执行 写出操作
//        		while((index= input.read(buff))!= -1){
//        			out.write(buff, 0, index);
//        			out.flush();
//        		}
//        		out.close();
//        		input.close();
        		
            } catch (Exception e) {
                TaoDebug.error("error happened when reading content into a String", e);
            }
            return new ResponseEntity<String>(contentFR, headers, HttpStatus.OK);
    	}else {
	        MediaUpload doc = mediaUploadService.findMediaUpload(id);
	        try {
	            response.setHeader("Content-Disposition", "inline;filename=\"" + doc.getFilepath() + doc.getContentType()
	                    + "\"");
	            OutputStream out = response.getOutputStream();
	            response.setContentType(doc.getContentType());
	            IOUtils.copy(new ByteArrayInputStream(doc.getContent()), out);
	            out.flush();
	        } catch (IOException e) {
	            e.printStackTrace();
	        } catch (Exception e) {
	            e.printStackTrace();
	        }
    	}
        return null;
    }
    
    @RequestMapping(value = "/latestVersion/sanjipos")
    public String downloadLatestSanjiPos(HttpServletResponse response, Model uiModel) {
        return null;
    }

	@RequestMapping(value = "/{id}", method = RequestMethod.GET, headers = "Accept=application/json")
    @ResponseBody
    public ResponseEntity<String> showJson(@PathVariable("id") Long id) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Type", "application/json; charset=utf-8");
        try {
            MediaUpload mediaUpload = mediaUploadService.findMediaUpload(id);
            if (mediaUpload == null) {
                return new ResponseEntity<String>(headers, HttpStatus.NOT_FOUND);
            }
            return new ResponseEntity<String>(mediaUploadService.toJson(mediaUpload), headers, HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<String>("{\"ERROR\":"+e.getMessage()+"\"}", headers, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

	@RequestMapping(headers = "Accept=application/json")
    @ResponseBody
    public ResponseEntity<String> listJson() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Type", "application/json; charset=utf-8");
        try {
            List<MediaUpload> result = mediaUploadService.findAllMediaUploads();
            return new ResponseEntity<String>(mediaUploadService.toJsonArray(result), headers, HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<String>("{\"ERROR\":"+e.getMessage()+"\"}", headers, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

	@RequestMapping(method = RequestMethod.POST, headers = "Accept=application/json")
    public ResponseEntity<String> createFromJson(@RequestBody String json, UriComponentsBuilder uriBuilder) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Type", "application/json");
        try {
            MediaUpload mediaUpload = mediaUploadService.fromJsonToMediaUpload(json);
            mediaUploadService.persist(mediaUpload);
            RequestMapping a = (RequestMapping) getClass().getAnnotation(RequestMapping.class);
            headers.add("Location",uriBuilder.path(a.value()[0]+"/"+mediaUpload.getId().toString()).build().toUriString());
            return new ResponseEntity<String>(headers, HttpStatus.CREATED);
        } catch (Exception e) {
            return new ResponseEntity<String>("{\"ERROR\":"+e.getMessage()+"\"}", headers, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

	@RequestMapping(value = "/jsonArray", method = RequestMethod.POST, headers = "Accept=application/json")
    public ResponseEntity<String> createFromJsonArray(@RequestBody String json) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Type", "application/json");
        try {
            for (MediaUpload mediaUpload: mediaUploadService.fromJsonArrayToMediaUploads(json)) {
                mediaUploadService.persist(mediaUpload);
            }
            return new ResponseEntity<String>(headers, HttpStatus.CREATED);
        } catch (Exception e) {
            return new ResponseEntity<String>("{\"ERROR\":"+e.getMessage()+"\"}", headers, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

	@RequestMapping(value = "/{id}", method = RequestMethod.PUT, headers = "Accept=application/json")
    public ResponseEntity<String> updateFromJson(@RequestBody String json, @PathVariable("id") Long id) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Type", "application/json");
        try {
            MediaUpload mediaUpload = mediaUploadService.fromJsonToMediaUpload(json);
            mediaUpload.setId(id);
            if (mediaUploadService.merge(mediaUpload) == null) {
                return new ResponseEntity<String>(headers, HttpStatus.NOT_FOUND);
            }
            return new ResponseEntity<String>(headers, HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<String>("{\"ERROR\":"+e.getMessage()+"\"}", headers, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

	@RequestMapping(value = "/{id}", method = RequestMethod.DELETE, headers = "Accept=application/json")
    public ResponseEntity<String> deleteFromJson(@PathVariable("id") Long id) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Type", "application/json");
        try {
            MediaUpload mediaUpload = mediaUploadService.findMediaUpload(id);
            if (mediaUpload == null) {
                return new ResponseEntity<String>(headers, HttpStatus.NOT_FOUND);
            }
            mediaUploadService.remove(mediaUpload);
            return new ResponseEntity<String>(headers, HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<String>("{\"ERROR\":"+e.getMessage()+"\"}", headers, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
