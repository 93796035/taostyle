package com.stgo.taostyle.web;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;


import com.stgo.taostyle.service.MediaUploadService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import com.stgo.taostyle.domain.MediaUpload;
import com.stgo.taostyle.domain.Person;

@RequestMapping("/game")
@Controller
public class GameController extends BaseController {

    @Autowired
    private TaoUtil taoUtil;
    @Autowired
    private MediaUploadService mediaUploadService;
    // this method is needed when editing a product item. the page was under the path of "\products"
    @RequestMapping("/congratulations")
    public String addProduct(
            Model uiModel,
            HttpServletRequest request) {
        return "game/congratulations";
    }

    // this method is needed when editing a product item. the page was under the path of "\game"
    @RequestMapping("/getImage/{id}")
    public void getImage(
            @PathVariable("id")
            String id,
            HttpServletRequest request,
            HttpServletResponse response) {
        response.setContentType("image/jpg");
        Person person = taoUtil.getCurPerson(request);
        MediaUpload tMedia = mediaUploadService.findMediaByKeyAndPerson(id, person);
        try {
            if (tMedia != null && tMedia.getContent() != null) {
                byte[] imageBytes = tMedia.getContent();
                response.getOutputStream().write(imageBytes);
                response.getOutputStream().flush();
            } else {
            }
        } catch (Exception e) {
            System.out.println("[GameController]Exception occured when fetching img of ID:" + id + "! (pid:" + person.getId() + " ) "  + e);
        }
    }
}
