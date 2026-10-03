package com.stgo.taostyle.web;

import java.io.InputStream;
import java.io.OutputStream;

import javax.activation.DataSource;

public class InputStreamDataSource implements DataSource {

    InputStream content;
    String filename;
    String contentType;

    public InputStreamDataSource(InputStream content, String filename, String contentType) {
        this.content = content;
        this.filename = filename;
        this.contentType = contentType;
    }

    public InputStream getInputStream() {
        return content;
    }

    public OutputStream getOutputStream() {
        return new java.io.ByteArrayOutputStream();
    }

    public String getContentType() {
        return contentType;
    }

    public String getName() {
        return filename;
    }
}
